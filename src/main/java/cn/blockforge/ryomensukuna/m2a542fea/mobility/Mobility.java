package cn.blockforge.ryomensukuna.m2a542fea.mobility;

import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.ChannelCasting;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CombatSkills;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** C (quick displacement) and Z (rise / air adjust). Independent cooldowns, never on the wheel. */
public final class Mobility {
    public static final int DASH = 0;
    public static final int LEAP = 1;
    private static final Map<UUID, float[]> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> AIR_STEP_USED = new ConcurrentHashMap<>();
    private static final Map<UUID, FallGrace> GRACE = new ConcurrentHashMap<>();
    private static final double BLINK_RANGE = 50.0;
    private static final double BOUND_SPEED = 2.6;

    private record FallGrace(double launchY, long since) {}

    private Mobility() {
    }

    public static void clear() {
        COOLDOWNS.clear();
        AIR_STEP_USED.clear();
        GRACE.clear();
    }

    public static float cooldownLeft(ServerPlayer p, int kind) {
        float[] cd = COOLDOWNS.get(p.getUUID());
        return cd == null ? 0.0f : cd[kind];
    }

    public static int unlockStage(int route, int kind) {
        return kind == DASH ? 1 : 2;
    }

    /** C is free to spam (0 s) on both routes; Z keeps its cooldown. */
    public static float cooldownSeconds(int route, int kind) {
        return kind == DASH ? 0.0f : 6.0f;
    }

    private static float cost(int route, int kind) {
        if (route == StageRules.GOJO) return kind == DASH ? 5.0f : 6.0f;
        return 6.0f;
    }

    private static void refuse(ServerPlayer p, String key, Object... args) {
        SukunaNet.actionBar(p, key, args);
    }

