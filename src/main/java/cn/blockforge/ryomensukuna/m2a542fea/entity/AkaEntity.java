package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Aka (Reversal Red): a fast repulsive shot that bursts outward on contact. */
public class AkaEntity extends TechniqueEntity {
    private static final double SPEED = 2.2;
    private static final double SCALE = StageRules.VOLUME_X3;
    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);

    public AkaEntity(EntityType<? extends AkaEntity> type, Level level) {
        super(type, level);
    }

    public void configure(LivingEntity owner, Vec3 dir, float charge, int castId) {
        this.setup(owner, charge, castId);
        this.heading = dir.normalize();
        this.setSize((float)((0.9f + charge * 0.45f) * SCALE));
        this.setDeltaMovement(this.heading.scale(SPEED));
    }

    @Override
    protected void tickServer(ServerLevel level) {
        LivingEntity owner = this.owner();
        Vec3 from = this.position();
        Vec3 to = from.add(this.heading.scale(SPEED));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        AABB sweep = new AABB(from, end).inflate((0.6 + this.charge() * 0.15) * SCALE);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep)) {
            if (!this.validTarget(e, owner)) continue;
            if (e.getBoundingBox().inflate(0.4 * SCALE).clip(from, end).isPresent()) {
                this.explode(level, e.getBoundingBox().getCenter());
                return;
            }
        }
        if (block.getType() != HitResult.Type.MISS || this.life > 40) {
            this.explode(level, end);
            return;
        }
        this.setPos(end);
        CurseFx.particles(level, new DustParticleOptions(0xFF2020, 1.6f), end.x, end.y, end.z, 7, 0.18, 0.18, 0.18, 0.0);
        CurseFx.particles(level, new DustParticleOptions(0xFFFFFF, 0.9f), end.x, end.y, end.z, 3, 0.08, 0.08, 0.08, 0.0);
    }

    private void explode(ServerLevel level, Vec3 at) {
        LivingEntity owner = this.owner();
        float k = Math.min(1.0f, this.charge() / 2.5f);
        double radius = (6.0 + 6.0 * k) * SCALE;
        float damage = 30.0f + 40.0f * k;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
            if (!this.validTarget(e, owner) || e.distanceToSqr(at) > radius * radius) continue;
            CurseManager.curseDamage(level, owner != null ? owner : this, e, damage, this.castId);
            Vec3 away = e.getBoundingBox().getCenter().subtract(at);
            if (away.lengthSqr() < 1.0E-3) away = this.heading;
            boolean capped = e instanceof Player || e.getType().builtInRegistryHolder().is(ConventionalEntityTypeTags.BOSSES);
            // Closer targets are thrown harder; players and bosses have a capped knockback.
            double falloff = 1.0 - 0.5 * Math.sqrt(e.distanceToSqr(at)) / radius;
            double power = capped ? 1.2 : (2.6 + 1.8 * k) * falloff;
            Vec3 v = away.normalize().scale(power);
            Mobility.push(e, new Vec3(v.x, Math.min(capped ? 0.5 : 1.1, 0.45 + v.y), v.z));
        }
        TerrainCuts.burst(level, at, (3.0 + 3.0 * k) * SCALE, false);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0f, 1.0f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.8f, 0.7f);
        DustParticleOptions red = new DustParticleOptions(0xFF2A2A, 2.0f);
        int n = 56;
        for (int i = 0; i < n; ++i) {
            double a = i * Math.PI * 2.0 / n;
            for (double y : new double[]{-0.4, 0.0, 0.4}) {
                Vec3 out = new Vec3(Math.cos(a), y, Math.sin(a)).normalize().scale(0.5 + radius * 0.08);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, out.x, out.y, out.z, 1.0);
            }
            CurseFx.particles(level, red, at.x + Math.cos(a) * radius * 0.6, at.y, at.z + Math.sin(a) * radius * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
        }
        CurseFx.particles(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, radius * 0.2, radius * 0.1, radius * 0.2, 0.0);
        CurseFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 10, radius * 0.4, radius * 0.3, radius * 0.4, 0.0);
        this.discard();
    }
}
