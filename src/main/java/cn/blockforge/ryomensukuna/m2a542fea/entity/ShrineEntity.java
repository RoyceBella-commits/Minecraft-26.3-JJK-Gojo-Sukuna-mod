package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShrineEntity
extends Entity implements cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.Collapsible {
    /** Active expansion after assembly (ticks); set from the caster's stage (20 s, 30 s at stage V). */
    public int activeTicks = cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules.DOMAIN_TICKS;
    public int castId = -1;
    private boolean finished;

    public static final int DURATION = Integer.MAX_VALUE;
    public static final int ASSEMBLY_TICKS = 100;
    private static final EntityDataAccessor<Long> START_TIME = SynchedEntityData.defineId(ShrineEntity.class, (EntityDataSerializer)EntityDataSerializers.LONG);
    /** Same radius as Unlimited Void (about 36 blocks). */
    public static final double RADIUS = cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules.DOMAIN_RADIUS;
    public static final int DOMAIN_CLEAR_HEIGHT = 384;
    private static final EntityTypeTest<Entity, LivingEntity> LIVING = EntityTypeTest.forClass(LivingEntity.class);
    private static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData.defineId(ShrineEntity.class, (EntityDataSerializer)EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ShrineEntity.class, (EntityDataSerializer)EntityDataSerializers.INT);
    public String ownerUuid = "";
    public int life = 0;
    public int duration = Integer.MAX_VALUE;
    public double radius = RADIUS;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean anchored;

    public ShrineEntity(EntityType<ShrineEntity> type, Level world) {
        super(type, world);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public boolean fireImmune() {
        return true;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, "");
        builder.define(LIFE, 0);
        builder.define(START_TIME, -1L);
    }

    public int getLife() {
        return this.level().isClientSide() ? (Integer)this.entityData.get(LIFE) : this.life;
    }

    public float getVisualLife(float tickDelta) {
        long start = (Long)this.entityData.get(START_TIME);
        return start < 0L ? (float)this.getLife() : Math.max(0.0f, (float)(this.level().getGameTime() - start) + tickDelta);
    }

    public String ownerId() {
        return (String)this.entityData.get(OWNER_UUID);
    }

    public void setOwnerUuid(String uuid) {
        String string = this.ownerUuid = uuid == null ? "" : uuid;
        if (this.entityData != null) {
            this.entityData.set(OWNER_UUID, this.ownerUuid);
        }
    }

    public Entity getOwnerEntity() {
        if (this.ownerUuid == null || this.ownerUuid.isEmpty()) {
            return null;
        }
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        ServerLevel sw = (ServerLevel)level;
        try {
            return sw.getEntity(UUID.fromString(this.ownerUuid));
        }
        catch (IllegalArgumentException bad) {
            return null;
        }
    }

    private boolean isOwner(Entity e) {
        return e != null && this.ownerUuid.equals(e.getStringUUID());
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public boolean immuneToDamageLegacy(DamageSource source, float amount) {
        return false;
    }

    public void tick() {
        LivingEntity owner;
        this.baseTick();
        if (this.level().isClientSide()) {
            return;
        }
        if (!this.anchored) {
            this.anchorX = this.getX();
            this.anchorY = this.getY();
            this.anchorZ = this.getZ();
            this.anchored = true;
            this.entityData.set(START_TIME, (this.level().getGameTime() - (long)this.life));
            this.entityData.set(OWNER_UUID, this.ownerUuid);
        }
        ++this.life;
        this.entityData.set(LIFE, this.life);
        if (this.life > ASSEMBLY_TICKS + this.activeTicks) {
            this.collapse("sukuna.hint.domain_end");
            return;
        }
        this.setPosRaw(this.anchorX, this.anchorY, this.anchorZ);
        this.setBoundingBox(this.makeBoundingBox());
        if (this.life % 20 == 0 && this.getOwnerEntity() instanceof ServerPlayer caster) {
            var d = cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.of(this);
            if (d != null) {
                cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.actionBar(caster, "sukuna.hint.shrine_stability",
                    Math.round(d.stability() * 100.0f), Math.max(0, this.remainingTicks()) / 20);
            }
        }
        Entity ownerEntity = this.getOwnerEntity();
        if (!(ownerEntity instanceof LivingEntity) || !(owner = (LivingEntity)ownerEntity).isAlive() || owner.isRemoved() || owner.level() != this.level()
            || owner instanceof ServerPlayer sp && !CurseManager.spend(sp, 0.4f)) {
            this.finishDomain();
            return;
        }
        if (this.life >= 100 && this.life % 4 == 0) {
            AABB box = this.getBoundingBox().inflate(this.radius);
            List<LivingEntity> targets = this.level().getEntities(LIVING, box, this::isValidTarget);
            for (LivingEntity target : targets) {
                // In a clash the rival caster is untouched; everyone else suffers both domains.
                if (CurseManager.protectedTarget(owner, target) || target.getUUID().equals(cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.clashRival(this))) continue;
                CurseManager.damage(this.level(), owner, target, 5.0f, cn.blockforge.ryomensukuna.m2a542fea.skill.SukunaDamage.sureHit(this.level(), owner), this.castId, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category.SLASH);
                this.spawnRandomSlashFx(target);
                this.level().playSound(null, target.getX(), target.getY() + 0.8, target.getZ(), SukunaSounds.DOMAIN_SLICE, SoundSource.PLAYERS, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
            }
        }
        if (this.life >= 100) {
            for (cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.Domain d : new java.util.ArrayList<>(cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.active())) {
                if (d.entity instanceof VoidDomainEntity shell && !d.owner.equals(owner.getUUID()) && shell.level() == this.level()
                    && d.center.distanceTo(new Vec3(this.anchorX, this.anchorY, this.anchorZ)) < this.radius + d.radius) {
                    // An even contest: no shell wear, only the crimson slashes along the seam.
                    if (this.life % 2 == 0) this.clashSeam(shell);
                }
            }
        }
        if (this.life >= 100 && this.life % 3 == 0) {
            for (int i = 0; i < 12; ++i) {
                double ang = this.random.nextDouble() * Math.PI * 2.0;
                double rr = Math.sqrt(this.random.nextDouble()) * (this.radius - 1.0);
                double ex = this.anchorX + Math.cos(ang) * rr;
                double ez = this.anchorZ + Math.sin(ang) * rr;
                double ey = this.anchorY + 0.8 + this.random.nextDouble() * 7.0;
                SlashFxEntity.spawn(this.level(), ex, ey, ez, 1, this.random.nextFloat() * 360.0f, this.random.nextFloat() * 70.0f - 35.0f, 1.8f + this.random.nextFloat() * 2.5f);
            }
            Level ang = this.level();
            if (ang instanceof ServerLevel) {
                int layer;
                ServerLevel sw = (ServerLevel)ang;
                int clearTick = this.life - 100;
                if (clearTick >= 0 && clearTick % 6 == 0 && (layer = clearTick / 6) < 384) {
                    TerrainCuts.domainLayer(sw, new Vec3(this.anchorX, this.anchorY, this.anchorZ), this.radius, layer);
                }
            }
            if (this.life % 12 == 0) {
                this.level().playSound(null, this.anchorX, this.anchorY + 2.0, this.anchorZ, SukunaSounds.DOMAIN_SLICE, SoundSource.PLAYERS, 1.8f, 0.75f + this.random.nextFloat() * 0.25f);
                Level level = this.level();
                if (level instanceof ServerLevel) {
                    ServerLevel sw = (ServerLevel)level;
                    sw.sendParticles((ParticleOptions)ParticleTypes.CLOUD, this.anchorX, this.anchorY + 1.2, this.anchorZ, 45, this.radius * 0.45, 0.25, this.radius * 0.45, 0.08);
                    sw.sendParticles((ParticleOptions)ParticleTypes.CRIT, this.anchorX, this.anchorY + 2.0, this.anchorZ, 32, this.radius * 0.55, 1.4, this.radius * 0.55, 0.2);
                }
            }
        }
    }

    /** Crimson slashes and sparks along the seam where this shrine presses on an Unlimited Void. */
    private void clashSeam(VoidDomainEntity shell) {
        Vec3 shrine = new Vec3(this.anchorX, this.anchorY, this.anchorZ);
        Vec3 voidCenter = shell.position();
        Vec3 s = shrine.subtract(voidCenter);
        double dist = s.length();
        double a = VoidDomainEntity.RADIUS, b = this.radius;
        if (dist < 1.0E-3) return;
        Vec3 n = s.scale(1.0 / dist);
        double along = (dist * dist + a * a - b * b) / (2.0 * dist);
        double seam = Math.sqrt(Math.max(0.0, a * a - along * along));
        if (seam < 1.0) return;
        Vec3 p0 = voidCenter.add(n.scale(along));
        Vec3 u = n.cross(new Vec3(0.0, 1.0, 0.0));
        if (u.lengthSqr() < 1.0E-4) u = new Vec3(1.0, 0.0, 0.0);
        u = u.normalize();
        Vec3 w = n.cross(u).normalize();
        for (int i = 0; i < 3; ++i) {
            double ang = this.random.nextDouble() * Math.PI * 2.0;
            double rr = Math.sqrt(this.random.nextDouble()) * seam;
            Vec3 at = p0.add(u.scale(Math.cos(ang) * rr)).add(w.scale(Math.sin(ang) * rr));
            SlashFxEntity.spawn(this.level(), at.x, at.y, at.z, SlashFxEntity.CLASH, this.random.nextFloat() * 360.0f, this.random.nextFloat() * 120.0f - 60.0f, 2.5f + this.random.nextFloat() * 2.5f);
        }
        if (this.life % 4 == 0 && this.level() instanceof ServerLevel sw) {
            for (int i = 0; i < 24; ++i) {
                double ang = i * Math.PI * 2.0 / 24.0 + this.life * 0.03;
                Vec3 at = p0.add(u.scale(Math.cos(ang) * seam)).add(w.scale(Math.sin(ang) * seam));
                sw.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CRIMSON_SPORE, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    @Override
    public int remainingTicks() {
        return ASSEMBLY_TICKS + this.activeTicks - this.life;
    }

    @Override
    public void setRemainingTicks(int ticks) {
        this.activeTicks = Math.max(0, this.life + Math.max(0, ticks) - ASSEMBLY_TICKS);
    }

    @Override
    public void collapse(String reasonKey) {
        Entity ownerEntity = this.getOwnerEntity();
        if (reasonKey != null && ownerEntity instanceof ServerPlayer p && !this.finished) {
            cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.actionBar(p, reasonKey);
        }
        this.finishDomain();
    }

    public void finishDomain() {
        if (!this.finished) {
            this.finished = true;
            cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.unregister(this);
            if (!this.level().isClientSide() && this.getOwnerEntity() instanceof ServerPlayer p) {
                CurseManager.startBurnout(p);
            }
        }
        if (!this.isRemoved()) {
            this.level().playSound(null, this.anchorX, this.anchorY + 3.0, this.anchorZ, SukunaSounds.DOMAIN_END, SoundSource.PLAYERS, 3.5f, 0.6f);
            this.discard();
        }
        DomainSkill.onFinished(this);
    }

    private boolean isValidTarget(LivingEntity e) {
        Player p;
        if (e == null || e.isRemoved() || !e.isAlive()) {
            return false;
        }
        if (e.isInvulnerable()) {
            return false;
        }
        if (this.isOwner((Entity)e)) {
            return false;
        }
        if (e instanceof Player && ((p = (Player)e).isCreative() || p.isSpectator())) {
            return false;
        }
        return e.distanceToSqr(this.anchorX, this.anchorY + 1.5, this.anchorZ) <= this.radius * this.radius;
    }

    private void spawnRandomSlashFx(LivingEntity target) {
        SlashFxEntity.spawn(this.level(), target.getX() + (this.random.nextDouble() - 0.5) * 4.0, target.getY() + 0.4 + this.random.nextDouble() * 1.8, target.getZ() + (this.random.nextDouble() - 0.5) * 4.0, 1, this.random.nextFloat() * 360.0f, this.random.nextFloat() * 60.0f - 30.0f, 1.0f);
    }

    private void syncParticles(ParticleOptions effect, double x, double y, double z, float ox, float oy, float oz, float speed, int count) {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sw = (ServerLevel)level;
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(effect, true, false, x, y, z, ox, oy, oz, speed, count);
        for (ServerPlayer viewer : sw.getPlayers(p -> p.distanceToSqr(x, y, z) < 9216.0)) {
            viewer.connection.send((Packet)packet);
        }
    }

    protected void writeModData(CompoundTag nbt) {
        nbt.putString("OwnerUuid", this.ownerUuid == null ? "" : this.ownerUuid);
        nbt.putInt("Life", this.life);
        nbt.putInt("Duration", this.duration);
        nbt.putDouble("Radius", this.radius);
    }

    protected void readModData(CompoundTag nbt) {
        if (nbt.contains("OwnerUuid")) {
            this.ownerUuid = nbt.getStringOr("OwnerUuid", "");
        }
        this.life = nbt.getIntOr("Life", 0);
        this.duration = nbt.getIntOr("Duration", 0) > 0 ? nbt.getIntOr("Duration", 0) : Integer.MAX_VALUE;
        this.radius = Math.max(RADIUS, nbt.getDoubleOr("Radius", 0.0));
        this.anchored = false;
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

