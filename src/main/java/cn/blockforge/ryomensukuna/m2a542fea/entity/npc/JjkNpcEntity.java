package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.combat.BlackFlash;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A spawn-egg sorcerer at full strength (stage V values): fights in melee with Black Flash and
 * uses its techniques now and then (less often than a player would), plus a rare domain.
 */
public abstract class JjkNpcEntity extends PathfinderMob {
    public static final float MAX_HEALTH = 20.0f + StageRules.goldHp(StageRules.MAX_STAGE);
    private static final Identifier FLASH_BOOST = SukunaMod.id("npc_black_flash");
    /** Domain cooldown (60 s from the cast). Nothing ever opens a domain while it runs. */
    private static final int DOMAIN_COOLDOWN = 1200;
    /** A technique is "ready" for range play once the cooldown is this low. */
    private static final int RANGED_READY = 20;
    protected int skillCooldown = 60;
    protected int domainCooldown = 200;
    protected int combatTicks;
    /** Close-quarters window after a gap-closer (blink / bound) against a strong foe. */
    protected int engageTicks;
    private int burstHealCooldown;
    /** Whether this sorcerer fights the current target from range (rolled once per target). */
    private boolean preferRange;
    private LivingEntity rangeRolledFor;

    protected JjkNpcEntity(EntityType<? extends JjkNpcEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.ARMOR, StageRules.armor(StageRules.MAX_STAGE))
            .add(Attributes.ATTACK_DAMAGE, StageRules.unarmedDamage(StageRules.MAX_STAGE))
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.FOLLOW_RANGE, 48.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    /** Whom this sorcerer fights (targets and technique damage both use it). */
    public abstract boolean canHarm(LivingEntity target);

    /** Technique friendly-fire rule: its natural foes, plus whoever it is fighting right now. */
    public boolean mayHurt(LivingEntity target) {
        return target != this && (this.canHarm(target) || target == this.getTarget());
    }

