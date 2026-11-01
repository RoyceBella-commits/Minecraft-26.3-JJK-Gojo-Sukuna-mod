package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.api.NpcHooks;
import cn.blockforge.ryomensukuna.m2a542fea.combat.BlackFlash;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DamageTaken;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DuelRules;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * A spawn-egg sorcerer at full strength (stage V values). Against a strong foe it mixes range and
 * hand-to-hand: techniques from range (light ones come back sooner than heavy ones, and some lead
 * into a follow-up), a blink / bound in for a flurry fought on the move, a break-away, and flurries
 * of its own between techniques; a foe that keeps chasing it down is fought where it stands. It
 * reads the fight: it leads its shots, sidesteps what flies at it, rushes or slips a foe charging a
 * great blow, saves its finisher and domain for an opening, and heals with a channelled reverse
 * cursed technique when badly hurt. It moves with its mobility (chasing, breaking away, climbing
 * out of pits), remembers a foe that slips out of sight, and wanders about when there is nobody to
 * fight.
 */
public abstract class JjkNpcEntity extends PathfinderMob {
    /** A far larger pool than any player's, so the NPCs read as bosses rather than players. */
    public static final float MAX_HEALTH = 200.0f;
    /** Health of the earlier NPCs (red + stage V gold); fights are balanced around it. */
    private static final float LEGACY_HEALTH = 20.0f + StageRules.goldHp(StageRules.MAX_STAGE);
    /** Every hit on an NPC is scaled by this, so the bigger pool takes as many blows as before. */
    public static final float DAMAGE_SCALE = MAX_HEALTH / LEGACY_HEALTH;
    private static final Identifier FLASH_BOOST = SukunaMod.id("npc_black_flash");
    private static final Identifier VS_BOOST = SukunaMod.id("npc_vs_mod");
    /** Domain cooldown (60 s from the cast). Nothing ever opens a domain while it runs. */
    private static final int DOMAIN_COOLDOWN = 1200;
    /** The fight logic blinks / bounds at most this often (2.5 s). */
    protected static final int MOBILITY_COOLDOWN = 50;
    /** A foe out of sight is still hunted this long (20 s); in that time it may also be sensed through walls. */
    private static final int MEMORY_TICKS = 400;
    private static final double SENSE_RANGE = 16.0;
    /** Chance to sidestep a World Cut / Hollow Purple aimed at it that it does not answer. */
    private static final float DODGE_CHANCE = 0.6f;
    /** Chance to sidestep a projectile about to hit it (checked a few times as it comes in). */
    private static final float PROJECTILE_DODGE_CHANCE = 0.3f;
    /** A fight this long counts as a hard one (8 s): Gojo reaches for the fused Purple. */
    private static final int LONG_FIGHT_TICKS = 160;
    /** A foe that struck it for less than this much in the last 3 s does not take its attention off the current one. */
    private static final float SWITCH_DAMAGE = 10.0f;
    protected int skillCooldown = 60;
    protected int domainCooldown = 200;
    protected int combatTicks;
    /** Hand-to-hand window: after a gap-closer, a flurry between techniques, or a chaser turned on. */
    protected int meleeTicks;
    /** When the window ends the sorcerer breaks off to range again. */
    private boolean breakOffAfter;
    /** The window is a counter against a chaser (dropped once the chaser backs off). */
    private boolean countering;
    protected int mobilityCooldown = 20;
    /** Ticks of a leap / blink during which a fall does not hurt. */
    private int fallGrace;
    /**
     * Reverse cursed technique cooldown (the Fate mod reads this field by name to hold the heal off
     * while Excalibur's wound lasts).
     */
    private int burstHealCooldown;
    /** Ticks left of a reverse cursed technique being channelled, and the health lost during it. */
    protected int rctTicks;
    private float rctTaken;
    /** Whether this sorcerer fights the current target from range between flurries (rolled once per target). */
    private boolean preferRange;
    /** Ticks the current target has been out of sight. */
    private int unseenTicks;
    /** Ticks until a counter-finisher is released at {@link #counterTarget}. */
    private int counterDelay = -1;
    private int counterCooldown;
    private LivingEntity counterTarget;
    /** True while a counter-finisher is being released: it is not answered in turn. */
    private static boolean answering;
    /** Ticks until a sidestep away from a finisher released at {@link #dodgeFrom}. */
    private int dodgeDelay = -1;
    private Vec3 dodgeFrom;
    /** Its own sidesteps (projectiles, charged blows) come at most this often. */
    private int evadeCooldown;
    private LivingEntity rangeRolledFor;
    /** The last foe fought and when, so one that slips away is taken up again. */
    private LivingEntity lastFoe;
    private long lastFoeAt;
    private Vec3 lastCheckPos;
    private final NpcTactics tactics = new NpcTactics();
    /** Rolled when a fight starts: how long it waits for an opening before the domain / the finisher anyway. */
    private int domainPatience = 400;
    private int finisherPatience = 240;
    /** Ticks until the next roll for a flurry of its own between techniques. */
    private int meleeRoll = 20;
    /** Recovery after the technique just used (set by {@link #recover}); 0 for the default. */
    private int recovery;
    /** A follow-up queued by the last technique: which, and in how many ticks. */
    private int followUpKind;
    private int followUpTicks = -1;
    /** The technique used last, and how many times in a row. */
    private int lastSkill = -1;
    private int repeats;
    /** Health lost to each attacker in the current 3 s window. */
    private final Map<UUID, Float> recentDamage = new HashMap<>();
    private long recentFrom;
    private long lastHurtAt = -1000L;

    protected JjkNpcEntity(EntityType<? extends JjkNpcEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.ARMOR, StageRules.armor(StageRules.MAX_STAGE))
            .add(Attributes.ATTACK_DAMAGE, StageRules.unarmedDamage(StageRules.MAX_STAGE))
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.FOLLOW_RANGE, 48.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    /** Whom this sorcerer fights (targets and technique damage both use it). */
    public abstract boolean canHarm(LivingEntity target);

