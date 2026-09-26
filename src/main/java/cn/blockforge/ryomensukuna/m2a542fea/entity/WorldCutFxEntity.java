package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class WorldCutFxEntity
extends Entity {
    public static final int INITIAL_LIFE = 60;
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(WorldCutFxEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private int life = 60;

    public WorldCutFxEntity(EntityType<WorldCutFxEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPermanentlyInvulnerable(true);
    }

    public static WorldCutFxEntity spawn(Level world, double x, double y, double z, float scale, float yaw, float pitch) {
        WorldCutFxEntity fx = new WorldCutFxEntity(SukunaMod.WORLD_CUT_FX, world);
        fx.entityData.set(SCALE, Float.valueOf(scale));
        fx.snapTo(x, y, z, yaw, pitch);
        world.addFreshEntity((Entity)fx);
        return fx;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, Float.valueOf(10.0f));
    }

    public float getScale() {
        return ((Float)this.entityData.get(SCALE)).floatValue();
    }

    public int getLife() {
        return this.life;
    }

    public float getLifeProgress() {
        float p = (float)this.life / 60.0f;
        return Math.max(0.0f, Math.min(1.0f, p));
    }

    public void tick() {
        super.tick();
        --this.life;
        if (!(this.level().isClientSide() || this.life > 0 && this.tickCount <= 70)) {
            this.discard();
        }
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void writeModData(CompoundTag nbt) {
        nbt.putFloat("Scale", this.getScale());
        nbt.putInt("Life", this.life);
    }

    public void readModData(CompoundTag nbt) {
        if (nbt.contains("Scale")) {
            this.entityData.set(SCALE, Float.valueOf(nbt.getFloatOr("Scale", 0.0f)));
        }
        this.life = nbt.contains("Life") ? nbt.getIntOr("Life", 0) : 60;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        
        CompoundTag tag = new CompoundTag(); writeModData(tag);
        out.store("SukunaData", CompoundTag.CODEC, tag);
    }
    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        
        readModData(in.read("SukunaData", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
}