    protected int foesNear(double radius) {
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), this::canHarm).size();
    }

    /** One technique chosen for the current distance; returns false to keep fighting hand to hand. */
    protected abstract boolean useSkill(ServerLevel level, LivingEntity target, double distance);

    /** Opens this sorcerer's domain now; returns false if it could not. */
    protected abstract boolean castDomain(ServerLevel level);

    /** Whether this sorcerer's own domain is currently open. */
    protected abstract boolean domainActive();

    /** Closes in on a strong foe between ranged techniques (Gojo blinks, Sukuna bounds). */
    protected abstract void gapClose(ServerLevel level, LivingEntity target);

    /** Gojo / Sukuna sorcerers: the technique NPCs and players on either route. */
    protected static boolean sorcerer(LivingEntity e) {
        if (e instanceof JjkNpcEntity) return true;
        return e instanceof ServerPlayer sp && CurseManager.of(sp).route() != StageRules.NONE;
    }

    protected static boolean bossLike(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || e instanceof net.minecraft.world.entity.boss.wither.WitherBoss
            || e instanceof net.minecraft.world.entity.monster.warden.Warden || e instanceof net.minecraft.world.entity.monster.ElderGuardian
            || e.getType().builtInRegistryHolder().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags.BOSSES);
    }

    /** Ranged techniques are about ready and this foe is fought from a distance. */
    protected boolean holdingRange() {
        return this.preferRange && this.engageTicks <= 0 && this.skillCooldown <= RANGED_READY && this.getTarget() != null;
    }

    /** An enemy Gojo / Sukuna has opened a domain around (or right next to) this sorcerer. */
    private boolean enemyDomainNearby(ServerLevel level) {
        for (cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.Domain d : cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.active()) {
            if (d.owner.equals(this.getUUID()) || d.entity.level() != level) continue;
            if (!(level.getEntity(d.owner) instanceof LivingEntity owner) || !sorcerer(owner)) continue;
            boolean hostile = this.canHarm(owner) || owner instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this;
            if (hostile && this.position().distanceTo(d.center) < d.radius + 10.0) return true;
        }
        return false;
    }

    private boolean bossNear(double radius) {
        return !this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), e -> e.isAlive() && bossLike(e)).isEmpty();
    }

    /**
     * Domain decision, only ever when the cooldown has run out. Must try: an enemy sorcerer opened a
     * domain here, or health is down to a third. More eager: over 20 foes inside the domain range, a
     * boss, or a sorcerer as the target. Otherwise: a strong target or a crowd, after 10 s of fighting.
     */
    private boolean wantsDomain(ServerLevel level, LivingEntity target, double distance) {
        if (this.domainCooldown > 0 || this.domainActive()) return false;
        if (this.enemyDomainNearby(level)) return true;
        if (target == null) return false;
        if (this.getHealth() <= this.getMaxHealth() / 3.0f) return true;
        double range = StageRules.DOMAIN_RADIUS;
        boolean eager = this.foesNear(range) > 20 || this.bossNear(range) || sorcerer(target);
        if (eager && this.combatTicks > 40 && distance <= range) return true;
        return this.combatTicks > 200 && distance <= 25.0 && this.hasLineOfSight(target) && (strong(target) || this.foesNear(20.0) >= 3);
    }

    protected static boolean sukunaPlayer(LivingEntity e) {
        return e instanceof ServerPlayer sp && CurseManager.of(sp).route() == StageRules.SUKUNA;
    }

    protected static boolean ignoredPlayer(LivingEntity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    /** Opponents that deserve the heavy techniques. */
    protected static boolean strong(LivingEntity t) {
        return t instanceof Player || t instanceof JjkNpcEntity || t.getMaxHealth() >= 60.0f;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new KeepDistanceGoal());
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() {
                return !JjkNpcEntity.this.holdingRange() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !JjkNpcEntity.this.holdingRange() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.canHarm(target)));
    }

    protected Vec3 aimAt(LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(this.getEyePosition());
        return d.lengthSqr() < 1.0E-4 ? this.getViewVector(1.0f) : d.normalize();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) return;
        if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0f);
        }
        if (this.burstHealCooldown > 0) --this.burstHealCooldown;
        if (this.getHealth() < this.getMaxHealth() * 0.3f && this.burstHealCooldown <= 0) {
            this.burstHealCooldown = 400;
            this.heal(this.getMaxHealth() * 0.4f);
            CurseFx.particles(level, ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SukunaSounds.RCT_HEAL, SoundSource.HOSTILE, 1.0f, 0.9f);
        }
        if (VoidDomainEntity.stunned(this)) return;
        if (this.skillCooldown > 0) --this.skillCooldown;
        if (this.domainCooldown > 0) --this.domainCooldown;
        if (this.engageTicks > 0) --this.engageTicks;
        LivingEntity target = this.getTarget();
        boolean fighting = target != null && target.isAlive() && target.level() == this.level();
        if (!fighting) {
            this.combatTicks = 0;
            target = null;
        } else {
            ++this.combatTicks;
            if (target != this.rangeRolledFor) {
                // Not every foe is fought from range: strong ones usually are, others seldom.
                this.rangeRolledFor = target;
                this.preferRange = this.random.nextFloat() < (strong(target) ? 0.7f : 0.25f);
            }
        }
        double distance = fighting ? this.distanceTo(target) : 0.0;
        if (this.wantsDomain(level, target, distance) && this.castDomain(level)) {
            this.domainCooldown = DOMAIN_COOLDOWN;
            this.skillCooldown = Math.max(this.skillCooldown, 80);
            return;
        }
        if (!fighting) return;
        if (this.skillCooldown <= 0 && this.hasLineOfSight(target)) {
            this.getLookControl().setLookAt(target, 60.0f, 60.0f);
            boolean used = this.useSkill(level, target, distance);
            // Techniques come every 5-8 s: noticeably rarer than a player could cast them.
            this.skillCooldown = used ? 100 + this.random.nextInt(60) : 30;
            // Against a strong foe, a blink / bound and a flurry of blows between ranged techniques.
            if (used && strong(target) && this.engageTicks <= 0 && distance > 4.0 && this.random.nextFloat() < 0.6f) {
                this.gapClose(level, target);
                this.engageTicks = 50;
            }
        }
    }

    /** Keeps 10-18 blocks from a foe fought at range while a technique is ready; closes in if too far. */
    private final class KeepDistanceGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private KeepDistanceGoal() {
            this.setFlags(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return JjkNpcEntity.this.holdingRange() && !VoidDomainEntity.stunned(JjkNpcEntity.this);
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = JjkNpcEntity.this.getTarget();
            if (target == null) return;
            JjkNpcEntity self = JjkNpcEntity.this;
            self.getLookControl().setLookAt(target, 30.0f, 30.0f);
            double d = self.distanceTo(target);
            if (d < 9.0) {
                if (self.getNavigation().isDone()) {
                    Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(self, 16, 7, target.position());
                    if (away != null) self.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                }
            } else if (d > 18.0) {
                self.getNavigation().moveTo(target, 1.1);
            } else {
                self.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            JjkNpcEntity.this.getNavigation().stop();
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (VoidDomainEntity.stunned(this) || !(target instanceof LivingEntity living)) {
            return false;
        }
        float mult = BlackFlash.npcStrike(this, living);
        AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (mult > 1.0f && attack != null) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(FLASH_BOOST, mult - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        try {
            return super.doHurtTarget(level, target);
        } finally {
            if (attack != null) attack.removeModifier(FLASH_BOOST);
        }
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        float before = this.getHealth();
        super.actuallyHurt(level, source, amount);
        DomainClash.recordDamage(this, before - this.getHealth());
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean causedByPlayer) {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean allowDrops) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }
}
