package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

import cn.blockforge.ryomensukuna.m2a542fea.combat.DuelRules;
import cn.blockforge.ryomensukuna.m2a542fea.entity.CurseSlashEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaSkill;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CombatSkills;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ryomen Sukuna (spawn egg): attacks every living thing except players who chose the Sukuna route
 * (and other Sukuna / Mahoraga). Uses Dismantle (now and then charged into the grid), Cleave, the
 * barrage, Flame Arrow, World Cut and Malevolent Shrine, and strings them: a Dismantle followed by
 * a bound in and a Cleave, a barrage finished with a Cleave. Calls Mahoraga readily (and then bears
 * its wheel) unless the last one was destroyed within 5 minutes. Moves with great bounds.
 */
public class SukunaNpcEntity extends JjkNpcEntity implements Enemy {
    private int worldCutCooldown = 400;
    /** Techniques for {@link #pickSkill}, and the follow-ups. */
    private static final int BARRAGE = 0, CLEAVE = 1, DISMANTLE = 2, GRID = 3, RED = 4, FISTS = 5;
    private static final int FOLLOW_CLEAVE = 1, FOLLOW_BOUND = 2;
    private static final double RED_SPEED = 3.7;
    private static final double WORLD_CUT_SPEED = 4.5;
    /** Against a strong foe Mahoraga comes out after 5 s of fighting (or once hurt below 60%). */
    private static final int MAHORAGA_STRONG_TICKS = 100;
    /** Against anything, after 15 s without a win. */
    private static final int MAHORAGA_ANY_TICKS = 300;

    public SukunaNpcEntity(EntityType<? extends SukunaNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean canHarm(LivingEntity t) {
        if (t == this || !t.isAlive() || ignoredPlayer(t)) return false;
        if (t instanceof SukunaNpcEntity || t instanceof MahoragaEntity || t instanceof ArmorStand) return false;
        return !sukunaPlayer(t);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.worldCutCooldown > 0 && !this.level().isClientSide()) --this.worldCutCooldown;
        // The summon cooldown only runs while no Mahoraga of its own is out.
    }

    @Override
    protected boolean useSkill(ServerLevel level, LivingEntity target, double distance) {
        boolean close = distance < 4.0;
        boolean open = this.targetOpen(target);
        // Infinity swallows the Flame Arrow; the cuts wear it down instead.
        float flameUse = Infinity.protects(target) ? 0.2f : 1.0f;
        float barrage = close ? 0.4f : 0.0f;
        float cleave = close ? 0.3f : distance < 16.0 ? 0.4f : 0.0f;
        float dismantle = close ? 0.0f : distance < 16.0 ? 0.45f : 0.6f;
        float grid = distance >= 6.0 && distance < 18.0 && strong(target) ? 0.15f : 0.0f;
        float red = distance >= 10.0 ? (open ? 0.5f : 0.25f) * flameUse : 0.0f;
        float fists = close ? 0.3f : 0.0f;
        int skill = this.pickSkill(barrage, cleave, dismantle, grid, red, fists);
        switch (skill) {
            case BARRAGE -> {
                CombatSkills.fireCursedBarrage(this, 1.5f);
                this.recoverLight();
                if (this.random.nextFloat() < 0.4f) this.queueFollowUp(FOLLOW_CLEAVE, 10 + this.random.nextInt(6));
            }
            case CLEAVE -> {
                CombatSkills.fireCleaveOn(this, target, close ? 1.0f : 1.2f);
                this.recoverMedium();
            }
            case DISMANTLE -> {
                CombatSkills.fireKai(this, this.leadAim(target, CurseSlashEntity.SPEED), 1.0f);
                this.recoverLight();
                if (distance < 14.0 && this.random.nextFloat() < 0.35f) this.queueFollowUp(FOLLOW_BOUND, 6 + this.random.nextInt(6));
            }
            case GRID -> {
                // Charged past 1.5: the cut opens into a grid.
                CombatSkills.fireKai(this, this.leadAim(target, CurseSlashEntity.SPEED), 1.6f);
                this.recoverMedium();
            }
            case RED -> {
                CombatSkills.fireRed(this, this.leadAim(target, RED_SPEED), 1.5f);
                this.recoverMedium();
            }
            default -> {
                this.used(FISTS);
                return false;
            }
        }
        this.used(skill);
        return true;
    }

    @Override
    protected boolean followUp(ServerLevel level, LivingEntity target, int kind, double distance) {
        if (kind == FOLLOW_CLEAVE && distance < 6.0) {
            CombatSkills.fireCleaveOn(this, target, 1.0f);
            return true;
        }
        if (kind == FOLLOW_BOUND && distance > 4.0 && this.gapClose(level, target)) {
            this.queueFollowUp(FOLLOW_CLEAVE, 6 + this.random.nextInt(5));
            return true;
        }
        return false;
    }

