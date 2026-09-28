package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MurasakiEntity;
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
    /** Fused Purple combo: an Ao set down, then Aka fired into it, then riding out behind the Purple. */
    private static final int COMBO_COOLDOWN = 400;
    private int comboCooldown = 200;
    private int comboStep;
    private int comboTimer;
    private Vec3 comboPoint;
    private int comboWait;

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
            if (this.comboCooldown > 0) --this.comboCooldown;
            if (this.comboStep > 0) this.tickCombo((ServerLevel)this.level());
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
            if (roll < 0.35f) {
                GojoSkills.fireAka(this, aim, 1.0f, -1);
            } else {
                GojoSkills.fireAoAt(this, target.getBoundingBox().getCenter(), 1.0f, -1);
            }
            return true;
        }
        if (roll < 0.8f) {
            GojoSkills.fireAka(this, aim, 1.5f, -1);
        } else {
            GojoSkills.fireAoAt(this, target.getBoundingBox().getCenter(), 1.5f, -1);
        }
        return true;
    }

    /** Pressed hard (a long fight or below half health), or facing Mahoraga: the fused Purple. */
    @Override
    protected boolean hardFightMove(ServerLevel level, LivingEntity target, double distance) {
        boolean hard = this.longFight() || this.getHealth() < this.getMaxHealth() * 0.5f;
        if (!(hard || target instanceof MahoragaEntity) || this.comboCooldown > 0 || this.comboStep > 0 || distance < 7.0) return false;
        Vec3 aim = this.aimAt(target);
        this.comboPoint = this.getEyePosition().add(aim.scale(6.0));
        AoEntity ao = GojoSkills.fireAoAt(this, this.comboPoint, 0.5f, -1);
        this.comboPoint = ao.position();
        this.comboStep = 1;
        this.comboTimer = 8;
        this.comboCooldown = COMBO_COOLDOWN;
        return true;
    }

    private void tickCombo(ServerLevel level) {
        if (--this.comboTimer > 0) return;
        if (this.comboStep == 1) {
            // Aka straight into the waiting Ao.
            AoEntity ao = AoEntity.of(this);
            if (ao == null) {
                this.comboStep = 0;
                return;
            }
            Vec3 dir = ao.position().subtract(this.getEyePosition());
            if (dir.lengthSqr() < 1.0E-3) dir = this.getViewVector(1.0f);
            GojoSkills.fireAka(this, dir.normalize(), 1.5f, -1);
            this.comboStep = 2;
            this.comboTimer = 1;
            this.comboWait = 20;
            return;
        }
        // Step 2: once the Purple is born, ride out behind it along its path.
        for (MurasakiEntity m : level.getEntitiesOfClass(MurasakiEntity.class, this.getBoundingBox().inflate(24.0),
                m -> m.fused() && m.owner() == this && m.age() < 6)) {
            Vec3 h = m.heading();
            this.setDeltaMovement(h.x * 2.2, Math.max(0.35, h.y * 2.2 + 0.35), h.z * 2.2);
            this.needsSync = true;
            this.fallDistance = 0.0;
            this.getNavigation().stop();
            CurseFx.particles(level, ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 16, 0.3, 0.6, 0.3, 0.05);
            this.comboStep = 0;
            return;
        }
        if (--this.comboWait <= 0) {
            this.comboStep = 0;
        } else {
            this.comboTimer = 1;
        }
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

    @Override
    protected void disengage(ServerLevel level, LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        away = new Vec3(away.x, 0.0, away.z);
        away = away.lengthSqr() < 1.0E-3 ? this.getViewVector(1.0f).scale(-1.0) : away.normalize();
        this.blinkCooldown = 0;
        this.blink(level, target.position().add(away.scale(14.0 + this.random.nextDouble() * 4.0)), away);
    }

    @Override
    protected boolean tryFinisher(ServerLevel level, LivingEntity target) {
        if (this.murasakiCooldown > 0) return false;
        GojoSkills.fireMurasaki(this, this.aimAt(target), -1);
        this.murasakiCooldown = 600;
        return true;
    }

    @Override
    protected void counterFinisher(ServerLevel level, LivingEntity target) {
        GojoSkills.fireMurasaki(this, this.aimAt(target), -1);
    }

    @Override
    protected boolean answers(boolean worldCut) {
        return worldCut;
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
