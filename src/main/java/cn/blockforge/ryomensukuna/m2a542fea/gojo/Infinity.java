package cn.blockforge.ryomensukuna.m2a542fea.gojo;

import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.GojoNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.SukunaNpcEntity;
import net.minecraft.network.chat.Component;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.skill.SukunaDamage;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mugen. While switched on nothing reaches the user: blows, projectiles, falls, lava and fire.
 * Only Sukuna's side can break it: World Cut always passes; three slashes / flames / fists (or
 * three Mahoraga blows) break that kind for 5 s. Damage that bypasses invulnerability (/kill,
 * the void) and hunger, drowning or effects are never blocked. Arrows stop in front of the user.
 */
public final class Infinity {
    private static final float UPKEEP_PER_SECOND = 0.5f;
    private static final float BLOCK_COST = 2.0f;
    private static final float ENVIRONMENT_COST = 0.5f;
    private static final double CATCH_RADIUS = 4.0;
    private static final double PIN_DISTANCE = 1.6;
    private static final int HOLD_TICKS = 40;
    private static final Map<Projectile, Hold> HELD = new HashMap<>();
    private static final Map<LivingEntity, Long> LAST_CHIME = new java.util.WeakHashMap<>();
    private static final Map<java.util.UUID, InfinityBreach> BREACH = new java.util.concurrent.ConcurrentHashMap<>();
    /** Kind of the technique currently dealing damage (set around the hurt call by CurseManager). */
    public static InfinityBreach.Category pending;

    private record Hold(LivingEntity holder, Vec3 pin, long until) {}

    private Infinity() {
    }

    public static void clear() {
        HELD.clear();
        LAST_CHIME.clear();
        BREACH.clear();
        pending = null;
    }

    public static boolean switchedOn(ServerPlayer p) {
        CurseState s = CurseManager.of(p);
        return s.route() == StageRules.GOJO && s.flag(CurseState.FLAG_INFINITY);
    }

    public static boolean active(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && switchedOn(p) && !CurseManager.burntOut(p);
    }

    /** Players with Infinity up, and the Gojo NPC (always up while it lives). */
    public static boolean protects(LivingEntity e) {
        if (e instanceof ServerPlayer p) return active(p);
        return e instanceof GojoNpcEntity npc && npc.isAlive();
    }

    private static float discount(ServerPlayer p) {
        return CurseManager.of(p).stage() >= 3 ? 0.5f : 1.0f;
    }

    public static void toggle(ServerPlayer p) {
        CurseState s = CurseManager.of(p);
        if (s.route() != StageRules.GOJO) {
            return;
        }
        boolean on = !s.flag(CurseState.FLAG_INFINITY);
        if (on && CurseManager.burntOut(p)) {
            SukunaNet.actionBar(p, "sukuna.hint.infinity_burnout");
            return;
        }
        if (on && !CurseManager.canAfford(p, BLOCK_COST)) {
            SukunaNet.actionBar(p, "sukuna.hint.energy");
            return;
        }
        CurseManager.setState(p, s.withFlag(CurseState.FLAG_INFINITY, on));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
        SukunaNet.actionBar(p, on ? "sukuna.hint.infinity_on" : "sukuna.hint.infinity_off");
        CurseManager.sync(p);
    }

    public static void onBurnoutStart(ServerPlayer p) {
        if (switchedOn(p)) {
            SukunaNet.actionBar(p, "sukuna.hint.infinity_sealed");
        }
    }

    /** The previous on/off choice is kept through burnout and restored if affordable. */
    public static void onBurnoutEnd(ServerPlayer p) {
        if (switchedOn(p) && !CurseManager.canAfford(p, BLOCK_COST)) {
            CurseManager.setState(p, CurseManager.of(p).withFlag(CurseState.FLAG_INFINITY, false));
            SukunaNet.actionBar(p, "sukuna.hint.infinity_no_energy");
        }
    }

    private static void switchOff(ServerPlayer p, String reason) {
        CurseManager.setState(p, CurseManager.of(p).withFlag(CurseState.FLAG_INFINITY, false));
        SukunaNet.actionBar(p, reason);
        CurseManager.sync(p);
    }

