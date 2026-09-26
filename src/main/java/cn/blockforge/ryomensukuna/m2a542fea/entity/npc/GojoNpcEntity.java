package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Gojo Satoru (spawn egg): hunts hostile creatures, Sukuna-route players, the Sukuna NPC and
 * Mahoraga. Infinity is always up; uses Ao, Aka, a rare Hollow Purple, blinks and Unlimited Void.
 */
public class GojoNpcEntity extends JjkNpcEntity {
    private int murasakiCooldown = 300;
    private int blinkCooldown;
    /** Ticks left holding an Ao circling this NPC; it is let go (and stays) when this runs out. */
    private int orbitTicks;

    public GojoNpcEntity(EntityType<? extends GojoNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean canHarm(LivingEntity t) {
        if (t == this || !t.isAlive() || ignoredPlayer(t) || t instanceof GojoNpcEntity) return false;
        return t instanceof Enemy || t instanceof MahoragaEntity || sukunaPlayer(t);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel && this.isAlive()) {
            Infinity.shield(this);
            if (this.murasakiCooldown > 0) --this.murasakiCooldown;
            if (this.blinkCooldown > 0) --this.blinkCooldown;
            if (this.orbitTicks > 0) {
                if (--this.orbitTicks == 0 || !AoEntity.grab(this)) {
                    AoEntity.release(this);
                    this.orbitTicks = 0;
                }
            }
        }
    }

    @Override
    protected boolean useSkill(ServerLevel level, LivingEntity target, double distance) {
        Vec3 aim = this.aimAt(target);
        if (this.blinkCooldown <= 0 && this.getHealth() < this.getMaxHealth() * 0.3f) {
            Vec3 away = this.position().subtract(target.position());
            away = away.lengthSqr() < 1.0E-3 ? this.getViewVector(1.0f).scale(-1.0) : away.normalize();
            if (this.blink(level, this.position().add(away.scale(12.0)), away)) return true;
        }
        if (distance > 20.0 && this.blinkCooldown <= 0) {
            Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
            Vec3 near = target.position().subtract(flat.lengthSqr() < 1.0E-3 ? Vec3.ZERO : flat.normalize().scale(3.0));
            if (this.blink(level, near, aim)) return true;
        }
        if (this.murasakiCooldown <= 0 && strong(target) && distance > 6.0) {
            GojoSkills.fireMurasaki(this, aim, -1);
            this.murasakiCooldown = 600;
            return true;
        }
        float roll = this.random.nextFloat();
        // Crowded or pressed hard: an Ao circling close around itself, held about 5 s.
        if (AoEntity.of(this) == null && (this.foesNear(6.0) >= 2 || distance < 4.0) && roll < 0.5f) {
            AoEntity.summonOrbiting(this, 1.5f, -1);
            this.orbitTicks = 100;
            return true;
        }
        if (distance < 7.0) {
            if (roll > 0.75f) return false;
            GojoSkills.fireAoAt(this, target.getBoundingBox().getCenter(), 1.0f, -1);
            return true;
        }
        if (roll < 0.65f) {
            GojoSkills.fireAka(this, aim, 1.5f, -1);
        } else {
            GojoSkills.fireAoAt(this, target.getBoundingBox().getCenter(), 1.5f, -1);
        }
        return true;
    }

    @Override
    protected boolean castDomain(ServerLevel level) {
        GojoSkills.expandVoid(this, -1, StageRules.DOMAIN_TICKS_MAX);
        return true;
    }

    @Override
    protected boolean domainActive() {
        return GojoSkills.voidActive(this);
    }

    /** Blink to the foe's flank, then fight hand to hand. */
    @Override
    protected void gapClose(ServerLevel level, LivingEntity target) {
        Vec3 aim = this.aimAt(target);
        Vec3 flank = new Vec3(-aim.z, 0.0, aim.x);
        flank = flank.lengthSqr() < 1.0E-3 ? Vec3.ZERO : flank.normalize().scale(this.random.nextBoolean() ? 2.0 : -2.0);
        this.blink(level, target.position().add(flank), aim);
    }

    private boolean blink(ServerLevel level, Vec3 aim, Vec3 dir) {
        Vec3 spot = Mobility.safeSpot(this, aim, dir);
        if (spot == null) return false;
        Vec3 from = this.position();
        CurseFx.particles(level, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.7, 0.3, 0.1);
        this.teleportTo(spot.x, spot.y, spot.z);
        this.getNavigation().stop();
        this.fallDistance = 0.0;
        CurseFx.particles(level, ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 20, 0.4, 0.7, 0.4, 0.08);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 1.7f);
        this.blinkCooldown = 60;
        return true;
    }
}