    /**
     * Mahoraga is called up from the shadow early: 5 s into a fight with a strong foe (or once hurt
     * below 60%), 15 s into any fight. Never while one is out or within 5 minutes of losing one.
     */
    @Override
    protected boolean hardFightMove(ServerLevel level, LivingEntity target, double distance) {
        boolean pressed = strong(target) && (this.combatTicks > MAHORAGA_STRONG_TICKS || this.getHealth() < this.getMaxHealth() * 0.6f);
        if (!pressed && this.combatTicks <= MAHORAGA_ANY_TICKS) return false;
        if (MahoragaSkill.deathLockSeconds(this) > 0.0f || this.mahoraga() != null) return false;
        Vec3 side = this.getViewVector(1.0f).cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-3 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 anchor = Mobility.safeSpot(this, this.position().add(side.scale(3.0)), side);
        MahoragaSkill.summonFor(this, anchor != null ? anchor : this.position(), null);
        return true;
    }

    private MahoragaEntity mahoraga() {
        String id = this.getStringUUID();
        return this.level().getEntitiesOfClass(MahoragaEntity.class, this.getBoundingBox().inflate(256.0),
            m -> m.isAlive() && id.equals(m.ownerUuid())).stream().findFirst().orElse(null);
    }

    @Override
    protected boolean castDomain(ServerLevel level) {
        return DomainSkill.castFor(this, StageRules.DOMAIN_TICKS_MAX, -1);
    }

    @Override
    protected boolean canDomainWhileHeld() {
        return true;
    }

    @Override
    protected boolean domainActive() {
        return DomainSkill.active(this);
    }

    @Override
    protected boolean tryFinisher(ServerLevel level, LivingEntity target) {
        if (this.worldCutCooldown > 0) return false;
        CombatSkills.fireWorldCut(this, this.leadAim(target, WORLD_CUT_SPEED), 2.0f);
        this.worldCutCooldown = DuelRules.FINISHER_COOLDOWN;
        return true;
    }

    @Override
    protected int finisherWait() {
        return this.worldCutCooldown;
    }

    @Override
    protected void counterFinisher(ServerLevel level, LivingEntity target) {
        CombatSkills.fireWorldCut(this, this.aimAt(target), 2.0f);
        this.worldCutCooldown = DuelRules.FINISHER_COOLDOWN;
    }

    @Override
    protected boolean answers(boolean worldCut) {
        return !worldCut;
    }

    /**
     * A great bound from the ground that lands at the spot (a low, quick one for a sidestep). Needs
     * room overhead for the arc; under a low ceiling only a flat bound to a spot no higher is made.
     */
    @Override
    protected boolean dashTo(ServerLevel level, Vec3 spot, LivingEntity facing, boolean low) {
        if (!this.onGround() && !this.isInWater()) return false;
        Vec3 to = spot.subtract(this.position());
        if (to.horizontalDistanceSqr() < 1.0 && to.y < 0.5) return false;
        int room = this.headroom(14);
        double[] v = NpcTactics.leap(to.x, to.y, to.z, low ? 0.6 : 2.0);
        if (NpcTactics.apex(v[1]) > room + 0.5) {
            if (to.y > 0.5 || room < 1) return false;
            v = NpcTactics.leap(to.x, to.y, to.z, 0.6);
            if (NpcTactics.apex(v[1]) > room + 0.5) return false;
        }
        this.setDeltaMovement(v[0], v[1], v[2]);
        this.needsSync = true;
        this.fallDistance = 0.0;
        this.getNavigation().stop();
        if (facing != null) this.getLookControl().setLookAt(facing, 90.0f, 90.0f);
        Vec3 feet = this.position();
        level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, low ? 10 : 18, 0.5, 0.05, 0.5, 0.08);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0f, 0.5f);
        return true;
    }

    /**
     * Out of a pit with a bound; walled in under a ceiling with no room for one, it bursts out of the
     * ground in front of the foe instead.
     */
    @Override
    protected boolean escapeUp(ServerLevel level, LivingEntity target) {
        if (super.escapeUp(level, target)) return true;
        if (!this.onGround() && !this.isInWater()) return false;
        Vec3 back = this.fromFoe(target);
        Vec3 spot = this.landing(target.position().add(back.scale(2.5)), back.scale(-1.0));
        if (spot == null) return false;
        Vec3 from = this.position();
        this.burstFx(level, from);
        this.teleportTo(spot.x, spot.y, spot.z);
        this.getNavigation().stop();
        this.getLookControl().setLookAt(target, 90.0f, 90.0f);
        this.burstFx(level, spot);
        this.moved();
        return true;
    }

    private void burstFx(ServerLevel level, Vec3 at) {
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.3, at.z, 40, 0.8, 0.4, 0.8, 0.15);
        }
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 24, 0.8, 0.1, 0.8, 0.08);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.2f, 0.6f);
    }
}
