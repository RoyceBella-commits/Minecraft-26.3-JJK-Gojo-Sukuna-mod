package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class SlashFxEntity
extends Entity {
    public static final int INITIAL_LIFE = 14;
    /** Crimson slash cut into the seam where Malevolent Shrine presses on Unlimited Void. */
    public static final int CLASH = 9;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(SlashFxEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SlashFxEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private int life = 14;

    public SlashFxEntity(EntityType<SlashFxEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPermanentlyInvulnerable(true);
        this.tickCount = 0;
    }

    public static SlashFxEntity spawn(Level world, double x, double y, double z, int mode, float yaw, float pitch, float scale) {
        SlashFxEntity fx = new SlashFxEntity(SukunaMod.SLASH_FX, world);
        fx.entityData.set(MODE, mode);
        fx.entityData.set(SCALE, Float.valueOf(scale));
        fx.setPosRaw(x, y, z);
        fx.snapTo(x, y, z, yaw, pitch);
        world.addFreshEntity((Entity)fx);
        return fx;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, 0);
        builder.define(SCALE, Float.valueOf(1.0f));
    }

    public int getMode() {
        return (Integer)this.entityData.get(MODE);
    }

    public float getScale() {
        return ((Float)this.entityData.get(SCALE)).floatValue();
    }

    public int duration() {
        return this.getMode() == 4 ? 100 : (this.getMode() == 5 ? 60 : (this.getMode() == 8 ? 30 : 14));
    }

    public int getLife() {
        return this.life;
    }

    public float getLifeProgress() {
        float p = (float)this.life / 14.0f;
        return Math.max(0.0f, Math.min(1.0f, p));
    }

    public void tick() {
        super.tick();
        this.life = Math.max(0, this.duration() - this.tickCount);
        if (!this.level().isClientSide() && this.getMode() == 4 && this.tickCount % 8 == 0) {
            block0: for (int i = 0; i < 16; ++i) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double r = this.random.nextDouble() * (double)this.getScale();
                int x = Mth.floor((double)(this.getX() + Math.cos(angle) * r));
                int z = Mth.floor((double)(this.getZ() + Math.sin(angle) * r));
                int y = (int)this.getY() + 2;
                while ((double)y > this.getY() - (double)this.getScale() - 3.0) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!this.level().hasChunkAt(p)) continue block0;
                    if (this.level().getBlockState(p).isRedstoneConductor((BlockGetter)this.level(), p)) {
                        if (!this.level().getBlockState(p.above()).isAir()) continue block0;
                        this.level().setBlock(p.above(), Blocks.FIRE.defaultBlockState(), 3);
                        continue block0;
                    }
                    --y;
                }
            }
        }
        if (!this.level().isClientSide() && this.tickCount >= this.duration()) {
            this.discard();
        }
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void writeModData(CompoundTag nbt) {
        nbt.putInt("Mode", this.getMode());
        nbt.putFloat("Scale", this.getScale());
        nbt.putInt("Life", this.life);
    }

    public void readModData(CompoundTag nbt) {
        this.entityData.set(MODE, nbt.getIntOr("Mode", 0));
        this.entityData.set(SCALE, Float.valueOf(nbt.getFloatOr("Scale", 0.0f)));
        this.life = nbt.getIntOr("Life", 0);
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

