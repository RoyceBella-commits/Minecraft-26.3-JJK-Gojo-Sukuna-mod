package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.CurseSlashEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.RedBlastEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatSkills {
    private static final List<Combo> COMBOS = new ArrayList<Combo>();
    private static final int BARRAGE_PUNCHES = 5;
    private static final int BARRAGE_INTERVAL = 2;
    private static final double BARRAGE_RADIUS = 3.0;
    private static final List<Barrage> BARRAGES = new ArrayList<Barrage>();

    private CombatSkills() {
    }

    public static int cleaveCount(float charge) {
        return Math.min(20, 2 + Math.round(18.0f * Mth.clamp((float)(charge / 2.5f), (float)0.0f, (float)1.0f)));
    }

    public static boolean barrageActive(LivingEntity p) {
        for (Barrage b : BARRAGES) {
            if (b.caster.getUUID().equals(p.getUUID())) return true;
        }
        return false;
    }

    public static void clear() {
        COMBOS.clear();
        BARRAGES.clear();
    }

    public static void tick() {
        CombatSkills.tickCleave();
        Iterator<Barrage> it = BARRAGES.iterator();
        while (it.hasNext()) {
            Barrage b = it.next();
            if (!b.caster.isAlive() || b.caster.isRemoved() || b.caster.level() != b.world) {
                it.remove();
                continue;
            }
            if (b.age++ % 2 != 0) continue;
            if (b.nextPunch >= 5) {
                it.remove();
                continue;
            }
            CombatSkills.firePunch(b, b.nextPunch++);
            if (b.nextPunch < 5) continue;
            it.remove();
        }
    }

    private static void tickCleave() {
        Iterator<Combo> it = COMBOS.iterator();
        while (it.hasNext()) {
            Combo c = it.next();
            if (!c.caster.isAlive() || c.caster.isRemoved() || c.caster.level() != c.world || c.remaining <= 0) {
                it.remove();
                continue;
            }
            if (c.ticks++ % 2 != 0) continue;
            if (c.target != null) {
                if (c.target.isRemoved() || c.target.level() != c.caster.level()) {
                    it.remove();
                    continue;
                }
                c.point = c.target.getBoundingBox().getCenter();
            }
            ServerLevel w = (ServerLevel)c.caster.level();
            float size = 1.5f + c.charge * 1.5f;
            if (c.target != null && c.target.isAlive()) {
                c.target.setInvulnerableTime(0);
                CurseManager.curseDamage((Level)w, (Entity)c.caster, c.target, 5.0f + c.charge * 3.0f + c.target.getMaxHealth() * 0.012f, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category.SLASH);
            }
            float yaw = c.caster.getYRot() + (float)(c.remaining * 47);
            SlashFxEntity.spawn((Level)w, c.point.x, c.point.y, c.point.z, 1, yaw, (c.remaining % 3 - 1) * 35, size);
            TerrainCuts.plane(w, c.point, Vec3.directionFromRotation((float)((c.remaining % 3 - 1) * 35), (float)yaw), (double)size * 1.9, false, 0.5);
            if (c.remaining % 4 == 0) {
                w.playSound(null, c.point.x, c.point.y, c.point.z, SukunaSounds.SLASH2, SoundSource.PLAYERS, 1.4f, 0.8f + (float)c.remaining * 0.02f);
            }
            --c.remaining;
        }
    }

    public static void fireKai(LivingEntity p, float charge) {
        fireKai(p, p.getViewVector(1.0f), charge);
    }

    public static void fireKai(LivingEntity p, Vec3 dir, float charge) {
        CurseSlashEntity.spawn((ServerLevel)p.level(), p, dir, p.getEyePosition().add(dir.scale(1.3)), charge);
        CombatSkills.sound(p, SukunaSounds.SLASH1);
    }

    /** Cleave locked onto a known target (NPC use). */
    public static void fireCleaveOn(LivingEntity p, LivingEntity target, float charge) {
        COMBOS.add(new Combo(p, target, target.getBoundingBox().getCenter(), charge));
        CombatSkills.sound(p, SukunaSounds.SLASH2);
    }

    public static void fireCleave(LivingEntity p, float charge) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1.0f).scale(100.0));
        LivingEntity target = null;
        double nearest = 10001.0;
        for (Entity e : p.level().getEntities((Entity)p, new AABB(eye, end).inflate(3.0))) {
            Optional hit;
            Player player;
            LivingEntity living;
            if (!(e instanceof LivingEntity) || !(living = (LivingEntity)e).isAlive() || e instanceof Player && ((player = (Player)e).isCreative() || player.isSpectator()) || !(hit = e.getBoundingBox().inflate(0.35).clip(eye, end)).isPresent() || !(eye.distanceToSqr((Vec3)hit.get()) < nearest)) continue;
            target = living;
            nearest = eye.distanceToSqr((Vec3)hit.get());
        }
        Vec3 point = target == null ? p.pick(100.0, 0.0f, false).getLocation() : target.getBoundingBox().getCenter();
        COMBOS.add(new Combo(p, target, point, charge));
        CombatSkills.sound(p, SukunaSounds.SLASH2);
    }

    public static void fireCursedBarrage(LivingEntity p, float charge) {
        BARRAGES.removeIf(b -> b.caster.getUUID().equals(p.getUUID()));
        BARRAGES.add(new Barrage(p, charge));
        CombatSkills.sound(p, SukunaSounds.SLASH1);
    }

    private static void firePunch(Barrage b, int index) {
        LivingEntity p = b.caster;
        ServerLevel w = (ServerLevel)p.level();
        boolean left = (index & 1) == 0;
        Vec3 center = p.position().add(0.0, (double)p.getEyeHeight() * 0.58, 0.0).add(b.direction.scale(1.15)).add(b.right.scale(left ? -0.5 : 0.5));
        float power = 4.5f + b.charge * 2.6f;
        HashSet<UUID> hit = new HashSet<UUID>();
        for (Entity e : w.getEntities((Entity)p, new AABB(center.subtract(3.0, 3.0, 3.0), center.add(3.0, 3.0, 3.0)))) {
            Player player;
            LivingEntity target;
            if (!(e instanceof LivingEntity) || !(target = (LivingEntity)e).isAlive() || target instanceof Player && ((player = (Player)target).isCreative() || player.isSpectator()) || target.getBoundingBox().getCenter().distanceToSqr(center) > 9.0 || !hit.add(target.getUUID())) continue;
            CurseManager.curseDamage((Level)w, (Entity)p, target, power, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category.FIST);
            if (index != 4 || CurseManager.protectedTarget(p, target)) continue;
            Vec3 knock = b.direction.normalize().scale(1.35 + (double)b.charge * 0.35);
            target.knockback(1.35 + (double)b.charge * 0.35, -knock.x, -knock.z, SukunaDamage.cursed(w, b.caster), power);
            target.push(0.0, 0.32 + (double)b.charge * 0.08, 0.0);
            target.needsSync = true;
        }
        float yaw = (float)Math.toDegrees(Math.atan2(-b.direction.x, b.direction.z));
        SlashFxEntity.spawn((Level)w, center.x, center.y, center.z, 7, yaw, 0.0f, 1.35f + b.charge * 0.25f);
        CurseFx.particles((Level)w, (ParticleOptions)ParticleTypes.CRIT, center.x, center.y, center.z, 16, 0.45, 0.35, 0.45, 0.12);
        CurseFx.particles((Level)w, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 8, 0.25, 0.25, 0.25, 0.015);
        if (index == 4) {
            SlashFxEntity.spawn((Level)w, center.x, center.y, center.z, 8, yaw, 0.0f, 2.6f + b.charge * 0.5f);
            CurseFx.particles((Level)w, (ParticleOptions)ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            CurseFx.particles((Level)w, (ParticleOptions)ParticleTypes.CLOUD, center.x, center.y - 0.3, center.z, 52, 1.1, 0.25, 1.1, 0.18);
            CurseFx.particles((Level)w, (ParticleOptions)ParticleTypes.END_ROD, center.x, center.y, center.z, 28, 0.65, 0.65, 0.65, 0.16);
            CurseFx.particles((Level)w, (ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 32, 0.8, 0.55, 0.8, 0.07);
        }
        w.playSound(null, center.x, center.y, center.z, index == 4 ? SukunaSounds.SLASH2 : SukunaSounds.SLASH1, SoundSource.PLAYERS, index == 4 ? 1.8f : 1.15f, 0.92f + (float)index * 0.035f);
        if (p instanceof ServerPlayer sp) SukunaNet.punch(sp, index, left, center, b.direction, b.charge);
    }

    public static void castRct(ServerPlayer player, float charge) {
    }

    public static void fireRed(LivingEntity p, float charge) {
        fireRed(p, p.getViewVector(1.0f), charge);
    }

    public static void fireRed(LivingEntity p, Vec3 dir, float charge) {
        RedBlastEntity.create((ServerLevel)p.level(), p, dir, charge, 2.5 + (double)charge * 0.8);
        CombatSkills.sound(p, SukunaSounds.RED_SHOOT);
    }

    public static void fireWorldCut(LivingEntity p, float charge) {
        fireWorldCut(p, p.getViewVector(1.0f), charge);
    }

    /** World Cut: a single cut through space along the aim. */
    public static void fireWorldCut(LivingEntity p, Vec3 dir, float charge) {
        CurseSlashEntity.launch((ServerLevel)p.level(), p, dir, p.getEyePosition().add(dir.normalize().scale(2.0)), charge, 2);
        CombatSkills.sound(p, SukunaSounds.WORLD_CUT);
        cn.blockforge.ryomensukuna.m2a542fea.combat.Finishers.released(p, true);
    }

    private static void sound(LivingEntity p, SoundEvent sound) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 2.0f, 0.85f);
    }

    private static final class Barrage {
        final LivingEntity caster;
        final ServerLevel world;
        final Vec3 direction;
        final Vec3 right;
        final float charge;
        int nextPunch;
        int age;

        Barrage(LivingEntity p, float q) {
            this.caster = p;
            this.world = (ServerLevel)p.level();
            this.charge = Mth.clamp((float)q, (float)0.0f, (float)2.5f);
            this.direction = p.getViewVector(1.0f).normalize();
            this.right = new Vec3(-this.direction.z, 0.0, this.direction.x).normalize();
        }
    }

    private static final class Combo {
        LivingEntity caster;
        final ServerLevel world;
        LivingEntity target;
        Vec3 point;
        float charge;
        int remaining;
        int ticks;

        Combo(LivingEntity p, LivingEntity t, Vec3 c, float q) {
            this.caster = p;
            this.world = (ServerLevel)p.level();
            this.target = t;
            this.point = c;
            this.charge = q;
            this.remaining = CombatSkills.cleaveCount(q);
        }
    }
}

