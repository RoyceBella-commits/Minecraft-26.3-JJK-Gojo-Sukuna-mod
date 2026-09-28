package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.Adaptation;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaAdaptation;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MahoragaEntity
extends PathfinderMob {
    public static final int ATTACK_TICKS = 24;
    private static final int HIT_TICK = 13;
    private static final EntityDataAccessor<Integer> ATTACK_KIND = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ATTACK_START = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ADAPTATION_STEPS = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ADAPTATION_TIME = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    private LivingEntity attackTarget;
    private int combo;
    private boolean hitApplied;
    private static final EntityDataAccessor<Long> EMERGE_START = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> AIR_STATE = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> AIR_START = SynchedEntityData.defineId(MahoragaEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    private int leapCooldown;
    private int followRefreshCooldown;
    private double lastFollowX;
    private double lastFollowY;
    private double lastFollowZ;
    private boolean leaping;
    private double leapStartY;
    private static final double FOLLOW_START_SQ = 64.0;
    private static final double FOLLOW_STOP_SQ = 25.0;
    private static final double ACQUIRE_RADIUS_SQ = 576.0;
    private static final double TARGET_LOSE_SQ = 1024.0;
    private static final long OWNER_HURT_MEMORY = 140L;
    private static final int SELF_HURT_MEMORY = 200;
    private String ownerUuid = "";
    private LivingEntity owner;
    private int ownerRefreshCooldown;
    private final MahoragaAdaptation adaptation = new MahoragaAdaptation(this);
    /** Each blow Infinity stops turns the wheel; the third one breaks through for 5 s (repeatable). */
    public void onInfinityHit(int count, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Result result) {
        if (result == cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Result.OPEN) {
            return;
        }
        this.turnAdaptationWheel();
        this.playSound(cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds.MAHORAGA_ADAPT, 1.0f, 1.0f);
        boolean broke = result == cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Result.BROKE;
        this.adaptationMessage(net.minecraft.network.chat.Component.translatable(broke ? "sukuna.hint.mahoraga_infinity_done" : "sukuna.hint.mahoraga_infinity", count));
    }
    private LivingEntity lastAttacker;
    private int lastAttackerAge;
    private int life;

    public float airTicks(float delta) {
        return Math.max(0.0f, (float)(this.level().getGameTime() - (Long)this.entityData.get(AIR_START)) + delta);
    }

    private void airState(int state) {
        if (this.airState() != state) {
            this.entityData.set(AIR_STATE, state);
            this.entityData.set(AIR_START, this.level().getGameTime());
        }
    }

    public void beginEmergence() {
        this.entityData.set(EMERGE_START, this.level().getGameTime());
        this.setNoAi(true);
    }

    public float emergence(float delta) {
        long t = (Long)this.entityData.get(EMERGE_START);
        return t < 0L ? 60.0f : Math.max(0.0f, (float)(this.level().getGameTime() - t) + delta);
    }

    public int airState() {
        return (Integer)this.entityData.get(AIR_STATE);
    }

    private void tickLeap() {
        Vec3 horizontal;
        boolean needsHeight;
        if (this.leapCooldown > 0) {
            --this.leapCooldown;
        }
        LivingEntity target = this.getTarget();
        if (this.leaping) {
            this.airState(this.getDeltaMovement().y > 0.0 ? 1 : 2);
            if (this.getY() > this.leapStartY + 20.0 && this.getDeltaMovement().y > 0.0) {
                this.setDeltaMovement(this.getDeltaMovement().x, 0.0, this.getDeltaMovement().z);
            }
            if (this.onGround()) {
                this.leaping = false;
                this.airState(3);
                this.leapCooldown = 100;
                Level level = this.level();
                if (level instanceof ServerLevel) {
                    ServerLevel sw = (ServerLevel)level;
                    sw.sendParticles((ParticleOptions)ParticleTypes.CLOUD, this.getX(), this.getY() + 0.15, this.getZ(), 48, 2.6, 0.18, 2.6, 0.12);
                    sw.sendParticles((ParticleOptions)ParticleTypes.POOF, this.getX(), this.getY() + 0.2, this.getZ(), 28, 2.0, 0.15, 2.0, 0.08);
                }
                SlashFxEntity.spawn(this.level(), this.getX(), this.getY() + 0.1, this.getZ(), 7, 0.0f, 90.0f, 9.0f);
                this.playSound(SukunaSounds.SLASH2, 1.8f, 0.55f);
                for (Entity e : this.level().getEntities((Entity)this, this.getBoundingBox().inflate(5.0))) {
                    Player p;
                    LivingEntity living;
                    if (!(e instanceof LivingEntity) || !this.validCombatTarget(living = (LivingEntity)e) || e instanceof Player && ((p = (Player)e).isCreative() || p.isSpectator())) continue;
                    CurseManager.curseDamage(this.level(), (Entity)this, living, 24.0f);
                    living.push(0.0, 0.7, 0.0);
                    living.needsSync = true;
                }
            }
            return;
        }
        if (this.airState() == 3 && this.airTicks(0.0f) > 10.0f) {
            this.airState(0);
        }
        if (target == null || !this.onGround() || this.leapCooldown > 0 || this.getAttackKind() != 0 || !this.validCombatTarget(target)) {
            return;
        }
        double horizontalDistance = Math.sqrt(Math.pow(target.getX() - this.getX(), 2.0) + Math.pow(target.getZ() - this.getZ(), 2.0));
        double verticalDistance = target.getY() - this.getY();
        boolean blocked = this.horizontalCollision || this.getNavigation().isDone();
        boolean bl = needsHeight = verticalDistance > 2.2;
        if (!blocked && !needsHeight) {
            return;
        }
        if (horizontalDistance < 3.5 && !needsHeight) {
            return;
        }
        double desired = Math.min(12.0, Math.max(3.0, verticalDistance + 3.0 + horizontalDistance * 0.1));
        double velocity = 0.0;
        double height = 0.0;
        double v = 0.0;
        for (double trial = 0.3; trial < 2.7; trial += 0.02) {
            height = 0.0;
            v = trial;
            for (int i = 0; i < 100 && v > 0.0; ++i) {
                height += v;
                v = (v - 0.08) * 0.98;
            }
            if (!(height >= desired)) continue;
            velocity = trial;
            break;
        }
        if ((horizontal = new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ())).lengthSqr() < 1.0E-4) {
            return;
        }
        horizontal = horizontal.normalize();
        this.setDeltaMovement(horizontal.scale(Math.min(0.62, 0.3 + horizontalDistance * 0.025)).add(0.0, velocity, 0.0));
        this.needsSync = true;
        this.getNavigation().stop();
        this.leaping = true;
        this.leapStartY = this.getY();
        this.airState(1);
        this.leapCooldown = 100;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        if (!this.level().isClientSide() && this.isAlive() && this.calculateFallDamage(distance, multiplier) > 0) {
            this.adaptation.damageExposure(source);
        }
        return false;
    }

    protected void onInsideBlock(BlockState block) {
        super.onInsideBlock(block);
        if (!this.level().isClientSide() && this.isAlive() && this.adaptation != null) {
            if (block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE) || block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE)) {
                this.adaptation.environmentExposure(this.damageSources().inFire());
            }
            if (block.is(Blocks.MAGMA_BLOCK)) {
                this.adaptation.environmentExposure(this.damageSources().hotFloor());
            }
        }
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMERGE_START, -1L);
        builder.define(AIR_STATE, 0);
        builder.define(AIR_START, 0L);
        builder.define(ATTACK_KIND, 0);
        builder.define(ATTACK_START, -1L);
        builder.define(ADAPTATION_STEPS, 0);
        builder.define(ADAPTATION_TIME, -100L);
    }

    public int getAttackKind() {
        return (Integer)this.entityData.get(ATTACK_KIND);
    }

    public boolean isChasing() {
        LivingEntity target = this.getTarget();
        return target != null && this.validCombatTarget(target) && this.distanceToSqr((Entity)target) > 16.0 && this.getAttackKind() == 0 && !this.leaping && this.onGround();
    }

    public float getAttackTicks(float delta) {
        long start = (Long)this.entityData.get(ATTACK_START);
        return start < 0L ? 24.0f : Math.max(0.0f, (float)(this.level().getGameTime() - start) + delta);
    }

    public float getWheelRotation(float delta) {
        float t = Math.min(1.0f, Math.max(0.0f, ((float)(this.level().getGameTime() - (Long)this.entityData.get(ADAPTATION_TIME)) + delta) / 12.0f));
        t = t * t * (3.0f - 2.0f * t);
        int steps = (Integer)this.entityData.get(ADAPTATION_STEPS);
        return ((float)steps - (steps > 0 ? 1.0f - t : 0.0f)) * (float)Math.PI / 4.0f;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        LivingEntity living;
        if (this.level().isClientSide() || !this.isAlive() || this.getAttackKind() != 0 || !(target instanceof LivingEntity) || !this.validCombatTarget(living = (LivingEntity)target)) {
            return false;
        }
        this.attackTarget = living;
        this.hitApplied = false;
        this.entityData.set(ATTACK_START, this.level().getGameTime());
        this.entityData.set(ATTACK_KIND, (1 + this.combo++ % 4));
        this.getNavigation().stop();
        return true;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private boolean validCombatTarget(LivingEntity target) {
        if (target == this) return false;
        if (!target.isAlive()) return false;
        if (target.isRemoved()) return false;
        if (target.getStringUUID().equals(this.ownerUuid)) return false;
        if (!(target instanceof MahoragaEntity)) return true;
        MahoragaEntity other = (MahoragaEntity)target;
        if (this.ownerUuid.isEmpty()) return true;
        if (this.ownerUuid.equals(other.ownerUuid())) return false;
        return true;
    }

    private void tickAttack() {
        int kind = this.getAttackKind();
        if (kind == 0) {
            return;
        }
        if (!this.isAlive()) {
            this.entityData.set(ATTACK_KIND, 0);
            this.attackTarget = null;
            return;
        }
        float ticks = this.getAttackTicks(0.0f);
        this.getNavigation().stop();
        if (this.attackTarget != null && ticks < 13.0f) {
            this.getLookControl().setLookAt((Entity)this.attackTarget, 40.0f, 40.0f);
        }
        if (!this.hitApplied && ticks >= 13.0f) {
            double reach;
            this.hitApplied = true;
            this.playSound(kind == 3 ? SukunaSounds.SLASH2 : SukunaSounds.SLASH1, 1.5f, 0.72f + (float)kind * 0.08f);
            reach = kind == 4 ? 3.8 : (kind == 3 ? 3.5 : 3.0);
            if (kind == 2 || kind == 3) {
                for (Entity candidate : this.level().getEntities((Entity)this, this.getBoundingBox().inflate(reach, 1.5, reach))) {
                    LivingEntity living;
                    if (!(candidate instanceof LivingEntity) || !((living = (LivingEntity)candidate) instanceof Enemy) && living != this.attackTarget || kind == 2 && !this.inFront(living)) continue;
                    this.strike(living, reach, kind);
                }
            } else if (this.attackTarget != null) {
                this.strike(this.attackTarget, reach, kind);
            }
            Vec3 impact = this.position().add(this.getViewVector(1.0f).scale(2.0));
            TerrainCuts.plane((ServerLevel)this.level(), impact.add(0.0, 1.0, 0.0), this.getViewVector(1.0f), 4.5, kind == 2, 0.6);
            if (kind == 3) {
                TerrainCuts.burst((ServerLevel)this.level(), impact, 3.0, false);
            }
            SlashFxEntity.spawn(this.level(), impact.x, impact.y + 1.0, impact.z, 1, this.getYRot(), kind == 2 ? 0.0f : 75.0f, 3.0f);
            if (kind == 3) {
                SlashFxEntity.spawn(this.level(), impact.x, impact.y + 0.1, impact.z, 7, 0.0f, 90.0f, 7.0f);
            }
        }
        if (ticks >= 24.0f) {
            this.entityData.set(ATTACK_KIND, 0);
            this.attackTarget = null;
        }
    }

    private boolean inFront(LivingEntity target) {
        Vec3 offset = target.position().subtract(this.position()).normalize();
        return this.getViewVector(1.0f).dot(offset) > -0.25;
    }

    private void strike(LivingEntity target, double reach, int kind) {
        if (!this.validCombatTarget(target) || this.distanceToSqr((Entity)target) > reach * reach || !this.hasLineOfSight((Entity)target)) {
            return;
        }
        if (super.doHurtTarget((ServerLevel)this.level(), target)) {
            Vec3 direction = target.position().subtract(this.position()).normalize();
            target.push(direction.x * 1.15, kind == 3 ? 0.85 : 0.38, direction.z * 1.15);
            this.setDeltaMovement(direction.x * 0.75, this.getDeltaMovement().y, direction.z * 0.75);
            this.needsSync = true;
            target.needsSync = true;
        }
    }

    public MahoragaEntity(EntityType<MahoragaEntity> type, Level world) {
        super(type, world);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 900.0).add(Attributes.ATTACK_DAMAGE, 28.0).add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.ARMOR, 8.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    public void ownerUuid(String uuid) {
        this.ownerUuid = uuid == null ? "" : uuid;
        this.owner = null;
        this.ownerRefreshCooldown = 0;
    }

    public String ownerUuid() {
        return this.ownerUuid;
    }

    private LivingEntity resolveOwner() {
        if (this.owner != null && (this.owner.isRemoved() || !this.owner.isAlive())) {
            this.owner = null;
        }
        if (this.owner != null || this.level().isClientSide() || this.ownerUuid.isEmpty()) {
            return this.owner;
        }
        if (this.ownerRefreshCooldown > 0) {
            --this.ownerRefreshCooldown;
            return null;
        }
        this.ownerRefreshCooldown = 20;
        try {
            LivingEntity living;
            UUID id = UUID.fromString(this.ownerUuid);
            Entity e = ((ServerLevel)this.level()).getEntity(id);
            if (e instanceof LivingEntity && (living = (LivingEntity)e).isAlive()) {
                this.owner = living;
            }
        }
        catch (IllegalArgumentException illegalArgumentException) {
            // empty catch block
        }
        return this.owner;
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(2, (Goal)new ControlledMeleeAttackGoal());
        this.goalSelector.addGoal(3, (Goal)new FollowOwnerGoal());
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.emergence(0.0f) < 60.0f) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (this.isNoAi()) {
            this.setNoAi(false);
        }
        this.tickLeap();
        if (this.life < Integer.MAX_VALUE) {
            ++this.life;
        }
        if (!this.isAlive()) {
            return;
        }
        this.resolveOwner();
        if (this.tickCount % 20 == 0) {
            this.keepUpWithOwner();
        }
        this.adaptation.tick();
        this.tickAdaptedFlight();
        this.tickAttack();
        if ((this.tickCount & 3) == 0) {
            this.updateTargeting();
        }
    }

    /** Too far from its master (24 blocks idle, 48 blocks always): step through shadow to its side. */
    private void keepUpWithOwner() {
        LivingEntity o = this.owner;
        if (o == null || o.level() != this.level() || this.getAttackKind() != 0) return;
        double d = this.distanceToSqr((Entity)o);
        LivingEntity target = this.getTarget();
        boolean engaged = target != null && target.isAlive() && this.distanceToSqr((Entity)target) < 256.0;
        if (d < 576.0 || d < 2304.0 && engaged) return;
        for (int attempt = 0; attempt < 16; ++attempt) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double r = 2.0 + this.random.nextDouble();
            BlockPos base = BlockPos.containing(o.getX() + Math.cos(angle) * r, o.getY(), o.getZ() + Math.sin(angle) * r);
            for (int dy = 1; dy >= -2; --dy) {
                BlockPos at = base.above(dy);
                Vec3 feet = Vec3.atBottomCenterOf(at);
                if (!this.level().getBlockState(at.below()).isFaceSturdy(this.level(), at.below(), net.minecraft.core.Direction.UP)) continue;
                if (!this.level().noCollision(this, this.getBoundingBox().move(feet.subtract(this.position())))) continue;
                Vec3 from = this.position();
                CurseFx.particles(this.level(), SukunaMod.CURSE_PARTICLE, from.x, from.y + 1.3, from.z, 30, 0.5, 1.0, 0.5, 0.02);
                this.getNavigation().stop();
                this.setTarget(null);
                this.snapTo(feet.x, feet.y, feet.z, this.getYRot(), this.getXRot());
                this.setDeltaMovement(Vec3.ZERO);
                this.fallDistance = 0.0;
                SlashFxEntity.spawn(this.level(), feet.x, feet.y + 0.015, feet.z, 5, 0.0f, 90.0f, 1.8f);
                this.playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.5f);
                return;
            }
        }
    }

    /** Health and adaptation, stored on the master when it is recalled. */
    public CompoundTag saveForRecall() {
        CompoundTag tag = new CompoundTag();
        this.writeModData(tag);
        tag.putFloat("Health", this.getHealth());
        return tag;
    }

    public void restoreFromRecall(CompoundTag tag) {
        String owner = this.ownerUuid;
        this.readModData(tag);
        this.ownerUuid(owner);
    }

    private void updateTargeting() {
        LivingEntity target = this.getTarget();
        LivingEntity o = this.owner;
        if (target != null && (!this.validCombatTarget(target) || target == o || this.distanceToSqr((Entity)target) > 1024.0)) {
            this.setTarget(null);
            target = null;
        }
        if (target != null) {
            return;
        }
        if (this.lastAttacker != null) {
            if (!this.validCombatTarget(this.lastAttacker) || this.lastAttacker == o || this.tickCount - this.lastAttackerAge > 200 || this.distanceToSqr((Entity)this.lastAttacker) > 1024.0) {
                this.lastAttacker = null;
            } else {
                this.setTarget(this.lastAttacker);
                return;
            }
        }
        if (o instanceof net.minecraft.world.entity.Mob master && master.getTarget() != null && this.validCombatTarget(master.getTarget())
            && master.getTarget().distanceToSqr((Entity)this) <= 1024.0) {
            // A Sukuna NPC's shikigami joins its master's fight.
            this.setTarget(master.getTarget());
            return;
        }
        if (o != null) {
            LivingEntity hurtBy = o.getLastHurtByMob();
            long sinceHurt = o.tickCount - o.getLastHurtByMobTimestamp();
            if (hurtBy != null && this.validCombatTarget(hurtBy) && sinceHurt <= 140L && hurtBy.distanceToSqr((Entity)this) <= 1024.0) {
                this.setTarget(hurtBy);
                return;
            }
        }
        AABB box = this.getBoundingBox().inflate(24.0, 8.0, 24.0);
        LivingEntity nearest = null;
        double nearestSq = 577.0;
        for (Entity e : this.level().getEntities((Entity)this, box)) {
            double d;
            LivingEntity living;
            if (!(e instanceof Enemy) || e instanceof cn.blockforge.ryomensukuna.m2a542fea.entity.npc.SukunaNpcEntity || !(e instanceof LivingEntity) || !this.validCombatTarget(living = (LivingEntity)e) || living.isInvulnerable() || !((d = this.distanceToSqr((Entity)living)) < nearestSq)) continue;
            nearestSq = d;
            nearest = living;
        }
        if (nearest != null) {
            this.setTarget(nearest);
        }
    }

    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean accepted;
        boolean nativeFire;
        LivingEntity attacker;
        if (this.level().isClientSide()) {
            return super.hurtServer(level, source, amount);
        }
        if (!this.isAlive() || amount <= 0.0f || !Float.isFinite(amount)) {
            return false;
        }
        LivingEntity o = this.resolveOwner();
        Entity rawAttacker = source.getEntity();
        if (rawAttacker != null && rawAttacker.getStringUUID().equals(this.ownerUuid)) {
            return false;
        }
        if (rawAttacker instanceof LivingEntity && (attacker = (LivingEntity)rawAttacker) != this && attacker != o && attacker.isAlive()) {
            this.lastAttacker = attacker;
            this.lastAttackerAge = this.tickCount;
        }
        boolean bl = nativeFire = source.is(DamageTypeTags.IS_FIRE) && this.fireImmune();
        if (this.isInvulnerableTo(level, source) && !nativeFire) {
            return false;
        }
        boolean bl2 = accepted = this.getInvulnerableTime() <= 10 || amount > this.lastHurt || source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        if (nativeFire) {
            this.adaptation.environmentExposure(source);
        } else if (accepted) {
            this.adaptation.damageExposure(source);
        }
        if (this.adaptation.immune(MahoragaAdaptation.damageId(source), false) || this.adaptation.protectedFrom(source)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        super.actuallyHurt(level, source, this.level().isClientSide() ? amount : this.adaptation.scale(source, amount));
    }

    public boolean addEffect(MobEffectInstance effect, Entity source) {
        if (!this.level().isClientSide() && this.isAlive() && this.adaptation.effect(effect, true)) {
            return false;
        }
        return super.addEffect(effect, source);
    }

    public boolean canBeAffected(MobEffectInstance effect) {
        return (this.adaptation == null || !this.adaptation.immune(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(), true)) && super.canBeAffected(effect);
    }

    public void makeStuckInBlock(BlockState block, Vec3 multiplier) {
        if (this.adaptation != null) {
            if (block.is(Blocks.COBWEB)) {
                if (!this.level().isClientSide()) {
                    this.adaptation.touchWeb();
                }
                if (this.adaptation.immune("minecraft:cobweb", true)) {
                    return;
                }
            }
            if (block.is(Blocks.POWDER_SNOW) && this.adaptation.has(Adaptation.Evolution.FROST)) {
                return;
            }
        }
        super.makeStuckInBlock(block, multiplier);
    }

    protected float getBlockSpeedFactor() {
        BlockState ground = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement());
        if (this.adaptation != null && this.adaptation.has(Adaptation.Evolution.SOUL) && ground.is(Blocks.SOUL_SAND)) {
            return 1.0f;
        }
        return super.getBlockSpeedFactor();
    }

    public boolean canBreatheUnderwater() {
        return this.adaptation != null && this.adaptation.has(Adaptation.Evolution.WATER) || super.canBreatheUnderwater();
    }

    public boolean canFreeze() {
        return (this.adaptation == null || !this.adaptation.has(Adaptation.Evolution.FROST)) && super.canFreeze();
    }

    public boolean canStandOnFluid(FluidState fluid) {
        return this.adaptation != null && (fluid.is(FluidTags.WATER) && this.adaptation.has(Adaptation.Evolution.WATER) || fluid.is(FluidTags.LAVA) && this.adaptation.has(Adaptation.Evolution.LAVA)) || super.canStandOnFluid(fluid);
    }

    public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.adaptation != null && (this.isInWater() && this.adaptation.has(Adaptation.Evolution.WATER) || this.isInLava() && (this.adaptation.has(Adaptation.Evolution.HEAT) || this.adaptation.has(Adaptation.Evolution.LAVA)))) {
            this.moveRelative(0.07f, input);
        }
        super.travel(input);
    }

    private void tickAdaptedFlight() {
        LivingEntity target = this.getTarget();
        if (!this.adaptation.has(Adaptation.Evolution.FLIGHT) || target == null || !this.validCombatTarget(target) || target.getY() < this.getY() + 2.0 || !this.hasLineOfSight((Entity)target) || this.getAttackKind() != 0) {
            return;
        }
        Vec3 direction = target.position().subtract(this.position()).normalize();
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().scale(0.65).add(direction.scale(0.28)).add(0.0, 0.08, 0.0));
        this.needsSync = true;
        this.leaping = true;
        this.leapStartY = this.getY();
        this.airState(1);
    }

    public void turnAdaptationWheel() {
        this.entityData.set(ADAPTATION_STEPS, ((Integer)this.entityData.get(ADAPTATION_STEPS) + 1));
        this.entityData.set(ADAPTATION_TIME, this.level().getGameTime());
    }

    public void adaptationMessage(Component message) {
        LivingEntity livingEntity = this.resolveOwner();
        if (livingEntity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)livingEntity;
            player.sendSystemMessage((Component)Component.literal((String)"\u9b54\u865a\u7f57 \u00b7 ").append(message), true);
        }
    }

    public BlockPos adaptationReturnOrigin() {
        LivingEntity resolved = this.resolveOwner();
        return resolved != null ? resolved.blockPosition() : ((ServerLevel)this.level()).getRespawnData().pos();
    }

    public void writeModData(CompoundTag nbt) {
        nbt.putString("MahoragaOwner", this.ownerUuid);
        nbt.putInt("MahoragaLife", this.life);
        this.adaptation.write(nbt);
        nbt.putInt("MahoragaWheelSteps", ((Integer)this.entityData.get(ADAPTATION_STEPS)).intValue());
    }

    public void readModData(CompoundTag nbt) {
        if (nbt.contains("MahoragaOwner")) {
            this.ownerUuid = nbt.getStringOr("MahoragaOwner", "");
        }
        if (nbt.contains("MahoragaLife")) {
            this.life = nbt.getIntOr("MahoragaLife", 0);
        }
        this.owner = null;
        this.ownerRefreshCooldown = 0;
        this.adaptation.read(nbt);
        if (nbt.contains("Health")) {
            this.setHealth(nbt.getFloatOr("Health", 0.0f));
        }
        this.entityData.set(ADAPTATION_STEPS, Math.max(0, nbt.getIntOr("MahoragaWheelSteps", 0)));
        this.entityData.set(ADAPTATION_TIME, (this.level().getGameTime() - 12L));
    }

    public void die(DamageSource cause) {
        if (!this.level().isClientSide()) {
            this.spawnCurseBurst(40);
            // A destroyed Mahoraga is gone for good: the next summon starts fresh.
            try {
                ServerPlayer master = ((ServerLevel)this.level()).getServer().getPlayerList().getPlayer(UUID.fromString(this.ownerUuid));
                if (master != null) master.removeAttached(cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaSkill.STORE);
            } catch (IllegalArgumentException ignored) {
                // No valid owner.
            }
        }
        super.die(cause);
    }

    private void spawnCurseBurst(int count) {
        if (this.level().isClientSide()) {
            return;
        }
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        for (int i = 0; i < count; ++i) {
            CurseFx.particles(this.level(), SukunaMod.CURSE_PARTICLE, x + (this.random.nextDouble() - 0.5) * 2.4, y + this.random.nextDouble() * 2.6, z + (this.random.nextDouble() - 0.5) * 2.4, 1, (this.random.nextDouble() - 0.5) * 0.04, 0.05 + this.random.nextDouble() * 0.14, (this.random.nextDouble() - 0.5) * 0.04, 0.0);
        }
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean causedByPlayer) {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean allowDrops) {
    }

    public boolean fireImmune() {
        return true;
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    private class ControlledMeleeAttackGoal
    extends MeleeAttackGoal {
        private ControlledMeleeAttackGoal() {
            super((PathfinderMob)MahoragaEntity.this, 5.0, false);
        }

        public boolean canUse() {
            return MahoragaEntity.this.getAttackKind() == 0 && !MahoragaEntity.this.leaping && super.canUse();
        }

        public boolean canContinueToUse() {
            return MahoragaEntity.this.getAttackKind() == 0 && !MahoragaEntity.this.leaping && super.canContinueToUse();
        }
    }

    private class FollowOwnerGoal
    extends Goal {
        private FollowOwnerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public boolean canUse() {
            if (MahoragaEntity.this.getTarget() != null) {
                return false;
            }
            LivingEntity o = MahoragaEntity.this.owner;
            return o != null && o.isAlive() && o.distanceToSqr((Entity)MahoragaEntity.this) > 64.0;
        }

        public boolean canContinueToUse() {
            if (MahoragaEntity.this.getTarget() != null) {
                return false;
            }
            LivingEntity o = MahoragaEntity.this.owner;
            return o != null && o.isAlive() && o.distanceToSqr((Entity)MahoragaEntity.this) > 25.0;
        }

        public void start() {
            MahoragaEntity.this.followRefreshCooldown = 0;
            super.start();
        }

        public void tick() {
            LivingEntity o = MahoragaEntity.this.owner;
            if (o == null) {
                return;
            }
            if (MahoragaEntity.this.followRefreshCooldown > 0) {
                --MahoragaEntity.this.followRefreshCooldown;
            }
            double moved = Math.pow(o.getX() - MahoragaEntity.this.lastFollowX, 2.0) + Math.pow(o.getY() - MahoragaEntity.this.lastFollowY, 2.0) + Math.pow(o.getZ() - MahoragaEntity.this.lastFollowZ, 2.0);
            if (MahoragaEntity.this.followRefreshCooldown == 0 || moved > 1.0 || MahoragaEntity.this.getNavigation().isDone()) {
                MahoragaEntity.this.getNavigation().moveTo((Entity)o, MahoragaEntity.this.isChasing() ? 5.0 : 1.05);
                MahoragaEntity.this.lastFollowX = o.getX();
                MahoragaEntity.this.lastFollowY = o.getY();
                MahoragaEntity.this.lastFollowZ = o.getZ();
                MahoragaEntity.this.followRefreshCooldown = 12;
            }
            MahoragaEntity.this.getLookControl().setLookAt((Entity)o, 30.0f, 30.0f);
        }

        public void stop() {
            MahoragaEntity.this.followRefreshCooldown = 0;
            MahoragaEntity.this.getNavigation().stop();
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        CompoundTag tag = new CompoundTag(); writeModData(tag);
        out.store("SukunaData", CompoundTag.CODEC, tag);
    }
    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        readModData(in.read("SukunaData", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}

