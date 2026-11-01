package cn.blockforge.ryomensukuna.m2a542fea.combat;

import cn.blockforge.ryomensukuna.m2a542fea.api.DomainHooks;
import cn.blockforge.ryomensukuna.m2a542fea.config.JjkConfig;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Domains reshape the land they cover and give it back afterwards.
 * <p>
 * A domain opens a <em>region</em> (a sphere). Unlimited Void and Malevolent Shrine flatten theirs
 * into a superflat arena (grass on dirt at the floor, nothing above it inside the sphere) and pull
 * every enemy within half a radius beyond the edge inside. From then on every enemy held inside (pulled
 * in or already there) cannot leave: whoever reaches the edge is thrown back in. The only ways out are
 * to open a domain of one's own (a clash) or to break this one (its caster shaken loose, its shell
 * shattered, its caster dead, its time run out), which closes the region. Any block changed inside a region - by
 * the flattening, by techniques, or by another mod through {@code api.DomainTerrainApi} - is first
 * written to a per-world journal with its original state. When a region closes, every journaled
 * block that no other open region still covers is put back exactly as it was; overlapping domains
 * therefore restore only once the last of them has gone. Block entities (chests and the like) and
 * bedrock are never touched. On server shutdown everything still journaled is restored at once.
 * <p>
 * While a region is open its land cannot be changed by anything else: digging, explosions, fire,
 * pistons, flowing fluids, techniques and other mods are all refused inside it (see the mixins on
 * {@code Level#setBlock} and the explosion), so the arena stays as the domain made it.
 */
public final class DomainTerrain {
    /** Positions inspected per tick while flattening (all regions together). */
    private static final int SCAN_BUDGET = 24000;
    /** Blocks written per tick while flattening, and restored per tick afterwards. */
    private static final int WRITE_BUDGET = 3000;
    private static final int RESTORE_BUDGET = 6000;
    /** Enemies up to this much of a radius beyond the edge are pulled in. */
    public static final double PULL_REACH = 0.5;
    /** The pull happens once the floor is ready, or after this many ticks at most. */
    private static final int PULL_AFTER = 60;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Identifier FATE_MARBLE = Identifier.fromNamespaceAndPath("fatekings", "unlimited_blade_works");
    private static final Map<UUID, Region> REGIONS = new LinkedHashMap<>();
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<BlockState>> JOURNALS = new HashMap<>();
    private static final Map<ServerLevel, LongArrayFIFOQueue> RESTORE = new HashMap<>();
    /** Areas just restored: anyone left inside a block there is lifted out once the queue is done. */
    private static final Map<ServerLevel, List<Region>> SETTLE = new HashMap<>();
    private static Method marbleRadius;
    /** >0 while the domain system itself writes blocks (flattening, {@link #place}, restoring). */
    private static int writing;

    /** Another mod's reality marble near a region: its sphere and its owner (null if unknown). */
    private record Marble(double x, double y, double z, double radius, UUID owner) {
        boolean contains(double px, double py, double pz) {
            double dx = px - this.x, dy = py - this.y, dz = pz - this.z;
            return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
        }
    }

    private DomainTerrain() {
    }

    /** One open domain region. */
    public static final class Region {
        final UUID id;
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        /** Y of the grass layer; the arena floor is its top. Only meaningful for flat regions. */
        final int floorY;
        final boolean flat;
        final UUID owner;
        final Predicate<LivingEntity> enemy;
        final Entity anchor;
        /** Enemies held inside until the domain ends or they open one of their own. */
        final java.util.Set<UUID> captives = new java.util.HashSet<>();
        final Map<UUID, Long> warned = new HashMap<>();
        int column;
        boolean flattened;
        boolean pulled;
        int age;

        Region(UUID id, ServerLevel level, Vec3 center, double radius, int floorY, boolean flat, UUID owner, Predicate<LivingEntity> enemy, Entity anchor) {
            this.id = id;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.floorY = floorY;
            this.flat = flat;
            this.owner = owner;
            this.enemy = enemy;
            this.anchor = anchor;
            this.flattened = !flat;
        }

        boolean covers(BlockPos p) {
            double dx = p.getX() + 0.5 - this.center.x, dy = p.getY() + 0.5 - this.center.y, dz = p.getZ() + 0.5 - this.center.z;
            return dx * dx + dy * dy + dz * dz <= this.radius * this.radius;
        }

        boolean covers(Vec3 v) {
            return v.distanceToSqr(this.center) <= this.radius * this.radius;
        }
    }

    // ---- Opening and closing ----

    /**
     * A domain of ours opens: its sphere becomes a superflat arena (unless terrain changes are turned
     * off) and enemies just outside are pulled in. {@code anchor}: the domain entity; the region closes
     * on its own if that entity disappears without closing it.
     */
    public static void openFlat(Entity anchor, LivingEntity owner, Vec3 center, double radius, int floorY, Predicate<LivingEntity> enemy) {
        if (!(anchor.level() instanceof ServerLevel level)) return;
        boolean flat = JjkConfig.terrainDestruction();
        REGIONS.put(anchor.getUUID(), new Region(anchor.getUUID(), level, center, radius, floorY, flat, owner == null ? null : owner.getUUID(), enemy, anchor));
    }

    /** A region whose blocks are shaped by someone else (another mod); only journaled and restored. */
    public static UUID openPlain(ServerLevel level, Vec3 center, double radius, UUID owner, Predicate<LivingEntity> enemy) {
        UUID id = UUID.randomUUID();
        Region r = new Region(id, level, center, radius, level.getMinY(), false, owner, enemy, null);
        r.pulled = enemy == null;
        REGIONS.put(id, r);
        return id;
    }

    /** The region closes: what it changed (and nobody else still covers) goes back as it was. */
    public static void close(UUID id) {
        Region r = REGIONS.remove(id);
        if (r == null) return;
        Long2ObjectOpenHashMap<BlockState> journal = JOURNALS.get(r.level);
        if (journal == null || journal.isEmpty()) return;
        LongArrayFIFOQueue queue = RESTORE.computeIfAbsent(r.level, k -> new LongArrayFIFOQueue());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<BlockState> e : journal.long2ObjectEntrySet()) {
            p.set(BlockPos.of(e.getLongKey()));
            if (!coveredByOpen(r.level, p)) queue.enqueue(e.getLongKey());
        }
        SETTLE.computeIfAbsent(r.level, k -> new ArrayList<>()).add(r);
    }

    public static boolean isOpen(UUID id) {
        return REGIONS.containsKey(id);
    }

    // ---- Protection ----

    /** Whether {@code p} lies in an open domain, whose land nothing but the domain itself may change. */
    public static boolean protects(net.minecraft.world.level.Level level, BlockPos p) {
        if (REGIONS.isEmpty() || writing > 0 || !(level instanceof ServerLevel server)) return false;
        return coveredByOpen(server, p);
    }

    private static void write(ServerLevel level, BlockPos p, BlockState state) {
        ++writing;
        try {
            level.setBlock(p, state, FLAGS);
        } finally {
            --writing;
        }
    }

    // ---- Journal ----

    private static boolean coveredByOpen(ServerLevel level, BlockPos p) {
        for (Region r : REGIONS.values()) {
            if (r.level == level && r.covers(p)) return true;
        }
        return false;
    }

    private static boolean untouchable(BlockState state) {
        return state.hasBlockEntity() || state.is(Blocks.BEDROCK);
    }

    /** About to change {@code p}: if a domain covers it, remember what was there first. */
    public static void note(ServerLevel level, BlockPos p, BlockState current) {
        if (REGIONS.isEmpty() || !coveredByOpen(level, p)) return;
        JOURNALS.computeIfAbsent(level, k -> new Long2ObjectOpenHashMap<>()).putIfAbsent(p.asLong(), current);
    }

    /** Sets a block inside a domain, journaling the original. False for blocks that are never touched. */
    public static boolean place(ServerLevel level, BlockPos p, BlockState state) {
        BlockState current = level.getBlockState(p);
        if (untouchable(current)) return false;
        if (current == state) return true;
        note(level, p, current);
        write(level, p, state);
        return true;
    }

    // ---- Per tick ----

    public static void tick(MinecraftServer server) {
        int scan = SCAN_BUDGET, write = WRITE_BUDGET;
        List<UUID> vanished = new ArrayList<>();
        for (Region r : REGIONS.values()) {
            if (r.anchor != null && r.anchor.isRemoved()) {
                // The domain vanished without closing (unloaded, killed by command): close it below.
                vanished.add(r.id);
                continue;
            }
            ++r.age;
            if (!r.flattened && scan > 0 && write > 0) {
                int[] budget = {scan, write};
                flatten(r, budget);
                scan = budget[0];
                write = budget[1];
            }
            if (r.flat && r.age <= PULL_AFTER + 40) cushion(r);
            if (!r.pulled && (r.flattened || r.age >= PULL_AFTER)) {
                r.pulled = true;
                pullIn(r);
                if (r.flat) settle(r);
                seal(r);
            }
            if (r.pulled) contain(r);
        }
        vanished.forEach(DomainTerrain::close);
        restore();
    }

    /** Clears the sphere above the floor and lays grass on three layers of dirt, column by column. */
    private static void flatten(Region r, int[] budget) {
        int span = (int)Math.ceil(r.radius);
        int side = span * 2 + 1;
        int cx = (int)Math.floor(r.center.x), cz = (int)Math.floor(r.center.z);
        List<Marble> marbles = foreignSpheres(r);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int maxY = r.level.getMaxY() - 1;
        while (r.column < side * side && budget[0] > 0 && budget[1] > 0) {
            int dx = r.column % side - span, dz = r.column / side - span;
            ++r.column;
            double x = cx + dx + 0.5, z = cz + dz + 0.5;
            double h2 = (x - r.center.x) * (x - r.center.x) + (z - r.center.z) * (z - r.center.z);
            double fy = r.floorY + 0.5 - r.center.y;
            if (h2 + fy * fy > r.radius * r.radius) continue;
            int top = Math.min(maxY, (int)Math.floor(r.center.y + Math.sqrt(Math.max(0.0, r.radius * r.radius - h2))));
            for (int y = r.floorY - 3; y <= top; ++y) {
                --budget[0];
                p.set(cx + dx, y, cz + dz);
                if (insideAny(marbles, p) || insideOtherShape(r, p)) continue;
                BlockState want = y > r.floorY ? Blocks.AIR.defaultBlockState()
                    : (y == r.floorY ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                BlockState current = r.level.getBlockState(p);
                if (current == want || untouchable(current)) continue;
                note(r.level, p, current);
                write(r.level, p, want);
                --budget[1];
            }
        }
        if (r.column >= side * side) r.flattened = true;
    }

    /** While the land drops away beneath them, nobody inside takes fall damage from it. */
    private static void cushion(Region r) {
        for (LivingEntity e : r.level.getEntitiesOfClass(LivingEntity.class, new AABB(r.center, r.center).inflate(r.radius), e -> r.covers(e.position()))) {
            e.fallDistance = 0.0;
        }
    }

    /** Anyone left inside a block (a cave under the new floor, a restored hill) is lifted onto the surface. */
    private static void settle(Region r) {
        for (LivingEntity e : r.level.getEntitiesOfClass(LivingEntity.class, new AABB(r.center, r.center).inflate(r.radius + 2.0))) {
            if (r.level.noCollision(e)) continue;
            int y = r.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int)Math.floor(e.getX()), (int)Math.floor(e.getZ()));
            e.teleportTo(e.getX(), y, e.getZ());
            e.fallDistance = 0.0;
        }
    }

    /** Enemies between the edge and half a radius beyond it are drawn in, onto the arena floor. */
    private static void pullIn(Region r) {
        if (r.enemy == null) return;
        double reach = r.radius * (1.0 + PULL_REACH);
        for (LivingEntity e : r.level.getEntitiesOfClass(LivingEntity.class, new AABB(r.center, r.center).inflate(reach))) {
            double d = e.position().distanceTo(r.center);
            if (d <= r.radius - 0.5 || d > reach || !e.isAlive() || e.isPassenger() || e.isVehicle() || e instanceof EnderDragon) continue;
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (!r.enemy.test(e) || heldElsewhere(r, e) || DomainHooks.immune(e)) continue;
            Vec3 flat = new Vec3(e.getX() - r.center.x, 0.0, e.getZ() - r.center.z);
            Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
            Vec3 aim = r.center.add(dir.scale(r.radius * 0.55));
            aim = new Vec3(aim.x, r.flat ? r.floorY + 1.0 : e.getY(), aim.z);
            Vec3 spot = Mobility.safeSpot(e, aim, dir.scale(-1.0));
            if (spot == null) spot = aim;
            Vec3 from = e.position();
            CurseFx.particles(r.level, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 24, 0.3, 0.7, 0.3, 0.1);
            e.teleportTo(spot.x, spot.y, spot.z);
            e.fallDistance = 0.0;
            CurseFx.particles(r.level, ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1.0, spot.z, 24, 0.3, 0.7, 0.3, 0.1);
            r.level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.6f);
            if (e instanceof net.minecraft.server.level.ServerPlayer sp) cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.actionBar(sp, "sukuna.hint.pulled_in");
            r.captives.add(e.getUUID());
        }
    }

    /** The barrier closes: every enemy inside now (pulled in or already there) is held. */
    private static void seal(Region r) {
        if (r.enemy == null) return;
        for (LivingEntity e : r.level.getEntitiesOfClass(LivingEntity.class, new AABB(r.center, r.center).inflate(r.radius))) {
            if (!r.covers(e.position()) || !e.isAlive() || e instanceof EnderDragon || r.captives.contains(e.getUUID())) continue;
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (!r.enemy.test(e) || heldElsewhere(r, e) || DomainHooks.immune(e)) continue;
            r.captives.add(e.getUUID());
            if (e instanceof net.minecraft.server.level.ServerPlayer sp) cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.actionBar(sp, "sukuna.hint.domain_trapped");
        }
    }

    /** Whoever is held and reaches the edge is thrown back in; opening a domain of one's own sets one free. */
    private static void contain(Region r) {
        if (r.captives.isEmpty()) return;
        List<Marble> marbles = null;
        for (Iterator<UUID> it = r.captives.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!(r.level.getEntity(id) instanceof LivingEntity e) || !e.isAlive() || e.isRemoved()) {
                it.remove();
                continue;
            }
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
                it.remove();
                continue;
            }
            if (marbles == null) marbles = foreignSpheres(r);
            if (ownsDomain(r, e, marbles)) {
                // A domain of one's own breaks the hold: from here it is a clash.
                it.remove();
                continue;
            }
            // Shrugging the domain off right now: free to walk out (held again once that ends, if still inside).
            if (DomainHooks.immune(e)) continue;
            double limit = r.radius - 1.0;
            Vec3 from = e.position();
            if (from.distanceToSqr(r.center) <= limit * limit) continue;
            Vec3 out = from.subtract(r.center);
            Vec3 dir = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
            Vec3 back = r.center.add(dir.scale(r.radius - 2.5));
            if (r.flat) back = new Vec3(back.x, Math.max(back.y, r.floorY + 1.0), back.z);
            e.teleportTo(back.x, back.y, back.z);
            e.setDeltaMovement(dir.scale(-0.35));
            e.needsSync = true;
            e.fallDistance = 0.0;
            CurseFx.particles(r.level, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 16, 0.3, 0.6, 0.3, 0.05);
            long now = r.level.getGameTime();
            if (e instanceof net.minecraft.server.level.ServerPlayer sp && now - r.warned.getOrDefault(id, -100L) >= 40L) {
                r.warned.put(id, now);
                cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.actionBar(sp, "sukuna.hint.domain_sealed");
                r.level.playSound(null, back.x, back.y, back.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f);
            }
        }
    }

    /** Whether {@code e} has a domain of its own open: one of ours, another mod's region, or a marble. */
    private static boolean ownsDomain(Region self, LivingEntity e, List<Marble> marbles) {
        for (Region r : REGIONS.values()) {
            if (r != self && e.getUUID().equals(r.owner)) return true;
        }
        for (DomainClash.Domain d : DomainClash.active()) {
            if (d.owner.equals(e.getUUID()) && d.entity != self.anchor) return true;
        }
        for (Marble m : marbles) {
            if (e.getUUID().equals(m.owner())) return true;
        }
        return false;
    }

    /** Owners of another open domain, and anyone standing in one, are anchored there. */
    private static boolean heldElsewhere(Region self, LivingEntity e) {
        for (Region r : REGIONS.values()) {
            if (r == self || r.level != self.level) continue;
            if (e.getUUID().equals(r.owner) || r.covers(e.position())) return true;
        }
        for (Marble m : foreignSpheres(self)) {
            if (m.contains(e.getX(), e.getY(), e.getZ())) return true;
        }
        return false;
    }

    private static void restore() {
        if (RESTORE.isEmpty()) return;
        int budget = RESTORE_BUDGET;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Iterator<Map.Entry<ServerLevel, LongArrayFIFOQueue>> it = RESTORE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ServerLevel, LongArrayFIFOQueue> e = it.next();
            ServerLevel level = e.getKey();
            LongArrayFIFOQueue queue = e.getValue();
            Long2ObjectOpenHashMap<BlockState> journal = JOURNALS.get(level);
            while (!queue.isEmpty() && budget > 0) {
                long key = queue.dequeueLong();
                p.set(BlockPos.of(key));
                // Covered again by a domain that opened meanwhile: that one will give it back.
                if (journal == null || coveredByOpen(level, p)) continue;
                BlockState original = journal.remove(key);
                if (original == null) continue;
                BlockState current = level.getBlockState(p);
                if (!untouchable(current) && current != original) write(level, p, original);
                --budget;
            }
            if (queue.isEmpty()) {
                it.remove();
                List<Region> done = SETTLE.remove(level);
                if (done != null) done.forEach(DomainTerrain::settle);
            }
            if (budget <= 0) return;
        }
    }

    /** Server shutting down: every domain's land goes back at once, before the world is saved. */
    public static void restoreAllNow() {
        REGIONS.clear();
        for (Map.Entry<ServerLevel, Long2ObjectOpenHashMap<BlockState>> e : JOURNALS.entrySet()) {
            ServerLevel level = e.getKey();
            for (Long2ObjectMap.Entry<BlockState> j : e.getValue().long2ObjectEntrySet()) {
                BlockPos p = BlockPos.of(j.getLongKey());
                if (!untouchable(level.getBlockState(p))) write(level, p, j.getValue());
            }
        }
        JOURNALS.clear();
        RESTORE.clear();
        SETTLE.clear();
    }

    // ---- Other domains that shape their own land ----

    /** Inside a region opened by someone else that shapes its own blocks (not superflat). */
    private static boolean insideOtherShape(Region self, BlockPos p) {
        for (Region r : REGIONS.values()) {
            if (r != self && !r.flat && r.anchor == null && r.level == self.level && r.covers(p)) return true;
        }
        return false;
    }

    private static boolean insideAny(List<Marble> marbles, BlockPos p) {
        for (Marble m : marbles) {
            if (m.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) return true;
        }
        return false;
    }

    /** Unlimited Blade Works marbles (the Fate kings mod) near a region. */
    private static List<Marble> foreignSpheres(Region r) {
        List<Marble> out = new ArrayList<>();
        for (Entity e : r.level.getEntities((Entity)null, new AABB(r.center, r.center).inflate(r.radius + 96.0),
                e -> FATE_MARBLE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())))) {
            double radius = marbleRadius(e);
            if (radius <= 0.0) continue;
            out.add(new Marble(e.getX(), e.getY(), e.getZ(), radius, marbleOwner(e)));
        }
        return out;
    }

    private static UUID marbleOwner(Entity marble) {
        try {
            return marble.getClass().getMethod("ownerId").invoke(marble) instanceof UUID id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static double marbleRadius(Entity marble) {
        try {
            if (marbleRadius == null || !marbleRadius.getDeclaringClass().isInstance(marble)) {
                marbleRadius = marble.getClass().getMethod("radius");
            }
            return ((Number)marbleRadius.invoke(marble)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0.0;
        }
    }
}
