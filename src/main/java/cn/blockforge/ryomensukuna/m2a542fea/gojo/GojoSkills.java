package cn.blockforge.ryomensukuna.m2a542fea.gojo;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.AkaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MurasakiEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Gojo techniques. Player entry points aim with the view; the generic ones take an explicit aim (NPCs). */
public final class GojoSkills {
    private static final double AO_RANGE = 32.0;
    private static final Map<UUID, VoidDomainEntity> VOIDS = new ConcurrentHashMap<>();

    private GojoSkills() {
    }

    public static void clear() {
        VOIDS.clear();
        AoEntity.clear();
        VoidDomainEntity.clearStuns();
    }

    public static boolean voidActive(LivingEntity p) {
        VoidDomainEntity v = VOIDS.get(p.getUUID());
        if (v == null || v.isRemoved()) {
            VOIDS.remove(p.getUUID());
            return false;
        }
        return true;
    }

    /** Where the player is looking, up to 32 blocks; stops one block short of walls. */
    private static Vec3 aimPoint(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(AO_RANGE));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation().subtract(look.scale(1.0));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, limit, new AABB(eye, limit).inflate(1.0),
            e -> e instanceof LivingEntity && e.isAlive() && e != p, 0.3f);
        return hit != null ? hit.getEntity().getBoundingBox().getCenter() : limit;
    }

    public static void fireAo(ServerPlayer p, float charge, int castId) {
        fireAoAt(p, aimPoint(p), charge, castId);
    }

    public static AoEntity fireAoAt(LivingEntity caster, Vec3 at, float charge, int castId) {
        ServerLevel level = (ServerLevel)caster.level();
        AoEntity ao = new AoEntity(SukunaMod.AO, level);
        ao.configure(caster, charge, castId);
        ao.setPos(at);
        level.addFreshEntity(ao);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.6f);
        return ao;
    }

    public static void fireAka(ServerPlayer p, float charge, int castId) {
        fireAka(p, p.getViewVector(1.0f), charge, castId);
    }

    public static void fireAka(LivingEntity caster, Vec3 dir, float charge, int castId) {
        ServerLevel level = (ServerLevel)caster.level();
        AkaEntity aka = new AkaEntity(SukunaMod.AKA, level);
        aka.configure(caster, dir, charge, castId);
        aka.setPos(caster.getEyePosition().add(dir.normalize().scale(1.0)).add(0.0, -0.2, 0.0));
        level.addFreshEntity(aka);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 1.5f);
    }

    public static void fireMurasaki(ServerPlayer p, float charge, int castId) {
        fireMurasaki(p, p.getViewVector(1.0f), castId);
    }

    public static void fireMurasaki(LivingEntity caster, Vec3 dir, int castId) {
        ServerLevel level = (ServerLevel)caster.level();
        MurasakiEntity m = new MurasakiEntity(SukunaMod.MURASAKI, level);
        m.configure(caster, dir, castId);
        // Spawned a little further ahead so the huge mass does not fill the caster's view.
        m.setPos(caster.getEyePosition().add(dir.normalize().scale(MurasakiEntity.RADIUS + 2.0)).add(0.0, -0.3, 0.0));
        level.addFreshEntity(m);
        cn.blockforge.ryomensukuna.m2a542fea.combat.Finishers.released(caster, false);
    }

    /** Ao struck by Aka in flight: a lesser Hollow Purple bursts out where the Ao was, along Aka's path. */
    public static MurasakiEntity fusePurple(LivingEntity caster, Vec3 at, Vec3 dir, int castId) {
        ServerLevel level = (ServerLevel)caster.level();
        MurasakiEntity m = new MurasakiEntity(SukunaMod.MURASAKI, level);
        m.configureFused(caster, dir, castId);
        m.setPos(at);
        level.addFreshEntity(m);
        for (int i = 0; i < 32; ++i) {
            double a = i * Math.PI * 2.0 / 32.0;
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, Math.cos(a) * 0.5, Math.sin(a) * 0.5, 0.0, 1.0);
        }
        CurseFx.particles(level, new DustParticleOptions(0xC08CFF, 3.0f), at.x, at.y, at.z, 60, 1.5, 1.5, 1.5, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.7f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.6f, 1.2f);
        cn.blockforge.ryomensukuna.m2a542fea.combat.Finishers.released(caster, false);
        return m;
    }

    public static void expandVoid(ServerPlayer p, int castId) {
        expandVoid(p, castId, StageRules.domainTicks(CurseManager.of(p).stage()));
        SukunaNet.actionBar(p, "sukuna.hint.void");
    }

    public static void expandVoid(LivingEntity caster, int castId, int duration) {
        ServerLevel level = (ServerLevel)caster.level();
        VoidDomainEntity v = new VoidDomainEntity(SukunaMod.VOID_DOMAIN, level);
        v.configure(caster, castId, duration);
        v.setPos(caster.position().add(0.0, 1.0, 0.0));
        level.addFreshEntity(v);
        VOIDS.put(caster.getUUID(), v);
    }

    public static Entity activeVoid(LivingEntity p) {
        return voidActive(p) ? VOIDS.get(p.getUUID()) : null;
    }
}
