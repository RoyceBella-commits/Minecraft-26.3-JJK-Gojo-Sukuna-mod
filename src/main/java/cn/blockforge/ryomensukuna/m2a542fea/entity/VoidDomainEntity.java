package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.SukunaDamage;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Unlimited Void: a closed black sphere. Every enemy inside is held completely still for as long
 * as the domain stands and takes a little sure-hit damage; the world itself is never altered.
 */
public class VoidDomainEntity extends TechniqueEntity implements DomainClash.Collapsible {
    public static final double RADIUS = StageRules.DOMAIN_RADIUS;
    public static final int OPEN_TICKS = 30;
    public static final int CLOSE_TICKS = 24;
    public static final float SHELL_MAX = 480.0f;
    private static final float SURE_HIT_DAMAGE = 3.0f;
    private static final EntityDataAccessor<Float> SHELL = SynchedEntityData.defineId(VoidDomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CLOSE_AT = SynchedEntityData.defineId(VoidDomainEntity.class, EntityDataSerializers.INT);
    /** Everything currently held by some Unlimited Void (players and mobs), until the given game time. */
    private static final Map<UUID, Long> STUNNED = new ConcurrentHashMap<>();
    private final Map<UUID, Vec3> frozenAt = new HashMap<>();
    private int duration = StageRules.DOMAIN_TICKS;
    private boolean finished;

    public VoidDomainEntity(EntityType<? extends VoidDomainEntity> type, Level level) {
        super(type, level);
    }

    public static void clearStuns() {
        STUNNED.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHELL, SHELL_MAX);
        builder.define(CLOSE_AT, -1);
    }

    public float shell() {
        return this.entityData.get(SHELL);
    }

    /** Age (ticks) at which the shell started breaking, or -1 while it stands. */
    public int closeAt() {
        return this.entityData.get(CLOSE_AT);
    }

    public void configure(LivingEntity owner, int castId, int duration) {
        this.setup(owner, 0.0f, castId);
        // The black hole opens in the sky behind the caster: remember which way they faced.
        this.setYRot(owner.getYRot());
        this.yRotO = owner.getYRot();
        this.setSize((float)RADIUS);
        this.duration = duration;
    }

