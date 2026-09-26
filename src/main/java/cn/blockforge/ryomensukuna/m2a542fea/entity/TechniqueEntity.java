package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived technique body (Ao, Aka, Murasaki, Unlimited Void). Not persisted: anything
 * reloaded from disk removes itself, so no orphaned techniques survive a restart.
 */
public abstract class TechniqueEntity extends Entity {
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(TechniqueEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(TechniqueEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(TechniqueEntity.class, EntityDataSerializers.STRING);
    protected UUID ownerId;
    protected int castId;
    protected int life;
    private boolean fresh;

    protected TechniqueEntity(EntityType<? extends TechniqueEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, 0.0f);
        builder.define(SIZE, 1.0f);
        builder.define(OWNER, "");
    }

    public void setup(LivingEntity owner, float charge, int castId) {
        this.ownerId = owner.getUUID();
        this.castId = castId;
        this.fresh = true;
        this.entityData.set(CHARGE, charge);
        this.entityData.set(OWNER, owner.getStringUUID());
    }

    /** Owner UUID string, available on both sides. */
    public String ownerString() {
        return this.entityData.get(OWNER);
    }

    public float charge() {
        return this.entityData.get(CHARGE);
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    protected void setSize(float size) {
        this.entityData.set(SIZE, size);
    }

    public int age() {
        return this.life;
    }

    /** The living caster (player or technique NPC) in this level, or null when gone. */
    public LivingEntity owner() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(this.ownerId);
        return e instanceof LivingEntity l && l.isAlive() && !l.isRemoved() && l.level() == this.level() ? l : null;
    }

    /** The caster when it is a player (progression, energy, HUD), else null. */
    public ServerPlayer playerOwner() {
        return this.owner() instanceof ServerPlayer sp ? sp : null;
    }

    public UUID ownerUuid() {
        return this.ownerId;
    }

    /** Whether a technique may affect this entity at all (owner, allies, creative players excluded). */
    protected boolean validTarget(LivingEntity e, LivingEntity owner) {
        if (e == null || !e.isAlive() || e.isRemoved() || e.isInvulnerable()) return false;
        if (owner != null && (e == owner || CurseManager.protectedTarget(owner, e))) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return true;
    }

    protected boolean lineOfSight(Vec3 from, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        return this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            ++this.life;
            return;
        }
        if (!this.fresh) {
            this.discard();
            return;
        }
        ++this.life;
        this.tickServer((ServerLevel)this.level());
    }

    protected abstract void tickServer(ServerLevel level);

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