    public static void tick(ServerPlayer p) {
        if (!active(p)) return;
        if (p.level().getGameTime() % 20L == 0L && !CurseManager.spend(p, UPKEEP_PER_SECOND * discount(p))) {
            switchOff(p, "sukuna.hint.infinity_no_energy");
            return;
        }
        shield(p);
    }

    /** Catches incoming projectiles: arrows and thrown items are pinned in front, the rest dissolve. */
    public static void shield(LivingEntity e) {
        ServerLevel level = (ServerLevel)e.level();
        Vec3 center = e.getBoundingBox().getCenter();
        List<Projectile> near = level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(CATCH_RADIUS),
            proj -> proj.getOwner() != e && !proj.isRemoved() && !HELD.containsKey(proj));
        for (Projectile proj : near) {
            Vec3 v = proj.getDeltaMovement();
            Vec3 offset = proj.position().subtract(center);
            if (v.lengthSqr() < 1.0E-4 || v.dot(offset) >= 0.0) continue;
            if (proj instanceof AbstractArrow || proj instanceof ThrowableProjectile) {
                double dist = Math.max(PIN_DISTANCE, offset.length());
                Vec3 dir = offset.lengthSqr() < 1.0E-4 ? v.normalize().scale(-1.0) : offset.normalize();
                Vec3 pin = center.add(dir.scale(Math.min(dist, PIN_DISTANCE + 0.4)));
                HELD.put(proj, new Hold(e, pin, level.getGameTime() + HOLD_TICKS));
                pin(proj, pin);
                ripple(level, pin, center);
                level.playSound(null, pin.x, pin.y, pin.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.9f);
            } else if (offset.length() < 2.5) {
                CurseFx.particles(level, ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
                proj.discard();
            } else {
                proj.setDeltaMovement(v.scale(0.35));
                proj.needsSync = true;
            }
        }
    }

    private static void pin(Projectile proj, Vec3 at) {
        proj.setPos(at.x, at.y, at.z);
        proj.setDeltaMovement(Vec3.ZERO);
        proj.setNoGravity(true);
        proj.needsSync = true;
    }