    public static double openFraction(float age) {
        float t = Math.min(1.0f, Math.max(0.0f, age / OPEN_TICKS));
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    /** Held still by an Unlimited Void right now (no moving, casting or striking). */
    public static boolean stunned(Entity e) {
        if (e == null) return false;
        Long until = STUNNED.get(e.getUUID());
        if (until == null) return false;
        if (until < e.level().getGameTime()) {
            STUNNED.remove(e.getUUID());
            return false;
        }
        return true;
    }

    public static boolean hardStunned(ServerPlayer p) {
        return stunned(p);
    }

    /** Whether a straight move enters or leaves any closed domain shell (not allowed). */
    public static boolean crossesBoundary(Level level, Vec3 from, Vec3 to) {
        for (DomainClash.Domain d : DomainClash.active()) {
            if (!d.closed || d.entity.level() != level) continue;
            if (d.contains(from) != d.contains(to)) return true;
        }
        return false;
    }

    @Override
    public int remainingTicks() {
        return this.duration - this.life;
    }

    @Override
    public void setRemainingTicks(int ticks) {
        this.duration = this.life + Math.max(0, ticks);
    }

    public void damageShell(float amount) {
        if (this.finished) return;
        float left = this.shell() - amount;
        this.entityData.set(SHELL, Math.max(0.0f, left));
        if (left <= 0.0f) this.collapse("sukuna.hint.void_shell_broken");
    }

    /** Players, monsters, bosses, hostile technique users and foreign Mahoraga; never allies or bystanders. */
    private boolean isEnemy(LivingEntity e, LivingEntity owner) {
        if (!this.validTarget(e, owner)) return false;
        if (e instanceof Player || e instanceof Enemy) return true;
        if (e instanceof MahoragaEntity m) return owner == null || !owner.getStringUUID().equals(m.ownerUuid());
        // A technique NPC is only an enemy of a player caster if it would fight them.
        if (e instanceof JjkNpcEntity npc) return !(owner instanceof Player) || npc.canHarm(owner) || npc.getTarget() == owner;
        return false;
    }

    @Override
    protected void tickServer(ServerLevel level) {
        Vec3 c = this.position();
        if (this.finished) {
            this.shatterFx(level, c);
            if (this.life - this.closeAt() >= CLOSE_TICKS) this.discard();
            return;
        }
        LivingEntity owner = this.owner();
        ServerPlayer player = this.playerOwner();
        if (this.life == 1) {
            if (owner != null) {
                DomainClash.register(owner, this, c, RADIUS, true);
                if (player != null) Progression.recordDomainExpanded(player);
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.45f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 3.0f, 0.5f);
        }
        if (owner == null) {
            this.collapse(null);
            return;
        }
        if (this.life > this.duration) {
            this.collapse("sukuna.hint.domain_end");
            return;
        }
        if (player != null && !CurseManager.spend(player, 0.4f)) {
            this.collapse("sukuna.hint.energy");
            return;
        }
        double current = RADIUS * openFraction(this.life);
        if (this.life <= OPEN_TICKS) this.expansionFx(level, c, current);
        long now = level.getGameTime();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(current))) {
            if (e.distanceToSqr(c) > current * current || !this.isEnemy(e, owner)) continue;
            // In a clash the rival caster is untouched; everyone else suffers both domains.
            if (e.getUUID().equals(DomainClash.clashRival(this))) continue;
            this.hold(e, now);
            if (this.life % 20 == 0) {
                CurseManager.damage(level, owner, e, SURE_HIT_DAMAGE, SukunaDamage.sureHit(level, owner), this.castId);
            }
        }
        DomainClash.Domain d = DomainClash.of(this);
        if (player != null && d != null && this.life % 20 == 0) {
            SukunaNet.actionBar(player, "sukuna.hint.domain_stability", Math.round(d.stability() * 100.0f), Math.round(this.shell() / SHELL_MAX * 100.0f), (this.duration - this.life) / 20);
        }
    }

    /** Complete immobilisation: pinned in place, no velocity, no pathing, no target, no strength. */
    private void hold(LivingEntity e, long now) {
        Vec3 pin = this.frozenAt.computeIfAbsent(e.getUUID(), k -> e.position());
        STUNNED.put(e.getUUID(), now + 2L);
        if (e.position().distanceToSqr(pin) > 0.01) e.teleportTo(pin.x, pin.y, pin.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0.0;
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 9, false, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 250, false, false, false));
        if (e instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        e.needsSync = true;
        if ((this.life + e.getId()) % 10 == 0) {
            Vec3 at = e.getBoundingBox().getCenter();
            CurseFx.particles(this.level(), ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.3, 0.5, 0.3, 0.0);
        }
    }

    /** Darkness spreads from the caster: a pale front races across the ground as the sphere opens. */
    private void expansionFx(ServerLevel level, Vec3 c, double r) {
        DustParticleOptions front = new DustParticleOptions(0xDDEBFF, 1.2f);
        int n = 56;
        for (int i = 0; i < n; ++i) {
            double a = i * Math.PI * 2.0 / n + this.life * 0.05;
            CurseFx.particles(level, front, c.x + Math.cos(a) * r, c.y - 0.8, c.z + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.0);
        }
        if (this.life % 6 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.5f + this.life * 0.02f);
        }
    }

    private void shatterFx(ServerLevel level, Vec3 c) {
        int t = this.life - this.closeAt();
        if (t > 12) return;
        double r = RADIUS * (1.0 + t * 0.02);
        for (int i = 0; i < 20; ++i) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double b = Math.acos(2.0 * this.random.nextDouble() - 1.0);
            Vec3 n = new Vec3(Math.sin(b) * Math.cos(a), Math.cos(b), Math.sin(b) * Math.sin(a));
            Vec3 q = c.add(n.scale(r));
            level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 0, n.x * 0.3, n.y * 0.3, n.z * 0.3, 1.0);
            CurseFx.particles(level, new DustParticleOptions(0x0B0B1A, 2.4f), q.x, q.y, q.z, 1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    @Override
    public void collapse(String reasonKey) {
        if (this.finished) return;
        this.finished = true;
        this.entityData.set(CLOSE_AT, this.life);
        ServerPlayer player = this.playerOwner();
        DomainClash.unregister(this);
        for (UUID id : this.frozenAt.keySet()) STUNNED.remove(id);
        if (this.level() instanceof ServerLevel level) {
            Vec3 c = this.position();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.4f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 3.0f, 0.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3.0f, 0.5f);
        }
        if (player != null) {
            if (reasonKey != null) SukunaNet.actionBar(player, reasonKey);
            CurseManager.startBurnout(player);
        }
        if (!(this.level() instanceof ServerLevel)) this.discard();
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }
}