    /** Technique friendly-fire rule: its natural foes, plus whoever it is fighting right now. */
    public boolean mayHurt(LivingEntity target) {
        return target != this && (this.canHarm(target) || target == this.getTarget());
    }

    protected int foesNear(double radius) {
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), this::canHarm).size();
    }

    /** One technique chosen for the current distance; returns false to keep fighting hand to hand. */
    protected abstract boolean useSkill(ServerLevel level, LivingEntity target, double distance);

    /** Opens this sorcerer's domain now; returns false if it could not. */
    protected abstract boolean castDomain(ServerLevel level);

    /** Whether this sorcerer's own domain is currently open. */
    protected abstract boolean domainActive();

    /**
     * Moves to {@code spot} with this sorcerer's mobility (Gojo blinks, Sukuna bounds), facing
     * {@code facing} if given. {@code low}: a quick flat sidestep rather than a full leap. False if
     * it cannot go there now (no room, airborne, a domain wall in the way).
     */
    protected abstract boolean dashTo(ServerLevel level, Vec3 spot, LivingEntity facing, boolean low);

    /** This sorcerer's finisher (Hollow Purple / World Cut) if it is off cooldown; returns whether it was cast. */
    protected abstract boolean tryFinisher(ServerLevel level, LivingEntity target);

    /** Ticks until the finisher is ready again (0 when it is). */
    protected abstract int finisherWait();

    /** The finisher cast straight back at an opponent's finisher (it then cools down as if cast). */
    protected abstract void counterFinisher(ServerLevel level, LivingEntity target);

    /** Whether this sorcerer answers the given finisher (Gojo answers World Cut, Sukuna answers Hollow Purple). */
    protected abstract boolean answers(boolean worldCut);

    /** A follow-up queued with {@link #queueFollowUp}; returns whether something was done. */
    protected boolean followUp(ServerLevel level, LivingEntity target, int kind, double distance) {
        return false;
    }

    /**
     * An opponent released World Cut / Hollow Purple: nearby technique NPCs that fight the caster
     * may answer with their own finisher a moment later so the two collide (an answer is never
     * answered in turn); one that does not answer and is in the line of fire may sidestep it.
     */
    public static void onFinisher(LivingEntity caster, boolean worldCut) {
        for (JjkNpcEntity npc : caster.level().getEntitiesOfClass(JjkNpcEntity.class, caster.getBoundingBox().inflate(128.0),
                n -> n != caster && n.isAlive())) {
            if (VoidDomainEntity.stunned(npc) || npc.counterDelay >= 0) continue;
            if (!answering && npc.answers(worldCut) && npc.mayHurt(caster) && npc.counterCooldown <= 0
                    && npc.finisherWait() <= DuelRules.COUNTER_READY_WITHIN && npc.random.nextFloat() < DuelRules.COUNTER_CHANCE) {
                npc.counterTarget = caster;
                npc.counterDelay = 5;
            } else if (npc.dodgeDelay < 0 && npc.mobilityCooldown <= 0 && npc.threatenedBy(caster) && npc.random.nextFloat() < DODGE_CHANCE) {
                npc.dodgeFrom = caster.position();
                npc.dodgeDelay = 1;
            }
        }
    }

    /** The caster is after this sorcerer, or aims within about 20 degrees of it. */
    private boolean threatenedBy(LivingEntity caster) {
        if (caster instanceof Mob mob && mob.getTarget() == this) return true;
        Vec3 to = this.getBoundingBox().getCenter().subtract(caster.getEyePosition());
        double d = to.length();
        return d > 1.0E-3 && d < 96.0 && caster.getViewVector(1.0f).dot(to.scale(1.0 / d)) > 0.94;
    }

    /** A move saved for hard fights, tried before anything else; false when it does not apply. */
    protected boolean hardFightMove(ServerLevel level, LivingEntity target, double distance) {
        return false;
    }

    /** Fought the current target for 8 s without a win. */
    protected boolean longFight() {
        return this.combatTicks > LONG_FIGHT_TICKS;
    }

    /** Whether this sorcerer may still try its domain while held by Unlimited Void. */
    protected boolean canDomainWhileHeld() {
        return false;
    }

    /** Gojo / Sukuna sorcerers: the technique NPCs and players on either route. */
    protected static boolean sorcerer(LivingEntity e) {
        if (e instanceof JjkNpcEntity) return true;
        return e instanceof ServerPlayer sp && CurseManager.of(sp).route() != StageRules.NONE;
    }

    protected static boolean bossLike(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || e instanceof net.minecraft.world.entity.boss.wither.WitherBoss
            || e instanceof net.minecraft.world.entity.monster.warden.Warden || e instanceof net.minecraft.world.entity.monster.ElderGuardian
            || e.getType().builtInRegistryHolder().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags.BOSSES);
    }

    /** Fighting this foe from range right now (outside a hand-to-hand window). */
    protected boolean holdingRange() {
        if (this.getTarget() == null || this.meleeTicks > 0) return false;
        return this.preferRange;
    }

    /** Channelling the reverse cursed technique: standing still, open to a blow. */
    public boolean channellingRct() {
        return this.rctTicks > 0;
    }

    /**
     * The foe is open to a heavy blow right now: held by Unlimited Void, channelling a heal, sent
     * flying, or reeling from a blow that knocked it back.
     */
    protected boolean targetOpen(LivingEntity t) {
        if (VoidDomainEntity.stunned(t)) return true;
        if (t instanceof JjkNpcEntity npc && npc.channellingRct()) return true;
        if (!t.onGround() && !t.isInWater() && !t.isNoGravity() && t.fallDistance > 1.5) return true;
        return t.hurtTime > 0 && t.getDeltaMovement().horizontalDistanceSqr() > 0.25;
    }

    /** The foe is charging a great blow (a Fate noble phantasm held ready) or healing. */
    protected static boolean channeling(LivingEntity t) {
        if (t instanceof JjkNpcEntity npc && npc.channellingRct()) return true;
        if (!t.isUsingItem()) return false;
        return "fatekings".equals(BuiltInRegistries.ITEM.getKey(t.getUseItem().getItem()).getNamespace());
    }

    /** An enemy Gojo / Sukuna has opened a domain around (or right next to) this sorcerer. */
    private boolean enemyDomainNearby(ServerLevel level) {
        for (cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.Domain d : cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash.active()) {
            if (d.owner.equals(this.getUUID()) || d.entity.level() != level) continue;
            if (!(level.getEntity(d.owner) instanceof LivingEntity owner) || !sorcerer(owner)) continue;
            boolean hostile = this.canHarm(owner) || owner instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this;
            if (hostile && this.position().distanceTo(d.center) < d.radius + 10.0) return true;
        }
        return false;
    }

    private boolean bossNear(double radius) {
        return !this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), e -> e.isAlive() && bossLike(e)).isEmpty();
    }

    /**
     * Domain decision, only ever when the cooldown has run out. Must try: an enemy sorcerer opened a
     * domain here, or health is down to a third. Soon: over 20 foes or a boss inside the domain
     * range. Otherwise it waits for an opening on a strong foe (or a crowd): the foe held, charging a
     * great blow or badly hurt, or a while (rolled per fight) without one.
     */
    private boolean wantsDomain(ServerLevel level, LivingEntity target, double distance) {
        if (this.domainCooldown > 0 || this.domainActive()) return false;
        if (this.enemyDomainNearby(level)) return true;
        if (target == null) return false;
        if (this.getHealth() <= this.getMaxHealth() / 3.0f) return true;
        double range = StageRules.DOMAIN_RADIUS;
        boolean crowd = this.foesNear(range) > 20 || this.bossNear(range);
        if (crowd && this.combatTicks > 40 && distance <= range) return true;
        boolean worth = strong(target) || this.foesNear(20.0) >= 3;
        if (!worth || distance > 25.0 || !this.hasLineOfSight(target)) return false;
        boolean opening = this.targetOpen(target) || channeling(target) || target.getHealth() < target.getMaxHealth() * 0.35f;
        return opening && this.combatTicks > 60 || this.combatTicks > this.domainPatience;
    }

    /**
     * Finisher decision, only when it is ready and the foe is worth it and not on top of it: at once
     * (mostly) on an opening or a foe down to 40%; otherwise now and then once the fight has lasted
     * a while (rolled per fight).
     */
    protected boolean wantsFinisherNow(LivingEntity target, double distance) {
        if (this.finisherWait() > 0 || distance < 6.0 || !strong(target)) return false;
        boolean opening = this.targetOpen(target) || channeling(target) || target.getHealth() < target.getMaxHealth() * 0.4f;
        if (opening) return this.random.nextFloat() < 0.8f;
        return this.combatTicks > this.finisherPatience && this.random.nextFloat() < 0.35f;
    }

    protected static boolean sukunaPlayer(LivingEntity e) {
        return e instanceof ServerPlayer sp && CurseManager.of(sp).route() == StageRules.SUKUNA;
    }

    protected static boolean ignoredPlayer(LivingEntity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    /** Opponents that deserve the heavy techniques. */
    protected static boolean strong(LivingEntity t) {
        return t instanceof Player || t instanceof JjkNpcEntity || t.getMaxHealth() >= 60.0f;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RangeMoveGoal());
        this.goalSelector.addGoal(2, new CloseFightGoal());
        // Wanders freely with nobody to fight, players near or not (a stroll about every 2 s).
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.9, 40, false) {
            @Override
            protected Vec3 getPosition() {
                Vec3 land = LandRandomPos.getPos(this.mob, 14, 7);
                return land != null ? land : super.getPosition();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setUnseenMemoryTicks(MEMORY_TICKS));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.canHarm(target))
            .setUnseenMemoryTicks(MEMORY_TICKS));
        this.targetSelector.addGoal(3, new SenseFoeGoal());
    }

    /** NPCs saved before the 200-health pool keep their share of health on the new pool. */
    private void upgradeLegacyHealth() {
        AttributeInstance max = this.getAttribute(Attributes.MAX_HEALTH);
        if (max == null || max.getBaseValue() >= MAX_HEALTH) return;
        float share = this.getHealth() / Math.max(1.0f, this.getMaxHealth());
        max.setBaseValue(MAX_HEALTH);
        this.setHealth(share * this.getMaxHealth());
    }

    protected Vec3 aimAt(LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(this.getEyePosition());
        return d.lengthSqr() < 1.0E-4 ? this.getViewVector(1.0f) : d.normalize();
    }

    /** Aim for a shot flying {@code speed} blocks a tick: where the foe will be when it gets there (at most 6 blocks ahead). */
    protected Vec3 leadAim(LivingEntity target, double speed) {
        Vec3 c = target.getBoundingBox().getCenter();
        Vec3 eye = this.getEyePosition();
        double[] l = DuelRules.lead(eye.distanceTo(c), speed, target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo, 6.0);
        Vec3 d = c.add(l[0], l[1], l[2]).subtract(eye);
        return d.lengthSqr() < 1.0E-4 ? this.getViewVector(1.0f) : d.normalize();
    }

    /** Horizontal unit vector from the foe toward this sorcerer (or straight back if they overlap). */
    protected Vec3 fromFoe(LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        away = new Vec3(away.x, 0.0, away.z);
        if (away.lengthSqr() < 1.0E-3) away = this.getViewVector(1.0f).scale(-1.0).multiply(1.0, 0.0, 1.0);
        return away.lengthSqr() < 1.0E-3 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
    }

    /** {@code v} turned about the vertical by {@code degrees}. */
    private static Vec3 turned(Vec3 v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, 0.0, v.x * s + v.z * c);
    }

    /** A spot within reach of the foe, somewhere new each time: a flank, behind it, or in between (1.5-3 blocks off). */
    private Vec3 besideFoe(LivingEntity target) {
        double[] angles = {60.0, 90.0, 135.0, 180.0};
        double angle = angles[this.random.nextInt(angles.length)] * (this.random.nextBoolean() ? 1.0 : -1.0);
        return target.position().add(turned(this.fromFoe(target), angle).scale(1.5 + this.random.nextDouble() * 1.5));
    }

    /** Free spot for this body near {@code aim} that does not cross a closed domain wall; null if none. */
    protected Vec3 landing(Vec3 aim, Vec3 dir) {
        Vec3 spot = Mobility.safeSpot(this, aim, dir);
        if (spot == null || VoidDomainEntity.crossesBoundary(this.level(), this.position(), spot)) return null;
        return spot;
    }

    /** Free blocks above the head, up to {@code max}. */
    protected int headroom(int max) {
        int free = 0;
        for (int i = 1; i <= max; ++i) {
            if (!this.level().noCollision(this, this.getBoundingBox().move(0.0, i, 0.0))) break;
            free = i;
        }
        return free;
    }

    /** Uses the mobility (if it is ready) to reach {@code spot}; a failed try is retried a moment later. */
    protected boolean mobility(ServerLevel level, Vec3 spot, LivingEntity facing, boolean low) {
        if (this.mobilityCooldown > 0 || NpcHooks.restrained(this)) return false;
        if (!this.dashTo(level, spot, facing, low)) {
            this.mobilityCooldown = 10;
            return false;
        }
        this.moved();
        return true;
    }

    /** A blink / bound was just made: the mobility cools down and the landing does not hurt. */
    protected void moved() {
        this.mobilityCooldown = MOBILITY_COOLDOWN;
        this.fallGrace = 60;
    }

    /**
     * Closes in on the foe: blinks / bounds to its flank or back when it can; otherwise it simply
     * walks in. (Subclasses hang follow-ups on {@link #onGapClosed}, never override this: the Fate
     * mod's compat names this method.)
     */
    protected boolean gapClose(ServerLevel level, LivingEntity target) {
        boolean closed = this.mobility(level, this.besideFoe(target), target, false);
        if (closed) this.onGapClosed(level, target);
        return closed;
    }

    /** Just closed in on the foe with the mobility. */
    protected void onGapClosed(ServerLevel level, LivingEntity target) {
    }

    /** Breaks away to range (10-18 blocks from the foe, a little off the straight line back). */
    protected boolean disengage(ServerLevel level, LivingEntity target) {
        Vec3 back = turned(this.fromFoe(target), (this.random.nextDouble() - 0.5) * 70.0);
        return this.mobility(level, target.position().add(back.scale(10.0 + this.random.nextDouble() * 8.0)), target, false);
    }

    /** Stuck in a pit or under cover: up and out, landing in front of the foe. */
    protected boolean escapeUp(ServerLevel level, LivingEntity target) {
        return this.mobility(level, target.position().add(this.fromFoe(target).scale(2.5)), target, false);
    }

    /**
     * A quick sidestep away from a threat coming from {@code from}: across its line, a little forward
     * or back, 4-10 blocks. It has its own short cooldown and also holds the mobility a moment.
     */
    protected boolean evade(ServerLevel level, Vec3 from) {
        if (this.evadeCooldown > 0 || NpcHooks.restrained(this)) return false;
        Vec3 away = this.position().subtract(from);
        away = new Vec3(away.x, 0.0, away.z);
        away = away.lengthSqr() < 1.0E-4 ? this.getViewVector(1.0f).multiply(-1.0, 0.0, -1.0) : away.normalize();
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1.0, 0.0, 0.0);
        double angle = (60.0 + this.random.nextDouble() * 60.0) * (this.random.nextBoolean() ? 1.0 : -1.0);
        Vec3 spot = this.position().add(turned(away, angle).scale(4.0 + this.random.nextDouble() * 6.0));
        if (!this.dashTo(level, spot, this.getTarget(), true)) {
            this.evadeCooldown = 8;
            return false;
        }
        this.evadeCooldown = 30 + this.random.nextInt(30);
        this.mobilityCooldown = Math.max(this.mobilityCooldown, 20);
        this.fallGrace = 60;
        return true;
    }

    /** Starts channelling the reverse cursed technique (it stands still and heals once it completes). */
    protected boolean reverseCurse(ServerLevel level) {
        if (this.burstHealCooldown > 0 || this.rctTicks > 0 || NpcHooks.restrained(this)) return false;
        this.rctTicks = DuelRules.RCT_CHANNEL;
        this.rctTaken = 0.0f;
        this.getNavigation().stop();
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SukunaSounds.RCT_HEAL, SoundSource.HOSTILE, 0.6f, 0.6f);
        return true;
    }

    /** Badly hurt, the heal ready, and a moment's room: nobody close, or the foe is held fast. */
    private boolean wantsRct(LivingEntity target, double distance) {
        if (this.burstHealCooldown > 0 || this.getHealth() >= this.getMaxHealth() * DuelRules.RCT_BELOW) return false;
        if (target == null) return true;
        boolean room = distance > 6.0 && this.foesNear(6.0) == 0;
        return (room || VoidDomainEntity.stunned(target)) && this.random.nextFloat() < 0.15f;
    }

    private void tickRct(ServerLevel level) {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
        if (this.getTarget() != null) this.getLookControl().setLookAt(this.getTarget(), 30.0f, 30.0f);
        if (this.rctTicks % 4 == 0) CurseFx.particles(level, ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 4, 0.4, 0.7, 0.4, 0.02);
        if (--this.rctTicks > 0) return;
        this.burstHealCooldown = DuelRules.RCT_COOLDOWN;
        this.heal(DuelRules.RCT_HEAL);
        CurseFx.particles(level, ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SukunaSounds.RCT_HEAL, SoundSource.HOSTILE, 1.0f, 0.9f);
    }

    /** Queues a follow-up technique in {@code ticks}. */
    protected void queueFollowUp(int kind, int ticks) {
        this.followUpKind = kind;
        this.followUpTicks = ticks;
    }

    /** Recovery after the technique being used now: between {@code min} and {@code max} ticks. */
    protected void recover(int min, int max) {
        this.recovery = DuelRules.recovery(min, max, this.random.nextFloat());
    }

    protected void recoverLight() {
        this.recover(DuelRules.LIGHT_MIN, DuelRules.LIGHT_MAX);
    }

    protected void recoverMedium() {
        this.recover(DuelRules.MEDIUM_MIN, DuelRules.MEDIUM_MAX);
    }

    /**
     * Picks a technique by weight ({@code weights[id]}, 0 = not now), the one used last counting
     * less each time it is repeated; -1 if none applies. Call {@link #used} with what was cast.
     */
    protected int pickSkill(float... weights) {
        float[] w = weights.clone();
        if (this.lastSkill >= 0 && this.lastSkill < w.length) w[this.lastSkill] = DuelRules.repeatWeight(w[this.lastSkill], this.repeats);
        return DuelRules.pick(this.random.nextFloat(), w);
    }

    protected void used(int skill) {
        this.repeats = skill == this.lastSkill ? this.repeats + 1 : 1;
        this.lastSkill = skill;
    }

    /** Starts a hand-to-hand window. */
    private void startMelee(int ticks, boolean breakOff, boolean counter) {
        this.meleeTicks = ticks;
        this.breakOffAfter = breakOff;
        this.countering = counter;
        this.tactics.calm();
    }

    /**
     * Neither reachable on foot nor (where it matters) in sight, and not deep below (no leap or blink
     * out of a pit helps against a foe in a cave underneath).
     */
    private boolean cutOff(LivingEntity target, double distance) {
        if (distance < 3.0 || target.getY() < this.getY() - 3.0) return false;
        boolean above = target.getY() - this.getY() > 1.5;
        if (this.unseenTicks == 0 && !(above && !this.holdingRange())) return false;
        Path path = this.getNavigation().createPath(target, 1);
        return path == null || !path.canReach();
    }

    /** Takes up again a foe lost a moment ago (out of sight, or dropped while held by Unlimited Void). */
    private LivingEntity remembered(ServerLevel level) {
        LivingEntity foe = this.lastFoe;
        if (foe == null) return null;
        if (!foe.isAlive() || foe.isRemoved() || foe.level() != level || ignoredPlayer(foe) || !this.mayHurt(foe)
                || level.getGameTime() - this.lastFoeAt > MEMORY_TICKS || this.distanceToSqr(foe) > 48.0 * 48.0) {
            this.lastFoe = null;
            return null;
        }
        return foe;
    }

    /**
     * A new attacker took the target over: it keeps the foe it was fighting instead, unless the new
     * one really hurt it (or is Mahoraga), or the old one is gone or far off.
     */
    private LivingEntity keepFoe(ServerLevel level, LivingEntity target) {
        LivingEntity old = this.rangeRolledFor;
        if (target == null || old == null || target == old || target instanceof MahoragaEntity) return target;
        if (!old.isAlive() || old.isRemoved() || old.level() != level || ignoredPlayer(old) || !this.mayHurt(old) || this.distanceToSqr(old) > 32.0 * 32.0) {
            return target;
        }
        if (this.recentDamage.getOrDefault(target.getUUID(), 0.0f) >= SWITCH_DAMAGE) return target;
        this.setTarget(old);
        return old;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) return;
        this.upgradeLegacyHealth();
        if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
            boolean resting = this.getTarget() == null && this.tickCount - this.lastHurtAt > DuelRules.RESTING_AFTER;
            this.heal(DuelRules.regen(resting));
        }
        if (this.burstHealCooldown > 0) --this.burstHealCooldown;
        if (this.skillCooldown > 0) --this.skillCooldown;
        if (this.domainCooldown > 0) --this.domainCooldown;
        if (this.mobilityCooldown > 0) --this.mobilityCooldown;
        if (this.evadeCooldown > 0) --this.evadeCooldown;
        if (this.fallGrace > 0) {
            --this.fallGrace;
            this.fallDistance = 0.0;
        }
        if (level.getGameTime() - this.recentFrom > 60L) {
            this.recentDamage.clear();
            this.recentFrom = level.getGameTime();
        }
        if (VoidDomainEntity.stunned(this)) {
            this.rctTicks = 0;
            // Held still: at most (Sukuna) an attempt to open its own domain.
            LivingEntity foe = this.getTarget() != null ? this.getTarget() : this.remembered(level);
            if (this.canDomainWhileHeld() && this.wantsDomain(level, foe, foe == null ? 0.0 : this.distanceTo(foe)) && this.castDomain(level)) {
                this.domainCooldown = DOMAIN_COOLDOWN;
            }
            return;
        }
        if (this.counterCooldown > 0) --this.counterCooldown;
        if (this.counterDelay >= 0 && --this.counterDelay < 0) {
            LivingEntity foe = this.counterTarget;
            this.counterTarget = null;
            if (foe != null && foe.isAlive() && foe.level() == level) {
                this.getLookControl().setLookAt(foe, 90.0f, 90.0f);
                answering = true;
                try {
                    this.counterFinisher(level, foe);
                } finally {
                    answering = false;
                }
                this.counterCooldown = 600;
            }
        }
        if (this.dodgeDelay >= 0 && --this.dodgeDelay < 0 && this.dodgeFrom != null) {
            Vec3 from = this.position().subtract(this.dodgeFrom);
            Vec3 side = new Vec3(-from.z, 0.0, from.x);
            side = side.lengthSqr() < 1.0E-3 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            side = turned(side, (this.random.nextDouble() - 0.5) * 50.0);
            this.mobility(level, this.position().add(side.scale((this.random.nextBoolean() ? 1.0 : -1.0) * (5.0 + this.random.nextDouble() * 5.0))), null, true);
            this.dodgeFrom = null;
        }
        LivingEntity target = this.getTarget();
        if (target == null && (target = this.remembered(level)) != null) this.setTarget(target);
        target = this.keepFoe(level, target);
        boolean fighting = target != null && target.isAlive() && target.level() == this.level();
        if (!fighting) {
            this.combatTicks = 0;
            this.meleeTicks = 0;
            this.countering = false;
            this.unseenTicks = 0;
            this.followUpTicks = -1;
            target = null;
        } else {
            this.lastFoe = target;
            this.lastFoeAt = level.getGameTime();
            if (target != this.rangeRolledFor) {
                // A new foe: strong ones are fought from range between flurries; others seldom.
                boolean freshFight = this.combatTicks == 0;
                this.rangeRolledFor = target;
                this.preferRange = strong(target) || this.random.nextFloat() < 0.25f;
                this.breakOffAfter = false;
                this.countering = false;
                this.tactics.reset();
                if (freshFight) {
                    // The fight's own pace: no two sorcerers open their domain or finisher on the same beat.
                    this.domainCooldown = Math.max(this.domainCooldown, 120 + this.random.nextInt(240));
                    this.domainPatience = 240 + this.random.nextInt(360);
                    this.finisherPatience = 160 + this.random.nextInt(200);
                    this.meleeTicks = 0;
                }
            }
            ++this.combatTicks;
            this.unseenTicks = this.getSensing().hasLineOfSight(target) ? 0 : this.unseenTicks + 1;
        }
        double distance = fighting ? this.distanceTo(target) : 0.0;
        if (this.rctTicks > 0) {
            this.tickRct(level);
            return;
        }
        if (this.wantsRct(target, distance) && this.reverseCurse(level)) return;
        if (this.wantsDomain(level, target, distance) && this.castDomain(level)) {
            this.domainCooldown = DOMAIN_COOLDOWN;
            this.skillCooldown = Math.max(this.skillCooldown, 60);
            return;
        }
        if (!fighting) return;
        if (this.tickCount % 4 == 0) this.senseThreats(level, target, distance);
        if (this.followUpTicks >= 0 && --this.followUpTicks < 0 && this.unseenTicks == 0 && !NpcHooks.restrained(this)) {
            this.getLookControl().setLookAt(target, 90.0f, 90.0f);
            if (this.followUp(level, target, this.followUpKind, distance)) this.skillCooldown = Math.max(this.skillCooldown, 20);
        }
        this.rhythm(level, target, distance);
        this.movement(level, target, distance);
        if (this.skillCooldown <= 0 && this.unseenTicks == 0) {
            this.getLookControl().setLookAt(target, 60.0f, 60.0f);
            this.recovery = 0;
            // A hard fight's signature move comes first (Gojo: the fused Purple; Sukuna: Mahoraga).
            boolean special = this.hardFightMove(level, target, distance);
            boolean finisher = !special && this.wantsFinisherNow(target, distance) && this.tryFinisher(level, target);
            boolean used = special || finisher || this.useSkill(level, target, distance);
            // Light techniques come back sooner than heavy ones; the default is a medium recovery.
            this.skillCooldown = !used ? 20 + this.random.nextInt(20)
                : this.recovery > 0 ? this.recovery : DuelRules.recovery(DuelRules.MEDIUM_MIN, DuelRules.MEDIUM_MAX, this.random.nextFloat());
            // Against a strong foe: a technique from range, then often a blink / bound in for a flurry.
            if (used && !special && strong(target) && this.meleeTicks <= 0 && distance > 4.0 && this.random.nextFloat() < 0.45f) {
                if (this.gapClose(level, target)) this.startMelee(40 + this.random.nextInt(40), true, false);
            }
        }
    }

    /**
     * Reads what comes at it (every 4 ticks): a foe charging a great blow at it is rushed (to break
     * the charge) or slipped; a projectile about to hit is sidestepped now and then (the Gojo NPC
     * leaves projectiles to Infinity).
     */
    private void senseThreats(ServerLevel level, LivingEntity target, double distance) {
        if (this.evadeCooldown > 0 || NpcHooks.restrained(this)) return;
        if (channeling(target) && !(target instanceof JjkNpcEntity) && this.threatenedBy(target) && target.getTicksUsingItem() > 15
                && this.random.nextFloat() < 0.25f) {
            if (distance < 16.0 && this.random.nextBoolean() && this.gapClose(level, target)) {
                this.startMelee(50, false, false);
                this.evadeCooldown = 40;
            } else {
                this.evade(level, target.position());
            }
            return;
        }
        if (Infinity.protects(this)) return;
        Vec3 me = this.getBoundingBox().getCenter();
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, this.getBoundingBox().inflate(10.0))) {
            Entity owner = p.getOwner();
            if (owner == this || owner instanceof LivingEntity o && o != target && !this.mayHurt(o)) continue;
            Vec3 v = p.getDeltaMovement();
            double vv = v.lengthSqr();
            if (vv < 0.04) continue;
            Vec3 rel = me.subtract(p.position());
            double t = rel.dot(v) / vv;
            if (t < 0.0 || t > 12.0 || rel.subtract(v.scale(t)).lengthSqr() > 1.8 * 1.8) continue;
            if (this.random.nextFloat() < PROJECTILE_DODGE_CHANCE) this.evade(level, p.position());
            return;
        }
    }

    /**
     * Range and hand-to-hand. A window runs out (breaking away if it was a flurry); at range, a foe
     * that closes in is broken away from once, and fought where it stands if it keeps chasing; and
     * while the next technique is still a while off, it now and then goes in for a flurry itself.
     */
    private void rhythm(ServerLevel level, LivingEntity target, double distance) {
        if (this.meleeTicks > 0) {
            if (this.countering && distance > NpcTactics.COUNTER_DROP) this.meleeTicks = 1;
            if (--this.meleeTicks <= 0) {
                this.countering = false;
                if (this.breakOffAfter) {
                    this.breakOffAfter = false;
                    this.disengage(level, target);
                }
            }
            return;
        }
        if (!this.holdingRange()) return;
        boolean hurt = this.getHealth() < this.getMaxHealth() * 0.3f;
        NpcTactics.Reaction r = this.tactics.pressed(distance < NpcTactics.CLOSE, this.mobilityCooldown <= 0, level.getGameTime());
        if (r == NpcTactics.Reaction.COUNTER && !(hurt && this.mobilityCooldown <= 0)) {
            this.startMelee(NpcTactics.COUNTER_TICKS, false, true);
            return;
        }
        if (r != NpcTactics.Reaction.NONE) {
            this.disengage(level, target);
            return;
        }
        if (--this.meleeRoll > 0) return;
        this.meleeRoll = 15 + this.random.nextInt(30);
        if (strong(target) && this.skillCooldown > 30 && this.random.nextFloat() < 0.35f) {
            this.startMelee(40 + this.random.nextInt(40), true, false);
            if (distance > 6.0) this.gapClose(level, target);
        }
    }

    /**
     * The mobility in a fight: out of a pit or from under cover when the foe cannot be reached,
     * away when badly hurt and pressed (to make room for the heal), after a foe that is far off or hidden.
     */
    private void movement(ServerLevel level, LivingEntity target, double distance) {
        if (this.tickCount % 10 == 0) {
            Vec3 here = this.position();
            double moved = this.lastCheckPos == null ? 0.0 : here.distanceTo(this.lastCheckPos);
            this.lastCheckPos = here;
            boolean blocked = this.cutOff(target, distance);
            if (this.tactics.stuck(blocked, distance, moved, this.mobilityCooldown <= 0) && this.escapeUp(level, target)) return;
        }
        if (this.mobilityCooldown > 0) return;
        float low = this.burstHealCooldown <= 0 ? DuelRules.RCT_BELOW : 0.3f;
        if (this.getHealth() < this.getMaxHealth() * low && distance < 5.0 && !this.countering && (this.preferRange || this.burstHealCooldown <= 0)) {
            this.meleeTicks = 0;
            this.breakOffAfter = false;
            this.countering = false;
            this.disengage(level, target);
            return;
        }
        if (this.tickCount % 10 != 0) return;
        if (!this.holdingRange()) {
            // Hand to hand: a foe far off is closed on at once.
            if (distance > 12.0 && (distance > 18.0 || this.random.nextFloat() < 0.5f)) this.gapClose(level, target);
        } else if (distance > 30.0 || this.unseenTicks > 60 && distance > 6.0) {
            // At range: a foe that ran far off or hides behind cover is gone after, and flushed out.
            if (this.gapClose(level, target)) this.startMelee(40 + this.random.nextInt(40), true, false);
        }
    }

    /**
     * At range: keeps a band (rolled each time, about 8-18 blocks) and circles the foe sideways
     * instead of standing still; walks toward a foe it cannot see.
     */
    private final class RangeMoveGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int strafeTicks;
        private boolean clockwise;
        private double near = 9.0;
        private double wide = 18.0;

        private RangeMoveGoal() {
            this.setFlags(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return JjkNpcEntity.this.holdingRange() && !VoidDomainEntity.stunned(JjkNpcEntity.this) && !JjkNpcEntity.this.channellingRct();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.near = 7.0 + JjkNpcEntity.this.random.nextDouble() * 3.0;
            this.wide = 15.0 + JjkNpcEntity.this.random.nextDouble() * 4.0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            JjkNpcEntity self = JjkNpcEntity.this;
            LivingEntity target = self.getTarget();
            if (target == null) return;
            self.getLookControl().setLookAt(target, 30.0f, 30.0f);
            double d = self.distanceTo(target);
            double near = this.near, wide = this.wide;
            if (self.unseenTicks > 10) {
                self.setXxa(0.0f);
                if (self.getNavigation().isDone() || self.tickCount % 10 == 0) self.getNavigation().moveTo(target, 1.15);
            } else if (d < near) {
                if (self.getNavigation().isDone()) {
                    Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(self, 16, 7, target.position());
                    if (away != null) {
                        self.setXxa(0.0f);
                        self.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                    } else {
                        // Cornered: back off sideways.
                        self.lookAt(target, 30.0f, 30.0f);
                        self.getMoveControl().strafe(-0.5f, this.clockwise ? 0.5f : -0.5f);
                    }
                }
            } else if (d > wide) {
                self.setXxa(0.0f);
                self.getNavigation().moveTo(target, 1.1);
            } else {
                self.getNavigation().stop();
                if (--this.strafeTicks <= 0) {
                    this.strafeTicks = 15 + self.random.nextInt(40);
                    this.clockwise = self.random.nextBoolean();
                }
                float forward = d < near + 2.0 ? -0.4f : d > wide - 2.0 ? 0.4f : 0.0f;
                self.lookAt(target, 30.0f, 30.0f);
                self.getMoveControl().strafe(forward, this.clockwise ? 0.6f : -0.6f);
            }
        }

        @Override
        public void stop() {
            JjkNpcEntity.this.getNavigation().stop();
            JjkNpcEntity.this.setXxa(0.0f);
            JjkNpcEntity.this.setZza(0.0f);
        }
    }

    /**
     * Hand to hand, fought on the move: paths in from afar, then circles the foe 1.6-2.6 blocks off
     * (changing direction now and then), steps in to strike, and after a string of two or three
     * blows steps back a moment before going in again.
     */
    private final class CloseFightGoal extends MeleeAttackGoal {
        private int ready;
        private int hits;
        private int backstep;
        private int circleTicks;
        private boolean clockwise;

        private CloseFightGoal() {
            super(JjkNpcEntity.this, 1.3, true);
        }

        @Override
        public boolean canUse() {
            return !JjkNpcEntity.this.holdingRange() && !JjkNpcEntity.this.channellingRct() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !JjkNpcEntity.this.holdingRange() && !JjkNpcEntity.this.channellingRct() && super.canContinueToUse();
        }

        @Override
        public void start() {
            super.start();
            this.ready = 4 + JjkNpcEntity.this.random.nextInt(8);
            this.hits = 0;
            this.backstep = 0;
        }

        @Override
        public void tick() {
            JjkNpcEntity self = JjkNpcEntity.this;
            LivingEntity target = self.getTarget();
            if (target == null) return;
            if (this.ready > 0) --this.ready;
            double d = self.distanceTo(target);
            if (d > 3.6 || !self.getSensing().hasLineOfSight(target)) {
                super.tick();
                return;
            }
            self.getNavigation().stop();
            self.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (--this.circleTicks <= 0) {
                this.circleTicks = 15 + self.random.nextInt(20);
                this.clockwise = self.random.nextBoolean();
            }
            float forward;
            if (this.backstep > 0) {
                --this.backstep;
                forward = -0.9f;
            } else {
                forward = d > 2.6 ? 0.7f : d < 1.6 ? -0.4f : 0.15f;
            }
            self.lookAt(target, 30.0f, 30.0f);
            self.getMoveControl().strafe(forward, this.clockwise ? 0.5f : -0.5f);
            if (this.backstep <= 0) this.checkAndPerformAttack(target);
        }

        @Override
        protected boolean isTimeToAttack() {
            return this.ready <= 0;
        }

        @Override
        protected int getTicksUntilNextAttack() {
            return this.ready;
        }

        @Override
        protected void resetAttackCooldown() {
            this.ready = 12 + JjkNpcEntity.this.random.nextInt(10);
            if (++this.hits >= 2 + JjkNpcEntity.this.random.nextInt(2)) {
                this.hits = 0;
                this.backstep = 8 + JjkNpcEntity.this.random.nextInt(6);
                this.ready += 6;
            }
        }
    }

    /**
     * Senses a strong foe (a player, a sorcerer, anything with 60+ health) through walls within 16
     * blocks when none is in sight: cursed energy, not eyes. Small fry out of sight are left alone.
     */
    private final class SenseFoeGoal extends NearestAttackableTargetGoal<LivingEntity> {
        private final TargetingConditions sensed = TargetingConditions.forCombat().range(SENSE_RANGE).ignoreLineOfSight()
            .selector((target, level) -> strong(target) && JjkNpcEntity.this.canHarm(target));

        private SenseFoeGoal() {
            super(JjkNpcEntity.this, LivingEntity.class, 20, false, false, null);
        }

        @Override
        protected void findTarget() {
            ServerLevel level = (ServerLevel)this.mob.level();
            LivingEntity best = null;
            double nearest = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.mob.getBoundingBox().inflate(SENSE_RANGE, 8.0, SENSE_RANGE))) {
                double d = this.mob.distanceToSqr(e);
                if (d < nearest && this.sensed.test(level, this.mob, e)) {
                    best = e;
                    nearest = d;
                }
            }
            this.target = best;
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (VoidDomainEntity.stunned(this) || this.channellingRct() || !(target instanceof LivingEntity living)) {
            return false;
        }
        float mult = BlackFlash.npcStrike(this, living);
        float vs = DamageTaken.npcVs(this, living);
        AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            if (mult > 1.0f) attack.addOrUpdateTransientModifier(new AttributeModifier(FLASH_BOOST, mult - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (vs != 1.0f) attack.addOrUpdateTransientModifier(new AttributeModifier(VS_BOOST, vs - 1.0f, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        try {
            return super.doHurtTarget(level, target);
        } finally {
            if (attack != null) {
                attack.removeModifier(FLASH_BOOST);
                attack.removeModifier(VS_BOOST);
            }
        }
    }

    /** Health lost to one blow from another NPC is capped (/kill and the void excepted); players hit as before. */
    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float damage) {
        float d = super.getDamageAfterMagicAbsorb(source, damage);
        return DamageTaken.npcBlow(source) ? DuelRules.capped(d, this.getMaxHealth()) : d;
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        float before = this.getHealth();
        super.actuallyHurt(level, source, amount);
        float lost = before - this.getHealth();
        DomainClash.recordDamage(this, lost);
        if (lost <= 0.0f) return;
        this.lastHurtAt = this.tickCount;
        if (source.getEntity() != null) this.recentDamage.merge(source.getEntity().getUUID(), lost, Float::sum);
        if (this.rctTicks > 0 && (this.rctTaken += lost) >= DuelRules.RCT_BREAK) {
            // The heal is broken off: tried again a little later.
            this.rctTicks = 0;
            this.burstHealCooldown = DuelRules.RCT_INTERRUPTED_COOLDOWN;
            CurseFx.particles(level, ParticleTypes.SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean causedByPlayer) {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean allowDrops) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }
}
