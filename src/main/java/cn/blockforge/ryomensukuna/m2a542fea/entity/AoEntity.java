package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ao (Lapse): an attraction point that drags enemies inward and crushes them.
 * While the cast key is held with Ao selected it circles the caster about five blocks out;
 * letting go leaves it where it is. A tap fires it at the aim point.
 */
public class AoEntity extends TechniqueEntity {
    public static final int FIXED = 0;
    public static final int HELD = 1;
    public static final double ORBIT_RADIUS = 5.0;
    private static final double SCALE = StageRules.VOLUME_X3;
    /** Hard cap on one Ao's life even if it is held forever (60 s). */
    private static final int MAX_LIFE = 1200;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(AoEntity.class, EntityDataSerializers.INT);
    private static final Map<UUID, AoEntity> BY_OWNER = new ConcurrentHashMap<>();
    private double radius = 10.0 * SCALE;
    private int duration = 120;
    private long lastGrab;
    private double orbitAngle;

    public AoEntity(EntityType<? extends AoEntity> type, Level level) {
        super(type, level);
    }

    public static void clear() {
        BY_OWNER.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, FIXED);
    }

    public int mode() {
        return this.entityData.get(MODE);
    }

    private void setMode(int mode) {
        this.entityData.set(MODE, mode);
    }

    /** One Ao per caster: a new one replaces the old attraction point. */
    public void configure(LivingEntity owner, float charge, int castId) {
        this.setup(owner, charge, castId);
        this.applyCharge(charge);
        AoEntity old = BY_OWNER.put(owner.getUUID(), this);
        if (old != null && old != this && !old.isRemoved()) old.collapse(false);
    }

    private void applyCharge(float charge) {
        float k = Math.min(1.0f, charge / 2.5f);
        this.radius = (10.0 + 6.0 * k) * SCALE;
        this.duration = Math.max(this.duration, (int)(120 + 120 * k));
        this.setSize((float)this.radius);
    }

    public static AoEntity of(LivingEntity p) {
        AoEntity ao = BY_OWNER.get(p.getUUID());
        if (ao == null || ao.isRemoved()) {
            BY_OWNER.remove(p.getUUID());
            return null;
        }
        return ao;
    }

    /** Spawns an Ao already circling the caster (the cast key was held without an Ao present). */
    public static AoEntity summonOrbiting(LivingEntity p, float charge, int castId) {
        ServerLevel level = (ServerLevel)p.level();
        AoEntity ao = new AoEntity(cn.blockforge.ryomensukuna.m2a542fea.SukunaMod.AO, level);
        ao.configure(p, charge, castId);
        Vec3 look = p.getViewVector(1.0f);
        ao.orbitAngle = Math.atan2(look.z, look.x);
        ao.setPos(p.position().add(Math.cos(ao.orbitAngle) * ORBIT_RADIUS, 1.0, Math.sin(ao.orbitAngle) * ORBIT_RADIUS));
        ao.setMode(HELD);
        ao.lastGrab = level.getGameTime();
        level.addFreshEntity(ao);
        return ao;
    }

    /** While the key stays held, a summoned Ao keeps gathering power up to a full charge. */
    public void grow(float charge) {
        LivingEntity owner = this.owner();
        if (owner == null || charge <= this.charge()) return;
        this.setup(owner, charge, this.castId);
        this.applyCharge(charge);
    }

    /** Cast key held with Ao selected while an Ao exists (or an NPC keeping it close): it circles the caster. */
    public static boolean grab(LivingEntity p) {
        AoEntity ao = of(p);
        if (ao == null || ao.level() != p.level()) return false;
        if (ao.mode() != HELD) {
            Vec3 rel = ao.position().subtract(p.position());
            ao.orbitAngle = Math.atan2(rel.z, rel.x);
            ao.setMode(HELD);
            ao.level().playSound(null, ao.getX(), ao.getY(), ao.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.6f);
        }
        long now = ao.level().getGameTime();
        if (now - ao.lastGrab >= 5L && p instanceof ServerPlayer sp) CurseManager.spend(sp, 0.5f);
        ao.lastGrab = now;
        // Holding keeps the technique alive (within the hard cap).
        ao.duration = Math.min(MAX_LIFE, Math.max(ao.duration, ao.life + 40));
        return true;
    }

    /** Cast key released: a circling Ao stops and stays where it is. */
    public static boolean release(LivingEntity p) {
        AoEntity ao = of(p);
        if (ao == null || ao.mode() != HELD) return false;
        ao.stay();
        return true;
    }

    private void stay() {
        this.setMode(FIXED);
        // Letting go gives the technique a second wind.
        this.duration = Math.min(MAX_LIFE, Math.max(this.duration, this.life + 100));
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    @Override
    protected void tickServer(ServerLevel level) {
        LivingEntity owner = this.owner();
        if (this.life == 1) {
            TerrainCuts.burst(level, this.position(), 3.0 * SCALE, false);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.4f, 1.6f);
        }
        if (this.life > this.duration || this.life > MAX_LIFE) {
            this.collapse(true);
            return;
        }
        this.move(level, owner);
        Vec3 c = this.position();
        double r = this.radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r))) {
            if (!this.validTarget(e, owner) || e.distanceToSqr(c) > r * r) continue;
            boolean moving = this.mode() != FIXED;
            if (!moving && !this.lineOfSight(c, e)) continue;
            boolean boss = e.getType().builtInRegistryHolder().is(ConventionalEntityTypeTags.BOSSES);
            Vec3 pull = c.subtract(e.getBoundingBox().getCenter());
            double dist = pull.length();
            if (dist > 0.6) {
                double strength = (boss ? 0.1 : 0.55) * Math.min(1.2, 2.5 * SCALE / Math.max(0.6, dist) + 0.4);
                Vec3 v = e.getDeltaMovement().scale(0.4).add(pull.normalize().scale(strength));
                Mobility.push(e, v);
            }
            if (this.life % 8 == 0) {
                CurseManager.curseDamage(level, owner != null ? owner : this, e, 8.0f + this.charge() * 3.0f, this.castId);
            }
        }
        if (this.mode() != FIXED && this.life % 4 == 0) {
            TerrainCuts.burst(level, c, 2.0 * SCALE, false);
        }
        this.fx(level, c, r);
    }

    private void move(ServerLevel level, LivingEntity owner) {
        if (this.mode() != HELD) return;
        if (owner == null || level.getGameTime() - this.lastGrab > 15L) {
            this.stay();
            return;
        }
        this.orbitAngle += 0.16;
        Vec3 target = owner.position().add(Math.cos(this.orbitAngle) * ORBIT_RADIUS, 1.0, Math.sin(this.orbitAngle) * ORBIT_RADIUS);
        Vec3 pos = this.position();
        Vec3 step = target.subtract(pos);
        double max = 2.2;
        if (step.length() > max) step = step.normalize().scale(max);
        this.setPos(pos.add(step.scale(0.85)));
        CurseFx.particles(level, new DustParticleOptions(0x5F9BFF, 1.0f), pos.x, pos.y, pos.z, 3, 0.15, 0.15, 0.15, 0.0);
    }

    private void fx(ServerLevel level, Vec3 c, double r) {
        if (this.life % 2 != 0) return;
        DustParticleOptions blue = new DustParticleOptions(0x2F6BFF, 1.3f);
        for (int i = 0; i < 20; ++i) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double b = Math.acos(2.0 * this.random.nextDouble() - 1.0);
            double rr = r * (0.3 + 0.6 * this.random.nextDouble());
            Vec3 q = c.add(Math.sin(b) * Math.cos(a) * rr, Math.cos(b) * rr, Math.sin(b) * Math.sin(a) * rr);
            Vec3 in = c.subtract(q).scale(0.12);
            level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 0, in.x, in.y, in.z, 1.0);
            CurseFx.particles(level, blue, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        CurseFx.particles(level, new DustParticleOptions(0x0A1E7A, 3.0f), c.x, c.y, c.z, 10, 0.5, 0.5, 0.5, 0.0);
        if (this.life % 20 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.6f, 0.6f);
        }
    }

    /** Final implosion: the gathered space snaps shut. */
    private void collapse(boolean loud) {
        if (this.level() instanceof ServerLevel level && loud) {
            Vec3 c = this.position();
            LivingEntity owner = this.owner();
            double blast = (6.0 + this.charge() * 1.5) * SCALE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(blast))) {
                if (!this.validTarget(e, owner) || e.distanceToSqr(c) > blast * blast) continue;
                CurseManager.curseDamage(level, owner != null ? owner : this, e, 24.0f + this.charge() * 8.0f, this.castId);
            }
            TerrainCuts.burst(level, c, (3.5 + this.charge() * 0.6) * SCALE, false);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2.0f, 0.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 0.6f);
            CurseFx.particles(level, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 120, blast * 0.15, blast * 0.15, blast * 0.15, 0.5);
            CurseFx.particles(level, ParticleTypes.EXPLOSION, c.x, c.y, c.z, 4, blast * 0.1, blast * 0.1, blast * 0.1, 0.0);
        }
        if (this.ownerId != null) BY_OWNER.remove(this.ownerId, this);
        this.discard();
    }
}