    public static void handle(ServerPlayer p, int kind, float forward, float strafe) {
        if (kind != DASH && kind != LEAP || !p.isAlive() || p.isSpectator()) return;
        CurseState s = CurseManager.of(p);
        if (!s.awakened()) {
            refuse(p, "sukuna.hint.dormant");
            return;
        }
        int route = s.route();
        String name = "sukuna.mobility." + route + "." + kind;
        if (s.stage() < unlockStage(route, kind)) {
            refuse(p, "sukuna.hint.mobility_locked", net.minecraft.network.chat.Component.translatable(name), StageRules.roman(unlockStage(route, kind)));
            return;
        }
        if (VoidDomainEntity.stunned(p)) {
            refuse(p, "sukuna.hint.stunned");
            return;
        }
        Skill channel = ChannelCasting.activeSkill(p);
        if (channel == Skill.MAHORAGA || CombatSkills.barrageActive(p)) {
            refuse(p, "sukuna.hint.action_locked");
            return;
        }
        float left = cooldownLeft(p, kind);
        if (left > 0.0f) {
            refuse(p, "sukuna.hint.mobility_cooldown", net.minecraft.network.chat.Component.translatable(name), String.format(Locale.ROOT, "%.1f", left));
            return;
        }
        if (route == StageRules.GOJO && kind == DASH && CurseManager.burntOut(p)) {
            refuse(p, "sukuna.hint.burnout_mobility");
            return;
        }
        if (!CurseManager.canAfford(p, cost(route, kind))) {
            refuse(p, "sukuna.hint.energy");
            return;
        }
        Vec3 dir = direction(p, forward, strafe);
        boolean done;
        if (route == StageRules.GOJO) {
            done = kind == DASH ? blink(p) : skyStep(p, forward, strafe, dir);
        } else {
            done = kind == DASH ? cursedBound(p, forward, strafe) : cursedLeap(p, forward, strafe, dir);
        }
        if (!done) return;
        // Using mobility cancels an unreleased charge or a heal; invested energy is not refunded.
        ChannelCasting.end(p);
        CurseManager.spend(p, cost(route, kind));
        float cd = cooldownSeconds(route, kind);
        if (cd > 0.0f) COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new float[2])[kind] = cd;
        CurseManager.sync(p);
    }

    private static Vec3 direction(ServerPlayer p, float forward, float strafe) {
        Vec3 f = Vec3.directionFromRotation(0.0f, p.getYRot());
        Vec3 leftVec = new Vec3(f.z, 0.0, -f.x);
        Vec3 d = f.scale(forward).add(leftVec.scale(strafe));
        return d.lengthSqr() < 0.01 ? f : d.normalize();
    }

    private static boolean hasInput(float forward, float strafe) {
        return Math.abs(forward) > 0.05f || Math.abs(strafe) > 0.05f;
    }

    /** Farthest safe point along the straight path; stops at the first obstruction. */
    private static double clearDistance(ServerPlayer p, Vec3 dir, double max) {
        ServerLevel level = p.level();
        AABB box = p.getBoundingBox();
        double best = 0.0;
        for (double d = 0.25; d <= max + 1.0E-4; d += 0.25) {
            Vec3 off = dir.scale(d);
            if (!level.noCollision(p, box.move(off))) break;
            if (VoidDomainEntity.crossesBoundary(level, p.position(), p.position().add(off))) break;
            best = d;
        }
        return best;
    }

    private static int headroom(ServerPlayer p, int max) {
        ServerLevel level = p.level();
        AABB box = p.getBoundingBox();
        int free = 0;
        for (int i = 1; i <= max; ++i) {
            if (!level.noCollision(p, box.move(0.0, i, 0.0))) break;
            free = i;
        }
        return free;
    }

    /** Spatial blink: appear where the crosshair points, up to 50 blocks away (blocks and creatures stop the ray). */
    private static boolean blink(ServerPlayer p) {
        ServerLevel level = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(BLINK_RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, limit, new AABB(eye, limit).inflate(1.0),
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p, 0.3f);
        Vec3 aim = hit != null ? hit.getLocation() : limit;
        Vec3 spot = safeSpot(p, aim, look);
        Vec3 from = p.position();
        if (spot == null || spot.distanceToSqr(from) < 1.0) {
            refuse(p, "sukuna.hint.blocked");
            return false;
        }
        if (VoidDomainEntity.crossesBoundary(level, from, spot)) {
            refuse(p, "sukuna.hint.shift_boundary");
            return false;
        }
        CurseFx.particles(level, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.7, 0.3, 0.1);
        ring(level, from.add(0.0, 1.0, 0.0), 0x9FE8FF, 1.0);
        p.teleportTo(spot.x, spot.y, spot.z);
        // Only the height the blink added is forgiven if the caster falls afterwards.
        GRACE.put(p.getUUID(), new FallGrace(Math.max(from.y, spot.y), level.getGameTime()));
        p.fallDistance = 0.0;
        ring(level, spot.add(0.0, 1.0, 0.0), 0x2F7BFF, 1.4);
        CurseFx.particles(level, ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 24, 0.4, 0.7, 0.4, 0.08);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.7f);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
        return true;
    }

    /** A spot at the aim point where the body fits, backing off toward the caster and stepping up. */
    public static Vec3 safeSpot(LivingEntity who, Vec3 aim, Vec3 dir) {
        ServerLevel level = (ServerLevel)who.level();
        Vec3 back = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize().scale(-0.25);
        AABB size = who.getBoundingBox().move(who.position().scale(-1.0));
        for (int b = 0; b <= 16; ++b) {
            Vec3 base = aim.add(back.scale(b));
            for (int up = 0; up <= 8; ++up) {
                Vec3 feet = base.add(0.0, up * 0.25 - 0.1, 0.0);
                if (level.noCollision(who, size.move(feet))) return feet;
            }
        }
        return null;
    }

    private static boolean skyStep(ServerPlayer p, float forward, float strafe, Vec3 dir) {
        if (!p.onGround() && AIR_STEP_USED.getOrDefault(p.getUUID(), false)) {
            refuse(p, "sukuna.hint.air_used");
            return false;
        }
        int free = headroom(p, 4);
        if (free <= 0) {
            refuse(p, "sukuna.hint.blocked");
            return false;
        }
        double vy = Math.sqrt(2.0 * 0.08 * Math.min(4.0, free) * 1.15);
        Vec3 horizontal = hasInput(forward, strafe) ? dir.scale(0.27) : Vec3.ZERO;
        if (!p.onGround()) AIR_STEP_USED.put(p.getUUID(), true);
        launch(p, new Vec3(horizontal.x, vy, horizontal.z));
        Vec3 feet = p.position();
        ring(p.level(), feet.add(0.0, 0.05, 0.0), 0x7FD8FF, 0.8);
        p.level().playSound(null, feet.x, feet.y, feet.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.2f);
        return true;
    }

    /** Cursed bound: a long, fast jump along the view (about 25 blocks). Once per stay in the air. */
    private static boolean cursedBound(ServerPlayer p, float forward, float strafe) {
        if (!p.onGround() && AIR_STEP_USED.getOrDefault(p.getUUID(), false)) {
            refuse(p, "sukuna.hint.air_used");
            return false;
        }
        Vec3 look = p.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-3 ? direction(p, forward, strafe) : flat.normalize();
        if (headroom(p, 1) <= 0 && clearDistance(p, dir, 1.0) < 0.5) {
            refuse(p, "sukuna.hint.blocked");
            return false;
        }
        double lift = Math.max(0.55, Math.min(1.25, 0.85 + look.y * 0.6));
        if (!p.onGround()) AIR_STEP_USED.put(p.getUUID(), true);
        Vec3 feet = p.position();
        launch(p, new Vec3(dir.x * BOUND_SPEED, lift, dir.z * BOUND_SPEED));
        ring(p.level(), feet.add(0.0, 0.1, 0.0), 0xB0121E, 1.4);
        CurseFx.particles(p.level(), ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, 18, 0.5, 0.05, 0.5, 0.08);
        CurseFx.particles(p.level(), new DustParticleOptions(0x8C0A14, 1.2f), feet.x, feet.y + 1.0, feet.z, 14, 0.3, 0.5, 0.3, 0.0);
        p.level().playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.5f);
        p.level().playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    private static boolean cursedLeap(ServerPlayer p, float forward, float strafe, Vec3 dir) {
        if (!p.onGround()) {
            refuse(p, "sukuna.hint.ground_only");
            return false;
        }
        int free = headroom(p, 5);
        if (free <= 0) {
            refuse(p, "sukuna.hint.blocked");
            return false;
        }
        double vy = Math.sqrt(2.0 * 0.08 * Math.min(5.0, free) * 1.15);
        Vec3 horizontal = hasInput(forward, strafe) ? dir.scale(0.36) : Vec3.ZERO;
        launch(p, new Vec3(horizontal.x, vy, horizontal.z));
        Vec3 feet = p.position();
        ring(p.level(), feet.add(0.0, 0.1, 0.0), 0xB0121E, 1.6);
        CurseFx.particles(p.level(), ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, 24, 1.0, 0.05, 1.0, 0.08);
        p.level().playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.5f);
        return true;
    }

    private static void launch(ServerPlayer p, Vec3 velocity) {
        p.setDeltaMovement(velocity);
        p.needsSync = true;
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        if (velocity.y > 0.2) {
            GRACE.put(p.getUUID(), new FallGrace(p.getY(), p.level().getGameTime()));
        }
    }

    public static void push(LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.needsSync = true;
        if (target instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    private static void ring(ServerLevel level, Vec3 c, int color, double r) {
        DustParticleOptions dust = new DustParticleOptions(color, 0.8f);
        for (int i = 0; i < 20; ++i) {
            double a = i * Math.PI * 2.0 / 20.0;
            CurseFx.particles(level, dust, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void tick(ServerPlayer p) {
        float[] cd = COOLDOWNS.get(p.getUUID());
        if (cd != null) {
            boolean any = false;
            for (int i = 0; i < cd.length; ++i) {
                if (cd[i] > 0.0f) {
                    cd[i] = Math.max(0.0f, cd[i] - 0.05f);
                    any = true;
                }
            }
            if (!any) COOLDOWNS.remove(p.getUUID());
        }
        if (p.onGround()) {
            AIR_STEP_USED.remove(p.getUUID());
        }
        FallGrace grace = GRACE.get(p.getUUID());
        if (grace != null) {
            long age = p.level().getGameTime() - grace.since();
            if (p.onGround() && age > 4L || age > 200L || !p.isAlive()) {
                GRACE.remove(p.getUUID());
            } else {
                // Only the height the technique added is forgiven; deeper drops still hurt.
                p.fallDistance = Math.min(p.fallDistance, Math.max(0.0, grace.launchY() - p.getY()));
            }
        }
    }

    public static boolean blockedAhead(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
