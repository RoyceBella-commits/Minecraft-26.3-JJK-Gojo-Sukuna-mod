package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash;
import cn.blockforge.ryomensukuna.m2a542fea.entity.TrainingDummyEntity;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Growth;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class CurseManager {
    public static final AttachmentType<CurseState> CURSE = AttachmentRegistry.<CurseState>builder().persistent(CurseState.CODEC).copyOnDeath().initializer(CurseState::fresh).buildAndRegister(SukunaMod.id("curse"));
    public static final float BURNOUT_SECONDS = 8.0f;
    private static final Map<UUID, float[]> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SYNC_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BURNOUT_UNTIL = new ConcurrentHashMap<>();

    private CurseManager() {
    }

    public static CurseState of(ServerPlayer player) {
        CurseState s = player.getAttached(CURSE);
        return s == null ? CurseState.fresh() : s;
    }

    public static void setState(ServerPlayer player, CurseState state) {
        player.setAttached(CURSE, state);
    }

    public static float energy(ServerPlayer player) {
        return CurseManager.of(player).energy();
    }

    public static float maxEnergy(ServerPlayer player) {
        return CurseManager.of(player).maxEnergy();
    }

    public static boolean unlocked(ServerPlayer player, Skill skill) {
        return CurseManager.of(player).unlocked(skill);
    }

    /** Six Eyes makes every Gojo expense cheaper; stage V removes the expense entirely. */
    public static float effectiveCost(CurseState s, float cost) {
        if (s.infiniteEnergy()) return 0.0f;
        return s.route() == StageRules.GOJO ? cost * 0.8f : cost;
    }

    public static boolean canAfford(ServerPlayer player, float cost) {
        CurseState s = CurseManager.of(player);
        return s.energy() + 0.001f >= effectiveCost(s, cost);
    }

    public static boolean spend(ServerPlayer player, float cost) {
        CurseState s = CurseManager.of(player);
        float real = effectiveCost(s, cost);
        if (real <= 0.0f) {
            return true;
        }
        if (s.energy() + 0.001f < real) {
            return false;
        }
        CurseManager.setState(player, s.withEnergy(s.energy() - real));
        return true;
    }

    public static void addEnergy(ServerPlayer player, float amount) {
        CurseState s = CurseManager.of(player);
        CurseManager.setState(player, s.withEnergy(s.energy() + amount));
    }

    public static void tickRegen(ServerPlayer player) {
        CurseState s = CurseManager.of(player);
        if (s.awakened()) {
            float rate = 2.2f + (float)s.stage() * 0.5f;
            float healed;
            if (s.energy() < s.maxEnergy() && !ChannelCasting.active(player) && !DomainSkill.active(player)
                && (healed = Math.min(s.maxEnergy(), s.energy() + rate * 0.2f)) - s.energy() > 1.0E-4f) {
                CurseManager.setState(player, s.withEnergy(healed));
            }
        }
        int t = SYNC_TICKS.merge(player.getUUID(), 1, Integer::sum);
        if (t >= 5) {
            SYNC_TICKS.put(player.getUUID(), 0);
            CurseManager.sync(player);
        }
    }

    public static void tickCooldowns(ServerPlayer player) {
        float[] cds = COOLDOWNS.get(player.getUUID());
        if (cds == null) {
            return;
        }
        boolean any = false;
        for (int i = 0; i < cds.length; ++i) {
            if (!(cds[i] > 0.0f)) continue;
            cds[i] = Math.max(0.0f, cds[i] - 0.05f);
            any = true;
        }
        if (!any) {
            COOLDOWNS.remove(player.getUUID());
        }
    }

    public static void setCooldown(ServerPlayer player, Skill skill, float seconds) {
        COOLDOWNS.computeIfAbsent(player.getUUID(), key -> new float[Skill.values().length])[skill.netId] = seconds;
        CurseManager.sync(player);
    }

    public static float cooldownLeft(ServerPlayer player, Skill skill) {
        float[] cds = COOLDOWNS.get(player.getUUID());
        return cds == null ? 0.0f : cds[skill.netId];
    }

    public static void startBurnout(ServerPlayer player) {
        BURNOUT_UNTIL.put(player.getUUID(), player.level().getGameTime() + (long)(BURNOUT_SECONDS * 20.0f));
        Infinity.onBurnoutStart(player);
        SukunaNet.actionBar(player, "sukuna.hint.burnout");
        CurseManager.sync(player);
    }

    public static float burnoutLeft(ServerPlayer player) {
        Long until = BURNOUT_UNTIL.get(player.getUUID());
        if (until == null) return 0.0f;
        long left = until - player.level().getGameTime();
        if (left <= 0L) {
            BURNOUT_UNTIL.remove(player.getUUID());
            Infinity.onBurnoutEnd(player);
            return 0.0f;
        }
        return left / 20.0f;
    }

    public static boolean burntOut(ServerPlayer player) {
        return burnoutLeft(player) > 0.0f;
    }

    public static void sync(ServerPlayer player) {
        CurseState s = CurseManager.of(player);
        Skill sel = Skill.byId(s.selected());
        SukunaNet.sendState(player, s, sel == null ? 0.0f : CurseManager.cooldownLeft(player, sel), burnoutLeft(player),
            Mobility.cooldownLeft(player, Mobility.DASH), Mobility.cooldownLeft(player, Mobility.LEAP), Infinity.active(player));
    }

    public static void onDeath(ServerPlayer player) {
        CurseState s = CurseManager.of(player);
        CurseManager.setState(player, s.withEnergy(0.0f));
        COOLDOWNS.remove(player.getUUID());
        BURNOUT_UNTIL.remove(player.getUUID());
        ChannelCasting.end(player);
        CurseManager.sync(player);
    }

    public static void clearTransient() {
        COOLDOWNS.clear();
        SYNC_TICKS.clear();
        BURNOUT_UNTIL.clear();
        ChannelCasting.clear();
        CombatSkills.clear();
        TerrainCuts.clear();
        Progression.clear();
        Mobility.clear();
        Infinity.clear();
        Growth.clear();
        DomainClash.clear();
        cn.blockforge.ryomensukuna.m2a542fea.combat.BlackFlash.clear();
    }

    /** Owning player of a technique source, or null for mobs, summons and the environment. */
    public static ServerPlayer casterOf(Entity attacker) {
        return attacker instanceof ServerPlayer sp ? sp : null;
    }

    /** Teammates, own pets and (when PvP is off) other players are never hurt by techniques. */
    public static boolean protectedTarget(Entity attacker, LivingEntity target) {
        if (attacker == null) return false;
        if (target == attacker) return true;
        if (attacker instanceof cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity npc) return !npc.mayHurt(target);
        if (target.isAlliedTo(attacker)) return true;
        if (target instanceof OwnableEntity pet && pet.getOwner() == attacker) return true;
        if (target instanceof Player victim && attacker instanceof ServerPlayer sp && !sp.canHarmPlayer(victim)) return true;
        return false;
    }

    public static void curseDamage(Level world, Entity attacker, LivingEntity target, float amount) {
        ServerPlayer caster = casterOf(attacker);
        CurseManager.curseDamage(world, attacker, target, amount, caster == null ? -1 : Progression.latestCast(caster));
    }

    /** Technique damage of a known kind (slash / flame / fist) for the Infinity-breaking rule. */
    public static void curseDamage(Level world, Entity attacker, LivingEntity target, float amount, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category kind) {
        ServerPlayer caster = casterOf(attacker);
        CurseManager.damage(world, attacker, target, amount, SukunaDamage.cursed(world, attacker), caster == null ? -1 : Progression.latestCast(caster), kind);
    }

    public static boolean damage(Level world, Entity attacker, LivingEntity target, float amount, DamageSource source, int castId, cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach.Category kind) {
        var previous = cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity.pending;
        cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity.pending = kind;
        try {
            return CurseManager.damage(world, attacker, target, amount, source, castId);
        } finally {
            cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity.pending = previous;
        }
    }

    /** Gojo / Sukuna sorcerers: awakened players, the technique NPCs and Mahoraga (Sukuna's shikigami). */
    public static boolean isSorcerer(Entity e) {
        if (e instanceof ServerPlayer sp) return CurseManager.of(sp).awakened();
        return e instanceof cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity || e instanceof cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
    }

    public static void curseDamage(Level world, Entity attacker, LivingEntity target, float amount, int castId) {
        CurseManager.damage(world, attacker, target, amount, SukunaDamage.cursed(world, attacker), castId);
    }

    /** Central technique damage: ally protection, practice credit and kill sound. */
    public static boolean damage(Level world, Entity attacker, LivingEntity target, float amount, DamageSource source, int castId) {
        if (target.isInvulnerable() || target.isRemoved() || protectedTarget(attacker, target)) {
            return false;
        }
        boolean hurt = world instanceof net.minecraft.server.level.ServerLevel sl && target.hurtServer(sl, source, amount);
        target.setInvulnerableTime(0);
        ServerPlayer caster = casterOf(attacker);
        if ((hurt || target instanceof TrainingDummyEntity) && caster != null) {
            Progression.creditHit(caster, castId);
        }
        if (target.getHealth() <= 0.0f) {
            world.playSound(null, target.getX(), target.getY(), target.getZ(), SukunaSounds.SLASH2, target.getSoundSource(), 1.0f, 0.8f);
        }
        return hurt;
    }
}
