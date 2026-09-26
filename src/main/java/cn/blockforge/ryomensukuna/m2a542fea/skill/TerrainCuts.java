package cn.blockforge.ryomensukuna.m2a542fea.skill;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class TerrainCuts {
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Cut>> QUEUES = new WeakHashMap<ServerLevel, LinkedHashMap<BlockPos, Cut>>();

    private TerrainCuts() {
    }

    public static void clear() {
        QUEUES.clear();
    }

    public static Vec3 right(Vec3 dir) {
        Vec3 r = new Vec3(0.0, 1.0, 0.0).cross(dir);
        return r.lengthSqr() < 0.001 ? new Vec3(1.0, 0.0, 0.0) : r.normalize();
    }

    public static void plane(ServerLevel world, Vec3 center, Vec3 dir, double half, boolean grid, double thickness) {
        Vec3 right = TerrainCuts.right(dir);
        Vec3 up = dir.cross(right).normalize();
        if (grid) {
            for (double offset = -half; offset <= half + 0.1; offset += 3.0) {
                TerrainCuts.line(world, center.add(right.scale(offset)), up, dir, half, thickness);
                TerrainCuts.line(world, center.add(up.scale(offset)), right, dir, half, thickness);
            }
        } else {
            TerrainCuts.line(world, center, right, dir, half, thickness);
        }
    }

    private static void line(ServerLevel world, Vec3 c, Vec3 axis, Vec3 dir, double half, double thickness) {
        Vec3 side = axis.cross(dir).normalize();
        for (double a = -half; a <= half; a += 0.75) {
            for (double b = -thickness; b <= thickness + 0.01; b += 0.75) {
                TerrainCuts.enqueue(world, BlockPos.containing((Position)c.add(axis.scale(a)).add(side.scale(b))), dir.scale(0.65).add(0.0, 0.65, 0.0), false);
            }
        }
    }

    public static void burst(ServerLevel world, Vec3 center, double radius, boolean fire) {
        int r = (int)Math.ceil(radius);
        for (int x = -r; x <= r; ++x) {
            for (int y = -r; y <= r; ++y) {
                for (int z = -r; z <= r; ++z) {
                    Vec3 d = new Vec3((double)x, (double)y, (double)z);
                    if (d.lengthSqr() > radius * radius) continue;
                    TerrainCuts.enqueue(world, BlockPos.containing((Position)center.add(d)), d.normalize().scale(0.85).add(0.0, 0.75, 0.0), fire);
                }
            }
        }
    }

    public static void domainLayer(ServerLevel world, Vec3 center, double radius, int yOffset) {
        int r = (int)Math.ceil(radius);
        BlockPos base = BlockPos.containing((Position)center);
        int y = base.getY() + yOffset;
        if (y < world.getMinY() || y >= world.getMaxY()) {
            return;
        }
        for (int x = -r; x <= r; ++x) {
            for (int z = -r; z <= r; ++z) {
                if ((double)(x * x + z * z) > radius * radius) continue;
                Vec3 force = new Vec3((double)x, 0.0, (double)z);
                force = force.lengthSqr() < 0.01 ? new Vec3(0.0, 0.8, 0.0) : force.normalize().scale(0.45).add(0.0, 0.55, 0.0);
                TerrainCuts.enqueue(world, new BlockPos(base.getX() + x, y, base.getZ() + z), force, false, true);
            }
        }
    }

    private static void enqueue(ServerLevel w, BlockPos p, Vec3 force, boolean fire) {
        TerrainCuts.enqueue(w, p, force, fire, false);
    }

    private static void enqueue(ServerLevel w, BlockPos p, Vec3 force, boolean fire, boolean clean) {
        if (!cn.blockforge.ryomensukuna.m2a542fea.config.JjkConfig.terrainDestruction()) {
            return;
        }
        LinkedHashMap q = QUEUES.computeIfAbsent(w, k -> new LinkedHashMap());
        if (!w.hasChunkAt(p)) {
            return;
        }
        Cut previous = (Cut)q.get(p);
        if (previous == null && q.size() < 260000 || clean && previous != null && !previous.clean()) {
            q.put(p, new Cut(p, force, fire, clean));
        }
    }

    public static void tick(ServerLevel world) {
        LinkedHashMap<BlockPos, Cut> q = QUEUES.get(world);
        if (q == null) {
            return;
        }
        int inspected = 0;
        int broken = 0;
        int debris = 0;
        Iterator<Cut> it = q.values().iterator();
        while (it.hasNext() && inspected++ < 24000 && broken < 3200) {
            boolean plant;
            BlockState state;
            Cut cut = it.next();
            it.remove();
            BlockPos p = cut.pos();
            if (!world.hasChunkAt(p) || (state = world.getBlockState(p)).isAir() || state.getDestroySpeed((BlockGetter)world, p) < 0.0f) continue;
            // Containers and other block entities, bedrock-like blocks are never destroyed.
            if (state.hasBlockEntity() || state.is(Blocks.BEDROCK)) continue;
            if (cut.clean()) {
                world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                ++broken;
                continue;
            }
            if (!state.getFluidState().isEmpty()) continue;
            boolean bl = plant = state.canBeReplaced() || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES);
            if (plant) {
                world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                ++broken;
                continue;
            }
            if (debris < 6 && broken % 32 == 0) {
                FallingBlockEntity flying = FallingBlockEntity.fall((Level)world, (BlockPos)p, (BlockState)state);
                flying.dropItem = false;
                flying.disableDrop();
                flying.setDeltaMovement(cut.impulse().add((world.getRandom().nextDouble() - 0.5) * 0.5, world.getRandom().nextDouble() * 0.5, (world.getRandom().nextDouble() - 0.5) * 0.5));
                flying.needsSync = true;
                ++debris;
            } else {
                world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            if (broken % 16 == 0) {
                world.levelEvent(2001, p, Block.getId((BlockState)state));
            }
            if (cut.ignite() && world.getRandom().nextInt(5) == 0 && world.getBlockState(p.below()).isRedstoneConductor((BlockGetter)world, p.below())) {
                world.setBlock(p, Blocks.FIRE.defaultBlockState(), 3);
            }
            ++broken;
        }
        if (q.isEmpty()) {
            QUEUES.remove(world);
        }
    }

    private record Cut(BlockPos pos, Vec3 impulse, boolean ignite, boolean clean) {
    }
}

