package cn.blockforge.ryomensukuna.m2a542fea.progression;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Growth items, practice hits and stage advancement shared by both routes. */
public final class Progression {
    private static final Map<UUID, Integer> CAST_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CREDITED = new ConcurrentHashMap<>();

    private Progression() {
    }

    public static void clear() {
        CAST_IDS.clear();
        CREDITED.clear();
    }

    /** A new active technique use; hits are credited at most once per cast id. */
    public static int beginCast(ServerPlayer player) {
        return CAST_IDS.merge(player.getUUID(), 1, Integer::sum);
    }

    public static int latestCast(ServerPlayer player) {
        return CAST_IDS.getOrDefault(player.getUUID(), 0);
    }

    public static void creditHit(ServerPlayer player, int castId) {
        if (castId <= 0) return;
        Integer previous = CREDITED.get(player.getUUID());
        if (previous != null && previous >= castId) return;
        CREDITED.put(player.getUUID(), castId);
        CurseState s = CurseManager.of(player);
        if (!s.awakened()) return;
        boolean wasDone = s.practiceDone();
        CurseState after = s.withHit();
        CurseManager.setState(player, after);
        if (!wasDone && after.practiceDone() && after.stage() < StageRules.MAX_STAGE) {
            SukunaNet.actionBar(player, "sukuna.hint.practice_done");
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
    }

    /** Records that the player successfully used an active skill (practice for stages III–V). */
    public static void recordUse(ServerPlayer player, Skill skill) {
        CurseState s = CurseManager.of(player).withUsed(skill);
        if (skill.isHeavy()) s = s.withFlag(CurseState.FLAG_HEAVY, true);
        CurseManager.setState(player, s);
    }

    public static void recordDomainExpanded(ServerPlayer player) {
        CurseState s = CurseManager.of(player);
        if (!s.flag(CurseState.FLAG_DOMAIN)) {
            CurseManager.setState(player, s.withFlag(CurseState.FLAG_DOMAIN, true));
        }
    }

    public static boolean canUse(ServerPlayer player, int itemRoute) {
        CurseState s = CurseManager.of(player);
        StageRules.UseResult r = StageRules.evaluateUse(s.route(), itemRoute, s.stage(), s.practiceDone());
        return r == StageRules.UseResult.AWAKENED || r == StageRules.UseResult.ADVANCED;
    }

    /** Explains why a growth item cannot be used; nothing is consumed. */
    public static void explainRefusal(ServerPlayer player, int itemRoute) {
        CurseState s = CurseManager.of(player);
        switch (StageRules.evaluateUse(s.route(), itemRoute, s.stage(), s.practiceDone())) {
            case WRONG_ROUTE -> SukunaNet.actionBar(player, "sukuna.hint.wrong_route");
            case MAXED -> SukunaNet.actionBar(player, "sukuna.hint.max_stage");
            case NOT_READY -> SukunaNet.actionBar(player, "sukuna.hint.not_ready." + s.stage());
            default -> { }
        }
    }

    /** Applies a successful growth-item use. Returns false (and consumes nothing) when not allowed. */
    public static boolean useGrowthItem(ServerPlayer player, int itemRoute) {
        CurseState s = CurseManager.of(player);
        StageRules.UseResult result = StageRules.evaluateUse(s.route(), itemRoute, s.stage(), s.practiceDone());
        if (result != StageRules.UseResult.AWAKENED && result != StageRules.UseResult.ADVANCED) {
            explainRefusal(player, itemRoute);
            CurseManager.sync(player);
            return false;
        }
        CurseState after = applyUpgrade(player, s, itemRoute);
        celebrate(player, itemRoute, result == StageRules.UseResult.AWAKENED, s, after);
        CurseManager.sync(player);
        return true;
    }

    /** Raises the player one stage on the given route (awakening them when they have no route). */
    private static CurseState applyUpgrade(ServerPlayer player, CurseState s, int route) {
        boolean awakening = s.route() == StageRules.NONE || s.stage() <= 0;
        int oldStage = awakening ? 0 : s.stage();
        int newStage = oldStage + 1;
        float gold = StageRules.goldAfterUpgrade(s.goldHp(), oldStage, newStage);
        CurseState after = s.withProgress(route, newStage, gold);
        if (route == StageRules.SUKUNA) {
            after = after.withFingers(after.fingers() + 1);
        }
        if (awakening) {
            after = after.withSelected((route == StageRules.GOJO ? Skill.AO : Skill.KAI).netId).withEnergy(after.maxEnergy());
            if (route == StageRules.GOJO) after = after.withFlag(CurseState.FLAG_INFINITY, true);
        }
        CurseManager.setState(player, after);
        Growth.applyAttributes(player);
        return after;
    }

    /**
     * Admin / single-player shortcut: raises the player to {@code targetStage} on the given route
     * without any practice. Returns false (explaining why) when the route differs or the stage is maxed.
     */
    public static boolean forceAdvance(ServerPlayer player, int route, int targetStage) {
        CurseState before = CurseManager.of(player);
        boolean awakening = before.route() == StageRules.NONE || before.stage() <= 0;
        if (!awakening && before.route() != route) {
            SukunaNet.actionBar(player, "sukuna.hint.wrong_route");
            return false;
        }
        if (!awakening && before.stage() >= StageRules.MAX_STAGE) {
            SukunaNet.actionBar(player, "sukuna.hint.max_stage");
            return false;
        }
        int target = Math.min(StageRules.MAX_STAGE, targetStage);
        CurseState s = before;
        do {
            s = applyUpgrade(player, s, route);
        } while (s.stage() < target);
        celebrate(player, route, awakening, before, s);
        CurseManager.sync(player);
        return true;
    }

    private static void celebrate(ServerPlayer player, int route, boolean awakening, CurseState before, CurseState after) {
        ServerLevel level = player.level();
        boolean gojo = route == StageRules.GOJO;
        String title = awakening ? (gojo ? "sukuna.awaken.gojo" : "sukuna.awaken.sukuna") : "sukuna.stage.up";
        SukunaNet.titleComponent(player,
            Component.translatable(title).withStyle(gojo ? ChatFormatting.AQUA : ChatFormatting.RED),
            Component.translatable("sukuna.stage.name." + after.stage(), StageRules.roman(after.stage())));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SukunaSounds.UNLOCK, SoundSource.PLAYERS, 1.2f, gojo ? 1.35f : 0.9f);
        if (gojo) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        }
        double cx = player.getX(), cy = player.getY() + 1.2, cz = player.getZ();
        DustParticleOptions ring = new DustParticleOptions(gojo ? 0x7FD8FF : 0xB0121E, 1.6f);
        DustParticleOptions inner = new DustParticleOptions(gojo ? 0xFFFFFF : 0x160406, 1.3f);
        for (int i = 0; i < 48; ++i) {
            double a = i * Math.PI * 2.0 / 48.0;
            for (double r : new double[]{1.2, 2.2, 3.2}) {
                CurseFx.particles(level, r > 2.0 ? ring : inner, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        CurseFx.particles(level, ParticleTypes.END_ROD, cx, cy, cz, 30, 0.4, 0.5, 0.4, 0.12);
        CurseFx.particles(level, new DustParticleOptions(0xFFD34A, 1.2f), cx, cy + 0.3, cz, 40, 0.8, 0.6, 0.8, 0.02);
        float goldGain = after.goldMax() - (before.route() == StageRules.NONE ? 0.0f : before.goldMax());
        float armorGain = StageRules.armor(after.stage()) - (before.route() == StageRules.NONE ? 0.0f : StageRules.armor(before.stage()));
        SukunaNet.actionBar(player, "sukuna.hint.growth", (int)(goldGain / 2.0f), (int)armorGain,
            (int)StageRules.unarmedDamage(after.stage()), Math.round(StageRules.blackFlashChance(after.stage()) * 100.0f),
            Math.round(StageRules.takenFraction(after.stage(), false) * 100.0f), Math.round(StageRules.takenFraction(after.stage(), true) * 100.0f));
        int newMask = after.unlockedMask() & ~(before.route() == after.route() ? before.unlockedMask() : 0);
        MutableComponent names = Component.empty();
        boolean any = false;
        for (Skill sk : Skill.values()) {
            if ((newMask & 1 << sk.netId) == 0) continue;
            if (any) names.append("、");
            names.append(Component.translatable(sk.translationKey()));
            any = true;
        }
        if (any) {
            player.sendSystemMessage(Component.translatable("sukuna.hint.unlocked", names).withStyle(ChatFormatting.GOLD));
        }
        player.sendSystemMessage(Component.translatable("sukuna.hint.unlocked_extra." + route + "." + after.stage()).withStyle(ChatFormatting.GRAY));
    }
}
