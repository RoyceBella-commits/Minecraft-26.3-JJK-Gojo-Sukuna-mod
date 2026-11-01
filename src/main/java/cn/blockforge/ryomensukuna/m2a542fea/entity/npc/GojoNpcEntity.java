package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.combat.DuelRules;
import cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MurasakiEntity;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Gojo Satoru (spawn egg): hunts hostile creatures, Sukuna-route players, the Sukuna NPC and
 * Mahoraga. Infinity is always up (no harmful effect takes hold while it is unbroken); uses Ao, Aka,
 * Hollow Purple and Unlimited Void, and blinks for every move the fight calls for. An Ao that drags
 * the foe in is often chased with an Aka; a blink in close sometimes ends in a point-blank Aka.
 */
public class GojoNpcEntity extends JjkNpcEntity {
    private int murasakiCooldown = 300;
    /** Techniques for {@link #pickSkill}, and the follow-ups. */
    private static final int AKA = 0, AO = 1, ORBIT = 2, FISTS = 3;
    private static final int FOLLOW_AKA = 1, FOLLOW_POINT_BLANK = 2;
    private static final double AKA_SPEED = 2.2;
    private static final double PURPLE_SPEED = 3.0;
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
        boolean close = distance < 7.0;
        boolean open = this.targetOpen(target);
        // Crowded or pressed hard: an Ao circling close around itself, held about 5 s.
        float orbit = AoEntity.of(this) == null && (this.foesNear(6.0) >= 2 || distance < 4.0) ? 0.5f : 0.0f;
        float aka = (close ? 0.35f : 0.6f) + (open ? 0.3f : 0.0f);
        float ao = close ? 0.35f : 0.3f;
        float fists = close ? 0.25f : 0.0f;
        int skill = this.pickSkill(aka, ao, orbit, fists);
        switch (skill) {
            case AKA -> {
                GojoSkills.fireAka(this, this.leadAim(target, AKA_SPEED), close ? 1.0f : 1.5f, -1);
                this.recoverMedium();
            }
            case AO -> {
                // Drags the foe in; often an Aka follows while it is still caught.
                GojoSkills.fireAoAt(this, target.getBoundingBox().getCenter(), close ? 1.0f : 1.5f, -1);
                this.recoverLight();
                if (this.random.nextFloat() < 0.6f) this.queueFollowUp(FOLLOW_AKA, 12 + this.random.nextInt(9));
            }
            case ORBIT -> {
                AoEntity.summonOrbiting(this, 1.5f, -1);
                this.orbitTicks = 100;
                this.recoverLight();
            }
            default -> {
                this.used(FISTS);
                return false;
            }
        }
        this.used(skill);
        return true;
    }

    @Override
    protected boolean followUp(ServerLevel level, LivingEntity target, int kind, double distance) {
        if (kind == FOLLOW_AKA && distance < 40.0 || kind == FOLLOW_POINT_BLANK && distance < 5.0) {
            GojoSkills.fireAka(this, this.leadAim(target, AKA_SPEED), 1.0f, -1);
            return true;
        }
        return false;
    }

    /** A blink in close: now and then a point-blank Aka a moment later. */
    @Override
    protected void onGapClosed(ServerLevel level, LivingEntity target) {
        if (this.random.nextFloat() < 0.3f) this.queueFollowUp(FOLLOW_POINT_BLANK, 14 + this.random.nextInt(10));
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
        // Against another NPC it is half a finisher, not a second one: it comes round far less often.
        boolean npcFoe = !(target instanceof net.minecraft.world.entity.player.Player) && !(target instanceof MahoragaEntity);
        this.comboCooldown = npcFoe ? DuelRules.FUSED_COOLDOWN : COMBO_COOLDOWN;
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
            this.moved();
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
    protected boolean tryFinisher(ServerLevel level, LivingEntity target) {
        if (this.murasakiCooldown > 0) return false;
        GojoSkills.fireMurasaki(this, this.leadAim(target, PURPLE_SPEED), -1);
        this.murasakiCooldown = DuelRules.FINISHER_COOLDOWN;
        return true;
    }

    @Override
    protected int finisherWait() {
        return this.murasakiCooldown;
    }

    @Override
    protected void counterFinisher(ServerLevel level, LivingEntity target) {
        GojoSkills.fireMurasaki(this, this.aimAt(target), -1);
        this.murasakiCooldown = DuelRules.FINISHER_COOLDOWN;
    }

    /** Behind unbroken Infinity no poison, wither, slowness or other harmful vanilla effect takes hold. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && Infinity.protects(this) && !Infinity.breached(this)
                && effect.getEffect().unwrapKey().map(k -> "minecraft".equals(k.identifier().getNamespace())).orElse(false)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    protected boolean answers(boolean worldCut) {
        return worldCut;
    }

    /** Blink: straight to the spot (anywhere, even mid-air or out of a pit), facing the foe. */
    @Override
    protected boolean dashTo(ServerLevel level, Vec3 aim, LivingEntity facing, boolean low) {
        Vec3 dir = aim.subtract(this.position());
        dir = dir.lengthSqr() < 1.0E-4 ? this.getViewVector(1.0f) : dir.normalize();
        Vec3 spot = this.landing(aim, dir);
        if (spot == null || spot.distanceToSqr(this.position()) < 1.0) return false;
        Vec3 from = this.position();
        CurseFx.particles(level, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.7, 0.3, 0.1);
        this.teleportTo(spot.x, spot.y, spot.z);
        this.getNavigation().stop();
        this.fallDistance = 0.0;
        CurseFx.particles(level, ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 20, 0.4, 0.7, 0.4, 0.08);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 1.7f);
        if (facing != null) this.getLookControl().setLookAt(facing, 90.0f, 90.0f);
        return true;
    }
}
