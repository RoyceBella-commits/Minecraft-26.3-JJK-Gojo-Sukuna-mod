package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CombatSkills;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ryomen Sukuna (spawn egg): attacks every living thing except players who chose the Sukuna route
 * (and other Sukuna / Mahoraga). Uses Dismantle, Cleave, the barrage, Flame Arrow, World Cut and
 * Malevolent Shrine; never summons Mahoraga.
 */
public class SukunaNpcEntity extends JjkNpcEntity implements Enemy {
    private int worldCutCooldown = 400;

    public SukunaNpcEntity(EntityType<? extends SukunaNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean canHarm(LivingEntity t) {
        if (t == this || !t.isAlive() || ignoredPlayer(t)) return false;
        if (t instanceof SukunaNpcEntity || t instanceof MahoragaEntity || t instanceof ArmorStand) return false;
        return !sukunaPlayer(t);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.worldCutCooldown > 0 && !this.level().isClientSide()) --this.worldCutCooldown;
    }

    @Override
    protected boolean useSkill(ServerLevel level, LivingEntity target, double distance) {
        Vec3 aim = this.aimAt(target);
        float roll = this.random.nextFloat();
        if (distance < 4.0) {
            if (roll > 0.4f) return false;
            CombatSkills.fireCursedBarrage(this, 1.5f);
            return true;
        }
        if (distance < 16.0) {
            if (roll < 0.45f) {
                CombatSkills.fireCleaveOn(this, target, 1.2f);
            } else {
                CombatSkills.fireKai(this, aim, 1.0f);
            }
            return true;
        }
        if (this.worldCutCooldown <= 0 && strong(target)) {
            CombatSkills.fireWorldCut(this, aim, 2.0f);
            this.worldCutCooldown = 800;
        } else if (roll < 0.3f) {
            CombatSkills.fireRed(this, aim, 1.5f);
        } else {
            CombatSkills.fireKai(this, aim, 1.0f);
        }
        return true;
    }

    @Override
    protected boolean castDomain(ServerLevel level) {
        return DomainSkill.castFor(this, StageRules.DOMAIN_TICKS_MAX, -1);
    }

    @Override
    protected boolean canDomainWhileHeld() {
        return true;
    }

    @Override
    protected boolean domainActive() {
        return DomainSkill.active(this);
    }

    /** A great bound backward, away from the foe. */
    @Override
    protected void disengage(ServerLevel level, LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0.0, away.z);
        flat = flat.lengthSqr() < 1.0E-3 ? this.getViewVector(1.0f).scale(-1.0) : flat.normalize();
        this.setDeltaMovement(flat.scale(2.2).add(0.0, 0.8, 0.0));
        this.needsSync = true;
        this.fallDistance = 0.0;
        this.getNavigation().stop();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 18, 0.5, 0.05, 0.5, 0.08);
    }

    @Override
    protected boolean tryFinisher(ServerLevel level, LivingEntity target) {
        if (this.worldCutCooldown > 0) return false;
        CombatSkills.fireWorldCut(this, this.aimAt(target), 2.0f);
        this.worldCutCooldown = 800;
        return true;
    }

    @Override
    protected void counterFinisher(ServerLevel level, LivingEntity target) {
        CombatSkills.fireWorldCut(this, this.aimAt(target), 2.0f);
    }

    @Override
    protected boolean answers(boolean worldCut) {
        return !worldCut;
    }

    /** A great bound toward the foe, landing within reach of its fists. */
    @Override
    protected void gapClose(ServerLevel level, LivingEntity target) {
        Vec3 to = target.position().subtract(this.position());
        Vec3 flat = new Vec3(to.x, 0.0, to.z);
        double dist = flat.length();
        if (dist < 1.0E-3) return;
        double speed = Math.min(2.4, 0.35 + dist * 0.11);
        this.setDeltaMovement(flat.scale(speed / dist).add(0.0, 0.75 + Math.max(0.0, to.y) * 0.05, 0.0));
        this.needsSync = true;
        this.fallDistance = 0.0;
        this.getNavigation().stop();
        Vec3 feet = this.position();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, 18, 0.5, 0.05, 0.5, 0.08);
        level.playSound(null, feet.x, feet.y, feet.z, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.5f);
    }
}
