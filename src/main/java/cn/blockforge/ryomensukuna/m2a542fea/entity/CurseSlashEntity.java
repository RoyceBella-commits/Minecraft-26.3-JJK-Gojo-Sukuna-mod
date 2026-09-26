package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.SukunaDamage;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CurseSlashEntity
extends Entity {
    public static final double SPEED = 3.2;
    public static final double RANGE = 160.0;
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(CurseSlashEntity.class, (EntityDataSerializer)EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(CurseSlashEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(CurseSlashEntity.class, (EntityDataSerializer)EntityDataSerializers.STRING);
    private final Set<UUID> hit = new HashSet<UUID>();
    private LivingEntity owner;
    private Vec3 interpolationTarget = Vec3.ZERO;
    private int interpolationTicks;
    private double traveled;

    public CurseSlashEntity(EntityType<CurseSlashEntity> type, Level world) {
        super(type, world);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static CurseSlashEntity spawn(ServerLevel w, LivingEntity owner, Vec3 dir, Vec3 start, float charge) {
        return CurseSlashEntity.launch(w, owner, dir, start, charge, charge >= 1.5f ? 1 : 0);
    }

    public static CurseSlashEntity launch(ServerLevel w, LivingEntity owner, Vec3 dir, Vec3 start, float charge, int mode) {
        CurseSlashEntity s = new CurseSlashEntity(SukunaMod.CURSE_SLASH, (Level)w);
        s.owner = owner;
        s.entityData.set(CHARGE, Float.valueOf(charge));
        s.entityData.set(MODE, mode);
        s.entityData.set(OWNER, owner.getStringUUID());
        s.snapTo(start.x, start.y, start.z, CurseSlashEntity.yawOf(dir), CurseSlashEntity.pitchOf(dir));
        s.setDeltaMovement(dir.normalize().scale(mode == 2 ? 4.5 : 3.2));
        w.addFreshEntity((Entity)s);
        return s;
    }

    public static float yawOf(Vec3 d) {
        return (float)Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public static float pitchOf(Vec3 d) {
        return (float)Math.toDegrees(Math.asin(Mth.clamp((double)(-d.normalize().y), (double)-1.0, (double)1.0)));
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, Float.valueOf(0.0f));
        builder.define(MODE, 0);
        builder.define(OWNER, "");
    }

    public float getCharge() {
        return ((Float)this.entityData.get(CHARGE)).floatValue();
    }

    public int getMode() {
        return (Integer)this.entityData.get(MODE);
    }

    public float halfSize() {
        return this.getMode() == 2 ? 24.0f + this.getCharge() * 10.0f : 4.0f + this.getCharge() * 3.2f;
    }

    public float getDamage() {
        return this.getMode() == 2 ? SukunaDamage.FINISHER_DAMAGE : 14.0f + this.getCharge() * 10.0f;
    }

    public String getOwnerId() {
        return (String)this.entityData.get(OWNER);
    }

    public Vec3 getTravelDir() {
        return Vec3.directionFromRotation((float)this.getXRot(), (float)this.getYRot());
    }

    public int getPierceLeft() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
        return net.minecraft.world.entity.LinearInterpolationHandler.create(this, 3);
    }

    public void tick() {
        Entity e;
        super.tick();
        if (this.level().isClientSide()) {
            if (this.interpolationTicks > 0) {
                Vec3 p = this.position().lerp(this.interpolationTarget, 1.0 / (double)this.interpolationTicks--);
                this.setPosRaw(p.x, p.y, p.z);
            }
            return;
        }
        if (this.owner == null && !this.getOwnerId().isEmpty() && (e = ((ServerLevel)this.level()).getEntity(UUID.fromString(this.getOwnerId()))) instanceof LivingEntity) {
            LivingEntity l;
            this.owner = l = (LivingEntity)e;
        }
        Vec3 dir = this.getTravelDir();
        double speed = this.getMode() == 2 ? 4.5 : 3.2;
        Vec3 origin = this.position();
        Vec3 right = TerrainCuts.right(dir);
        Vec3 up = dir.cross(right).normalize();
        for (double t = 0.0; t <= speed; t += 0.65) {
            Vec3 c = origin.add(dir.scale(t));
            if (!this.level().hasChunkAt(BlockPos.containing((Position)c))) {
                this.discard();
                return;
            }
            if (this.getMode() == 2) {
                TerrainCuts.plane((ServerLevel)this.level(), c, dir, this.halfSize(), false, 1.4);
                continue;
            }
            TerrainCuts.plane((ServerLevel)this.level(), c, dir, this.halfSize(), this.getMode() == 1, 0.4);
        }
        AABB area = new AABB(origin, origin.add(dir.scale(speed))).inflate((double)(this.halfSize() + 2.0f));
        for (Entity e2 : this.level().getEntities((Entity)this, area)) {
            boolean intersects;
            Player p;
            LivingEntity target;
            if (!(e2 instanceof LivingEntity) || !(target = (LivingEntity)e2).isAlive() || e2 == this.owner || this.getOwnerId().equals(e2.getStringUUID()) || this.hit.contains(e2.getUUID()) || e2 instanceof Player && ((p = (Player)e2).isCreative() || p.isSpectator())) continue;
            Vec3 offset = e2.getBoundingBox().getCenter().subtract(origin);
            double along = offset.dot(dir);
            double a = offset.dot(right);
            double b = offset.dot(up);
            double pad = (double)Math.max(target.getBbWidth(), target.getBbHeight()) * 0.5;
            if (along < -pad || along > speed + pad || Math.abs(a) > (double)this.halfSize() + pad) continue;
            intersects = this.getMode() == 1 ? Math.abs(b) <= (double)this.halfSize() + pad && (CurseSlashEntity.gridDistance(a, this.halfSize()) < 0.4 + pad || CurseSlashEntity.gridDistance(b, this.halfSize()) < 0.4 + pad) : (Math.abs(b) < (this.getMode() == 2 ? 2.0 : 0.4) + pad);
            if (!intersects) continue;
            this.hit.add(e2.getUUID());
            target.setInvulnerableTime(0);
            if (this.getMode() == 2) {
                // World Cut: one clean cut through space; ~1000 to creatures, 60-70 to players.
                Entity src = this.owner != null ? this.owner : this;
                CurseManager.damage(this.level(), src, target, SukunaDamage.finisher(target, this.random), SukunaDamage.worldCut(this.level(), src),
                    src instanceof net.minecraft.server.level.ServerPlayer sp ? cn.blockforge.ryomensukuna.m2a542fea.progression.Progression.latestCast(sp) : -1);
            } else {
                CurseManager.curseDamage(this.level(), (Entity)(this.owner != null ? this.owner : this), target, this.getDamage(), cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category.SLASH);
                SlashFxEntity.spawn(this.level(), e2.getX(), e2.getY(0.5), e2.getZ(), 1, this.getYRot(), this.getXRot(), 2.0f + this.getCharge());
            }
        }
        Vec3 next = origin.add(dir.scale(speed));
        this.setPosRaw(next.x, next.y, next.z);
        this.traveled += speed;
        double d = this.getMode() == 2 ? 256.0 : 160.0;
        if (this.traveled >= d || this.tickCount > 90) {
            this.discard();
        }
    }

    private static double gridDistance(double value, double half) {
        double v = (value + half) % 3.0;
        return Math.min(v, 3.0 - v);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    protected void writeModData(CompoundTag n) {
        n.putFloat("Charge", this.getCharge());
        n.putInt("Mode", this.getMode());
        n.putString("Owner", this.getOwnerId());
        n.putDouble("Traveled", this.traveled);
    }

    protected void readModData(CompoundTag n) {
        this.entityData.set(CHARGE, Float.valueOf(n.getFloatOr("Charge", 0.0f)));
        this.entityData.set(MODE, n.getIntOr("Mode", 0));
        this.entityData.set(OWNER, n.getStringOr("Owner", ""));
        this.traveled = n.getDoubleOr("Traveled", 0.0);
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