    /** Keeps caught projectiles hanging in the air, then lets them drop. Runs once per server tick. */
    public static void tickHeld() {
        Iterator<Map.Entry<Projectile, Hold>> it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Projectile, Hold> entry = it.next();
            Projectile proj = entry.getKey();
            Hold hold = entry.getValue();
            if (proj.isRemoved()) {
                it.remove();
                continue;
            }
            LivingEntity holder = hold.holder();
            boolean keep = proj.level().getGameTime() < hold.until() && holder.isAlive() && !holder.isRemoved()
                && holder.level() == proj.level() && protects(holder) && holder.position().distanceToSqr(hold.pin()) < 36.0;
            if (!keep) {
                proj.setNoGravity(false);
                proj.setDeltaMovement(0.0, -0.05, 0.0);
                proj.needsSync = true;
                it.remove();
                continue;
            }
            if (proj.position().distanceToSqr(hold.pin()) > 1.0E-4 || proj.getDeltaMovement().lengthSqr() > 1.0E-6) {
                pin(proj, hold.pin());
            }
        }
    }

    /** Environmental harm that "approaches" the body and is stopped by the infinite distance. */
    private static boolean blocksEnvironment(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
            || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)
            || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.FALLING_BLOCK)
            || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.STALAGMITE)
            || source.is(DamageTypes.FLY_INTO_WALL);
    }

    /** Sukuna's side: Sukuna-route players and the Sukuna NPC. Only they (and Mahoraga) can break Infinity. */
    private static boolean sukunaSide(Entity e) {
        return e instanceof SukunaNpcEntity || e instanceof ServerPlayer sp && CurseManager.of(sp).route() == StageRules.SUKUNA;
    }

    /** Which kind of attack this is for breaking Infinity, or null if it can never break it. */
    private static InfinityBreach.Category category(Entity attacker, DamageSource source) {
        if (attacker instanceof MahoragaEntity) return InfinityBreach.Category.MAHORAGA;
        if (!sukunaSide(attacker)) return null;
        if (pending != null) return pending;
        if (source.is(DamageTypeTags.IS_FIRE)) return InfinityBreach.Category.FLAME;
        if (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)) return InfinityBreach.Category.FIST;
        return InfinityBreach.Category.SLASH;
    }

    private static Component categoryName(InfinityBreach.Category c) {
        return Component.translatable("sukuna.category." + c.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** ALLOW_DAMAGE hook. Returns false when Infinity stops the harm. */
    public static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        if (!protects(target)) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker == target) return true;
        if (source.is(SukunaDamage.WORLD_CUT)) {
            crack(target, direct != null ? direct : attacker);
            return true;
        }
        InfinityBreach.Category kind = attacker == null ? null : category(attacker, source);
        if (kind != null) {
            long now = target.level().getGameTime();
            InfinityBreach breach = BREACH.computeIfAbsent(target.getUUID(), k -> new InfinityBreach());
            InfinityBreach.Result result = breach.hit(kind, now);
            if (attacker instanceof MahoragaEntity mahoraga) {
                mahoraga.onInfinityHit(result == InfinityBreach.Result.COUNTED ? breach.count(kind) : InfinityBreach.HITS_TO_BREAK, result);
            }
            if (result != InfinityBreach.Result.COUNTED) {
                crack(target, direct != null ? direct : attacker);
                if (result == InfinityBreach.Result.BROKE) {
                    if (target instanceof ServerPlayer p) SukunaNet.actionBar(p, "sukuna.hint.infinity_breached", categoryName(kind));
                    if (attacker instanceof ServerPlayer sp) SukunaNet.actionBar(sp, "sukuna.hint.infinity_broken_by", categoryName(kind));
                    target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
                }
                return true;
            }
            if (attacker instanceof ServerPlayer sp) {
                SukunaNet.actionBar(sp, "sukuna.hint.infinity_break_progress", categoryName(kind), breach.count(kind));
            }
        }
        boolean environment = attacker == null && direct == null;
        if (environment && !blocksEnvironment(source)) return true;
        // Lava and fire call in every tick; only pay when the hit would really land (outside i-frames).
        boolean charged = !environment || target.getInvulnerableTime() <= 10;
        if (charged && target instanceof ServerPlayer p && !CurseManager.spend(p, (environment ? ENVIRONMENT_COST : BLOCK_COST) * discount(p))) {
            switchOff(p, "sukuna.hint.infinity_no_energy");
            return true;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) target.clearFire();
        if (!environment) {
            Entity from = direct != null ? direct : attacker;
            ripple((ServerLevel)target.level(), from.getBoundingBox().getCenter(), target.getBoundingBox().getCenter());
        }
        long now = target.level().getGameTime();
        Long last = LAST_CHIME.get(target);
        if (last == null || now - last >= 10L) {
            LAST_CHIME.put(target, now);
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.8f);
        }
        return false;
    }

    private static void ripple(ServerLevel level, Vec3 from, Vec3 center) {
        Vec3 dir = from.subtract(center);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0.0, 0.0, 1.0);
        Vec3 n = dir.normalize();
        Vec3 point = center.add(n.scale(0.9));
        Vec3 a = n.cross(new Vec3(0.0, 1.0, 0.0));
        if (a.lengthSqr() < 1.0E-4) a = new Vec3(1.0, 0.0, 0.0);
        a = a.normalize();
        Vec3 b = n.cross(a).normalize();
        DustParticleOptions ring = new DustParticleOptions(0xBFEFFF, 0.6f);
        for (int i = 0; i < 12; ++i) {
            double t = i * Math.PI * 2.0 / 12.0;
            Vec3 q = point.add(a.scale(Math.cos(t) * 0.45)).add(b.scale(Math.sin(t) * 0.45));
            CurseFx.particles(level, ring, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void crack(LivingEntity p, Entity from) {
        Vec3 c = p.getBoundingBox().getCenter();
        Vec3 f = from == null ? c.add(0.0, 0.0, 1.0) : from.getBoundingBox().getCenter();
        Vec3 d = f.subtract(c);
        Vec3 point = d.lengthSqr() < 1.0E-4 ? c : c.add(d.normalize().scale(0.8));
        CurseFx.particles(p.level(), new DustParticleOptions(0x5A1F7A, 1.0f), point.x, point.y, point.z, 14, 0.25, 0.35, 0.25, 0.0);
        CurseFx.particles(p.level(), ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, 10, 0.2, 0.3, 0.2, 0.05);
    }
}
