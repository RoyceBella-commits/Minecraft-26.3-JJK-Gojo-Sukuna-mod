package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.CurseSlashEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RedBlastEntity
extends LargeFireball {
    private static final EntityDataAccessor<String> OWNER_ID = SynchedEntityData.defineId(RedBlastEntity.class, (EntityDataSerializer)EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(RedBlastEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EXTRA_DAMAGE = SynchedEntityData.defineId(RedBlastEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(RedBlastEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private boolean exploded;
    private int lifeTicks;

    public RedBlastEntity(EntityType<? extends RedBlastEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public static RedBlastEntity create(ServerLevel world, LivingEntity owner, Vec3 dir, float charge, double speed) {
        RedBlastEntity ball = new RedBlastEntity(SukunaMod.RED_BLAST, (Level)world);
        Vec3 n = dir.normalize();
        ball.setOwner((Entity)owner);
        ball.entityData.set(OWNER_ID, owner.getUUID().toString());
        ball.entityData.set(POWER, Float.valueOf(2.5f + 3.5f * charge));
        ball.entityData.set(EXTRA_DAMAGE, Float.valueOf(8.0f + 10.0f * charge));
        ball.entityData.set(CHARGE, Float.valueOf(charge));
        Vec3 start = owner.getEyePosition().add(n.scale(0.8));
        ball.setPosRaw(start.x, start.y, start.z);
        ball.snapTo(start.x, start.y, start.z, CurseSlashEntity.yawOf(n), CurseSlashEntity.pitchOf(n));
        ball.setDeltaMovement(n.scale(speed));
        ball.accelerationPower = 0.0;
        world.addFreshEntity((Entity)ball);
        return ball;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ID, "");
        builder.define(POWER, Float.valueOf(2.5f));
        builder.define(EXTRA_DAMAGE, Float.valueOf(8.0f));
        builder.define(CHARGE, Float.valueOf(0.0f));
    }

    public float getPower() {
        return ((Float)this.entityData.get(POWER)).floatValue();
    }

    public float getExtraDamage() {
        return ((Float)this.entityData.get(EXTRA_DAMAGE)).floatValue();
    }

    public float getCharge() {
        return ((Float)this.entityData.get(CHARGE)).floatValue();
    }

    protected float getInertia() {
        return 1.0f;
    }

    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (this.level().isClientSide()) {
            return;
        }
        if (this.isInWater() || this.level().getBlockState(this.blockPosition()).is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)) {
            this.detonate();
            return;
        }
        Level level = this.level();
        if (level instanceof ServerLevel) {
            ServerLevel sw = (ServerLevel)level;
            sw.sendParticles((ParticleOptions)ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 7, 0.22, 0.22, 0.22, 0.035);
            sw.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(), 3, 0.12, 0.12, 0.12, 0.02);
            if ((this.tickCount & 3) == 0) {
                sw.sendParticles((ParticleOptions)ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.16, 0.16, 0.16, 0.01);
            }
        }
        if (++this.lifeTicks > 120) {
            this.detonate();
            return;
        }
    }

    protected void onHit(HitResult hitResult) {
        if (this.level().isClientSide() || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        Vec3 hit = hitResult.getLocation();
        this.setPosRaw(hit.x, hit.y, hit.z);
        this.detonate();
    }

    protected void onHitEntity(EntityHitResult entityHitResult) {
        if (this.level().isClientSide()) {
            return;
        }
        this.detonate();
    }

    private void detonate() {
        if (this.exploded) {
            return;
        }
        this.exploded = true;
        Level world = this.level();
        if (!(world instanceof ServerLevel)) {
            this.discard();
            return;
        }
        ServerLevel serverWorld = (ServerLevel)world;
        Entity source = this.getOwner() != null ? this.getOwner() : this;
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        double radius = 6.0f + this.getCharge() * 4.0f;
        TerrainCuts.burst(serverWorld, this.position(), radius, true);
        SlashFxEntity.spawn(world, x, y, z, 4, 0.0f, 0.0f, (float)radius);
        SlashFxEntity.spawn(world, x, y + 0.1, z, 7, 0.0f, 90.0f, (float)radius * 1.8f);
        this.level().playSound(null, x, y, z, SukunaSounds.RED_BOOM, SoundSource.MASTER, 1.0f, 1.0f);
        AABB area = this.getBoundingBox().inflate(radius * 1.5);
        List<Entity> victims = this.level().getEntities((Entity)this, area, e -> {
            LivingEntity lv;
            return e instanceof LivingEntity && !(lv = (LivingEntity)e).isRemoved();
        });
        for (Entity e2 : victims) {
            LivingEntity owner;
            LivingEntity target;
            if (!(e2 instanceof LivingEntity) || (target = (LivingEntity)e2).distanceToSqr(x, y, z) > radius * radius * 2.25 || source instanceof LivingEntity && (owner = (LivingEntity)source) == target) continue;
            if (target instanceof Player) {
                Player p = (Player)target;
                if (p.getAbilities().instabuild || p.isSpectator()) continue;
            }
            boolean alive = target.isAlive();
            CurseManager.curseDamage(world, (Entity)source, target, 35.0f + this.getCharge() * 35.0f, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category.FLAME);
            Vec3 impulse = target.position().subtract(this.position()).normalize().scale(2.0);
            target.push(impulse.x, 1.2, impulse.z);
            target.needsSync = true;
            if (!alive) continue;
            target.igniteForSeconds(15);
        }
        this.discard();
    }

    public void writeModData(CompoundTag nbt) {
        nbt.putString("OwnerUuid", (String)this.entityData.get(OWNER_ID));
        nbt.putFloat("Power", this.getPower());
        nbt.putFloat("ExtraDamage", this.getExtraDamage());
        nbt.putFloat("Charge", this.getCharge());
    }

    public void readModData(CompoundTag nbt) {
        if (nbt.contains("OwnerUuid")) {
            this.entityData.set(OWNER_ID, nbt.getStringOr("OwnerUuid", ""));
        }
        if (nbt.contains("Power")) {
            this.entityData.set(POWER, Float.valueOf(nbt.getFloatOr("Power", 0.0f)));
        }
        if (nbt.contains("ExtraDamage")) {
            this.entityData.set(EXTRA_DAMAGE, Float.valueOf(nbt.getFloatOr("ExtraDamage", 0.0f)));
        }
        if (nbt.contains("Charge")) {
            this.entityData.set(CHARGE, Float.valueOf(nbt.getFloatOr("Charge", 0.0f)));
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
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
}

