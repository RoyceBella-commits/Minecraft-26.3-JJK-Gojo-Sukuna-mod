package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class SukunaClientState {
    public static float energy = 0.0f;
    public static float maxEnergy = 1000.0f;
    public static int fingers = 0;
    public static int unlockedMask = 0;
    public static int selected = 0;
    public static float selectedCooldown = 0.0f;
    public static int route = StageRules.NONE;
    public static int stage = 0;
    public static int hits = 0;
    public static int usedKinds = 0;
    public static int flags = 0;
    public static int postDomainHits = 0;
    public static float gold = 0.0f;
    public static float goldMax = 0.0f;
    public static boolean infinityOn;
    public static boolean receivedState;
    private static float burnout;
    private static float dashCd;
    private static float leapCd;
    private static long cooldownAt;
    private static long timersAt;
    private static int pendingSelection = -1;
    private static long selectionAt;
    private static float lastGold;
    public static long goldChangedAt;
    public static boolean goldLost;
    public static Skill dangerSkill;
    public static Vec3 dangerFrom;
    public static long dangerAt;

    private SukunaClientState() {
    }

    public static void selectLocally(int skillId) {
        pendingSelection = skillId;
        selectionAt = System.nanoTime();
        selected = skillId;
        selectedCooldown = 0.0f;
        cooldownAt = selectionAt;
    }

    private static float left(float value, long at) {
        return Math.max(0.0f, value - (float)(System.nanoTime() - at) / 1.0E9f);
    }

    public static float cooldown() {
        return left(selectedCooldown, cooldownAt);
    }

    public static float burnout() {
        return left(burnout, timersAt);
    }

    public static float dashCooldown() {
        return left(dashCd, timersAt);
    }

    public static float leapCooldown() {
        return left(leapCd, timersAt);
    }

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SukunaClientState.reset());
    }

    public static void setState(float e, float max, int f, int mask, int sel, float cd, int r, int st, int h, int used, int fl,
                                int postHits, float g, float gMax, float burn, float dash, float leap, boolean infinity) {
        receivedState = true;
        energy = e;
        maxEnergy = max;
        fingers = f;
        unlockedMask = mask;
        route = r;
        stage = st;
        hits = h;
        usedKinds = used;
        flags = fl;
        postDomainHits = postHits;
        if (Math.abs(g - lastGold) > 0.01f) {
            goldLost = g < lastGold;
            goldChangedAt = System.nanoTime();
        }
        lastGold = g;
        gold = g;
        goldMax = gMax;
        infinityOn = infinity;
        long now = System.nanoTime();
        burnout = burn;
        dashCd = dash;
        leapCd = leap;
        timersAt = now;
        if (pendingSelection == sel || now - selectionAt > 3000000000L) {
            pendingSelection = -1;
        }
        if (pendingSelection < 0) {
            selected = sel;
            selectedCooldown = cd;
            cooldownAt = now;
        }
    }

    public static void danger(int skillId, Vec3 from) {
        dangerSkill = Skill.byId(skillId);
        dangerFrom = from;
        dangerAt = System.nanoTime();
    }

    public static void reset() {
        receivedState = false;
        energy = 0.0f;
        maxEnergy = 1000.0f;
        fingers = 0;
        unlockedMask = 0;
        selected = 0;
        selectedCooldown = 0.0f;
        route = StageRules.NONE;
        stage = 0;
        hits = 0;
        usedKinds = 0;
        flags = 0;
        postDomainHits = 0;
        gold = 0.0f;
        goldMax = 0.0f;
        lastGold = 0.0f;
        infinityOn = false;
        burnout = dashCd = leapCd = 0.0f;
        pendingSelection = -1;
        dangerSkill = null;
        ChargeInput.reset();
    }

    public static boolean unlocked(int skillNetId) {
        return Skill.byId(skillNetId) != null && (unlockedMask & 1 << skillNetId) != 0;
    }

    public static boolean awakened() {
        return receivedState && route != StageRules.NONE;
    }

    public static boolean infinite() {
        return stage >= StageRules.MAX_STAGE;
    }

    public static boolean flag(int f) {
        return (flags & f) != 0;
    }

    public static Skill[] wheel() {
        return Skill.wheel(route);
    }

    /** Practice line for the HUD, e.g. "命中 7/15 · 技能 1/2". */
    public static Component practice() {
        return switch (stage) {
            case 1 -> Component.translatable("sukuna.practice.1", Math.min(hits, 5));
            case 2 -> Component.translatable("sukuna.practice.2", Math.min(hits, 15), Math.min(usedKinds, 2));
            case 3 -> Component.translatable("sukuna.practice.3", Math.min(hits, 25), Component.translatable(flag(CurseState.FLAG_HEAVY) ? "sukuna.practice.yes" : "sukuna.practice.no"));
            case 4 -> Component.translatable("sukuna.practice.4", Component.translatable(flag(CurseState.FLAG_DOMAIN) ? "sukuna.practice.yes" : "sukuna.practice.no"), Math.min(postDomainHits, 5));
            default -> Component.translatable("sukuna.practice.max");
        };
    }

    public static void showMessage(String key) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.hud.setOverlayMessage(Component.translatable(key), false);
        }
    }

    public static void onFeedback(int skillId, byte reason) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) {
            return;
        }
        String key = switch (reason) {
            case SukunaNet.FAIL_LOCKED -> "sukuna.hint.locked";
            case SukunaNet.FAIL_ENERGY -> "sukuna.hint.energy";
            case SukunaNet.CAST_OK -> "sukuna.hint.cast";
            case SukunaNet.FAIL_BURNOUT -> "sukuna.hint.skill_burnout";
            case SukunaNet.FAIL_FULL -> "sukuna.hint.heal_full";
            case SukunaNet.FAIL_CANCELLED -> "sukuna.hint.cancelled";
            case SukunaNet.FAIL_STUNNED -> "sukuna.hint.stunned_skill";
            default -> "sukuna.hint.cooldown";
        };
        Skill skill = Skill.byId(skillId);
        if (skill == null) {
            return;
        }
        if (skill.isHeal() && reason != SukunaNet.CAST_OK) {
            ChargeInput.healStopped();
        }
        ChatFormatting color = reason == SukunaNet.CAST_OK ? ChatFormatting.WHITE : reason == SukunaNet.FAIL_FULL ? ChatFormatting.GREEN : ChatFormatting.RED;
        Object arg = reason == SukunaNet.FAIL_LOCKED
            ? Component.translatable("sukuna.hint.locked_detail", Component.translatable(skill.translationKey()), StageRules.roman(skill.unlockStage))
            : Component.translatable(skill.translationKey());
        client.gui.hud.setOverlayMessage(Component.translatable(key, arg).withStyle(color), false);
    }
}
