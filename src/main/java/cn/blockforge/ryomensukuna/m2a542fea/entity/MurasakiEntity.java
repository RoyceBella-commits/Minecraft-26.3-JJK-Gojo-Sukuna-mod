package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.SukunaDamage;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Hollow Purple: a piercing mass that erases what it passes; reaches as far as World Cut. */
public class MurasakiEntity extends TechniqueEntity {
    /** Grown twice by volume x3 (2.1 and 2.2): 4.5 x cbrt(3)^2, about 9.4 blocks. */
    public static final double RADIUS = 4.5 * StageRules.VOLUME_X3 * StageRules.VOLUME_X3;
    public static final double RANGE = 256.0;
    private static final double SPEED = 3.0;
    private static final double BLAST_RADIUS = 9.0 * StageRules.VOLUME_X3 * StageRules.VOLUME_X3;
    /** Each target is struck once, by the body or by the final burst. */
    private final Set<UUID> hit = new HashSet<>();
    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);
    private double travelled;
    /** Fused in flight from Ao and Aka: half the size, reach and damage, armour counts, kills Mahoraga. */
    private boolean fused;

    public MurasakiEntity(EntityType<? extends MurasakiEntity> type, Level level) {
        super(type, level);
    }

    public void configure(LivingEntity owner, Vec3 dir, int castId) {
        this.setup(owner, 2.5f, castId);
        this.heading = dir.normalize();
        this.setSize((float)RADIUS);
        this.setDeltaMovement(this.heading.scale(SPEED));
    }

    /** Hollow Purple born from an Ao struck by Aka in flight. */
    public void configureFused(LivingEntity owner, Vec3 dir, int castId) {
        this.configure(owner, dir, castId);
        this.fused = true;
        this.setSize((float)this.radius());
    }

    public Vec3 heading() {
        return this.heading;
    }

    public boolean fused() {
        return this.fused;
    }

    private double radius() {
        return this.fused ? RADIUS * 0.5 : RADIUS;
    }

    private void strike(ServerLevel level, LivingEntity owner, LivingEntity e) {
        this.hit.add(e.getUUID());
        if (!this.fused) {
            CurseManager.curseDamage(level, owner != null ? owner : this, e, SukunaDamage.finisher(e, this.random), this.castId);
            return;
        }
        if (e instanceof MahoragaEntity mahoraga) {
            // The fused Purple erases Mahoraga before the wheel can turn.
            mahoraga.kill(level);
            if (mahoraga.isAlive()) mahoraga.setHealth(0.0f);
            return;
        }
        Entity attacker = owner != null ? owner : this;
        CurseManager.damage(level, attacker, e, SukunaDamage.finisher(e, this.random) * 0.5f, SukunaDamage.fusedPurple(level, attacker), this.castId);
    }

    @Override
    protected void tickServer(ServerLevel level) {
        LivingEntity owner = this.owner();
        Vec3 c = this.position();
        if (this.life == 1) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0f, 0.5f);
        }
        if (!level.hasChunkAt(BlockPos.containing(c))) {
            this.discard();
            return;
        }
        if (this.collideWithWorldCut(level, c)) return;
        double radius = this.radius();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius + 0.5))) {
            if (!this.validTarget(e, owner) || this.hit.contains(e.getUUID())) continue;
            if (e.getBoundingBox().getCenter().distanceToSqr(c) > (radius + 0.8) * (radius + 0.8)) continue;
            this.strike(level, owner, e);
        }
        if (this.life % 2 == 0) {
            TerrainCuts.burst(level, c, radius - 0.3, false);
        }
        DustParticleOptions purple = new DustParticleOptions(0x8A2BE2, 3.0f);
        CurseFx.particles(level, purple, c.x, c.y, c.z, 28, radius * 0.45, radius * 0.45, radius * 0.45, 0.0);
        CurseFx.particles(level, ParticleTypes.PORTAL, c.x, c.y, c.z, 24, radius * 0.6, radius * 0.6, radius * 0.6, 0.6);
        CurseFx.particles(level, ParticleTypes.CLOUD, c.x, c.y - radius * 0.6, c.z, 8, radius * 0.5, 0.1, radius * 0.5, 0.05);
        this.fusionSpirals(level, c);
        Vec3 next = c.add(this.heading.scale(SPEED));
        this.travelled += SPEED;
        this.setPos(next);
        if (this.life % 6 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.5f, 0.5f);
        }
        if (this.travelled >= (this.fused ? RANGE * 0.5 : RANGE) || next.y < level.getMinY() - 8) {
            this.detonate(level, next, owner);
        }
    }

    /** Hollow Purple meeting World Cut: the two annihilate each other in a flash, harming nothing. */
    private boolean collideWithWorldCut(ServerLevel level, Vec3 c) {
        double radius = this.radius();
        for (CurseSlashEntity cut : level.getEntitiesOfClass(CurseSlashEntity.class, new AABB(c, c).inflate(radius + 60.0), e -> e.getMode() == 2 && !e.isRemoved())) {
            Vec3 dir = cut.getTravelDir();
            Vec3 right = TerrainCuts.right(dir);
            Vec3 up = dir.cross(right).normalize();
            Vec3 off = c.subtract(cut.position());
            double along = off.dot(dir);
            if (along < -radius || along > 4.5 + radius + 3.0) continue;
            if (Math.abs(off.dot(right)) > cut.halfSize() + radius || Math.abs(off.dot(up)) > 2.0 + radius) continue;
            Vec3 at = cut.position().add(dir.scale(Math.max(0.0, along)));
            CurseFx.particles(level, new DustParticleOptions(0x9B30FF, 3.0f), at.x, at.y, at.z, 160, 4.0, 4.0, 4.0, 0.0);
            CurseFx.particles(level, new DustParticleOptions(0xE0101A, 3.0f), at.x, at.y, at.z, 120, 5.0, 3.0, 5.0, 0.0);
            CurseFx.particles(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 3, 2.0, 2.0, 2.0, 0.0);
            CurseFx.particles(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 80, 1.0, 1.0, 1.0, 0.6);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0f, 0.4f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.5f);
            cut.discard();
            this.discard();
            return true;
        }
        return false;
    }

    /** Blue and red twisting around the purple mass: Lapse and Reversal still visibly fused together. */
    private void fusionSpirals(ServerLevel level, Vec3 c) {
        Vec3 side = this.heading.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1.0, 0.0, 0.0);
        side = side.normalize();
        Vec3 up = side.cross(this.heading).normalize();
        DustParticleOptions blue = new DustParticleOptions(0x2F7BFF, 2.2f);
        DustParticleOptions red = new DustParticleOptions(0xFF2A2A, 2.2f);
        for (int k = 0; k < 6; ++k) {
            double a = this.life * 0.45 + k * 0.35;
            double back = -k * 0.6;
            Vec3 ring = side.scale(Math.cos(a) * this.radius() * 0.95).add(up.scale(Math.sin(a) * this.radius() * 0.95));
            Vec3 along = this.heading.scale(back);
            Vec3 b = c.add(along).add(ring);
            Vec3 r = c.add(along).subtract(ring);
            CurseFx.particles(level, blue, b.x, b.y, b.z, 1, 0.1, 0.1, 0.1, 0.0);
            CurseFx.particles(level, red, r.x, r.y, r.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    /** End of the path: the unstable mass bursts outward. */
    private void detonate(ServerLevel level, Vec3 at, LivingEntity owner) {
        double scale = this.fused ? 0.5 : 1.0;
        double blast = BLAST_RADIUS * scale;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(blast))) {
            if (!this.validTarget(e, owner) || this.hit.contains(e.getUUID()) || e.distanceToSqr(at) > blast * blast) continue;
            this.strike(level, owner, e);
        }
        TerrainCuts.burst(level, at, 6.0 * StageRules.VOLUME_X3 * StageRules.VOLUME_X3 * scale, false);
        CurseFx.particles(level, new DustParticleOptions(0xC08CFF, 3.0f), at.x, at.y, at.z, 240, blast * 0.6, blast * 0.6, blast * 0.6, 0.0);
        CurseFx.particles(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 4, 2.0, 2.0, 2.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 5.0f, 0.5f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0f, 0.6f);
        this.discard();
    }
}
