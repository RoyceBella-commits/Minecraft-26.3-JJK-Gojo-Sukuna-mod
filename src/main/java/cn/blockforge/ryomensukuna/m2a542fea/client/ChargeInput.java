package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Unified controls for both routes: hold the wheel key to choose, the cast key to charge/fire,
 * the quick key for Infinity (Gojo) or held reverse technique (Sukuna), and C / Z for mobility.
 * Nothing fires while chat, inventories or menus are open.
 */
public final class ChargeInput {
    private static final ChargeSession SESSION = new ChargeSession();
    private static final InputGate WHEEL_GATE = new InputGate();
    private static final InputGate CAST_GATE = new InputGate();
    private static final InputGate HUD_GATE = new InputGate();
    private static final InputGate QUICK_GATE = new InputGate();
    private static final InputGate DASH_GATE = new InputGate();
    private static final InputGate LEAP_GATE = new InputGate();
    /** One gate per technique hotkey (index = skill net id). */
    private static final InputGate[] SKILL_GATES = new InputGate[Skill.values().length];
    /** Technique whose hotkey is driving the current press, or -1. */
    private static int hotkeySkill = -1;
    private static int heartbeat;

    static {
        for (int i = 0; i < SKILL_GATES.length; ++i) SKILL_GATES[i] = new InputGate();
    }
    private static int quickHeartbeat;
    private static boolean quickHealing;
    private static boolean suppressRelease;

    private ChargeInput() {
    }

    private static void channel(int skill, boolean held) {
        if (!ClientPlayNetworking.canSend(SukunaNet.C2S_CHANNEL)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(skill);
        buf.writeBoolean(held);
        SukunaClientPackets.send(SukunaNet.C2S_CHANNEL, buf);
    }

    public static boolean isCharging() {
        return SESSION.isCharging() || quickHealing;
    }

    public static int chargingSkill() {
        return quickHealing ? Skill.RCT.netId : SESSION.skillId();
    }

    public static float currentCharge() {
        return SESSION.seconds(System.nanoTime());
    }

    public static void reset() {
        SESSION.reset();
        WHEEL_GATE.reset();
        CAST_GATE.reset();
        HUD_GATE.reset();
        QUICK_GATE.reset();
        DASH_GATE.reset();
        LEAP_GATE.reset();
        for (InputGate gate : SKILL_GATES) gate.reset();
        hotkeySkill = -1;
        heartbeat = 0;
        quickHeartbeat = 0;
        quickHealing = false;
        suppressRelease = false;
    }

    /** The server stopped a heal (full or out of energy): stop re-requesting it until re-pressed. */
    public static void healStopped() {
        quickHealing = false;
        if (SESSION.isCharging()) {
            Skill s = Skill.byId(SESSION.skillId());
            if (s != null && s.isHeal()) SESSION.cancel();
        }
    }

    /** Makes a technique the current one (as choosing it on the wheel does). */
    public static void select(Skill skill) {
        if (ClientPlayNetworking.canSend(SukunaNet.C2S_SELECT)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeInt(skill.netId);
            SukunaClientPackets.send(SukunaNet.C2S_SELECT, buf);
            SukunaClientState.selectLocally(skill.netId);
        }
    }

    private static boolean drain(net.minecraft.client.KeyMapping key) {
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        return clicked;
    }

    /** Stops an unreleased charge (used when a menu opens or mobility interrupts it). */
    private static void cancelCharge(long now) {
        if (SESSION.isCharging()) {
            channel(SESSION.skillId(), false);
        }
        SESSION.tick(false, false, false, -1, now);
    }

    private static void stopQuickHeal() {
        if (quickHealing) {
            channel(Skill.RCT.netId, false);
            quickHealing = false;
        }
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean wheel = SukunaKeys.WHEEL.isDown(), wheelEvent = drain(SukunaKeys.WHEEL);
            boolean cast = SukunaKeys.CAST.isDown(), castEvent = drain(SukunaKeys.CAST);
            boolean hud = SukunaKeys.HUD.isDown(), hudEvent = drain(SukunaKeys.HUD);
            boolean quick = SukunaKeys.QUICK.isDown(), quickEvent = drain(SukunaKeys.QUICK);
            boolean dash = SukunaKeys.DASH.isDown(), dashEvent = drain(SukunaKeys.DASH);
            boolean leap = SukunaKeys.LEAP.isDown(), leapEvent = drain(SukunaKeys.LEAP);
            boolean active = client.level != null && client.player != null && client.isWindowActive()
                && client.player.isAlive() && !client.player.isSpectator();
            boolean awakened = SukunaClientState.awakened();
            boolean normalScreen = client.gui.screen() == null;
            boolean enabled = active && awakened && normalScreen;
            boolean wheelEdge = WHEEL_GATE.update(enabled, wheel, wheelEvent);
            boolean castEdge = CAST_GATE.update(enabled, cast, castEvent);
            boolean hudEdge = HUD_GATE.update(enabled, hud, hudEvent);
            boolean quickEdge = QUICK_GATE.update(enabled, quick, quickEvent);
            boolean dashEdge = DASH_GATE.update(enabled, dash, dashEvent);
            boolean leapEdge = LEAP_GATE.update(enabled, leap, leapEvent);
            // Technique hotkeys (unbound by default): pressing one selects that technique and acts as the cast key.
            boolean hotkeyEdge = false;
            for (Skill sk : Skill.values()) {
                var key = SukunaKeys.SKILLS[sk.netId];
                boolean edge = SKILL_GATES[sk.netId].update(enabled, key.isDown(), drain(key));
                if (edge && hotkeySkill < 0 && !SESSION.isCharging() && sk.route == SukunaClientState.route) {
                    hotkeySkill = sk.netId;
                    hotkeyEdge = true;
                }
            }
            boolean hotkeyHeld = hotkeySkill >= 0 && SKILL_GATES[hotkeySkill].held();
            if (hotkeySkill >= 0 && !hotkeyHeld && !hotkeyEdge && !SESSION.isCharging()) hotkeySkill = -1;
            cast = CAST_GATE.held() || hotkeyHeld;
            castEdge = castEdge || hotkeyEdge;
            long now = System.nanoTime();
            if (!active || !awakened) {
                hotkeySkill = -1;
                cancelCharge(now);
                stopQuickHeal();
                heartbeat = 0;
                suppressRelease = false;
                if (!awakened && client.gui.screen() instanceof RadialScreen) client.gui.setScreen(null);
                return;
            }
            if (!normalScreen) {
                hotkeySkill = -1;
                cancelCharge(now);
                stopQuickHeal();
                return;
            }
            if (hudEdge) SukunaHud.togglePanel();
            if (wheelEdge) {
                cancelCharge(now);
                client.gui.setScreen(new RadialScreen());
                return;
            }
            if (dashEdge) mobility(client, Mobility.DASH, now);
            if (leapEdge) mobility(client, Mobility.LEAP, now);
            handleQuick(quickEdge, QUICK_GATE.held(), now);
            // A hotkey casts its own technique without touching the wheel selection.
            int selected = hotkeyEdge ? hotkeySkill : SukunaClientState.selected;
            boolean connected = ClientPlayNetworking.canSend(SukunaNet.C2S_CAST);
            boolean wasCharging = SESSION.isCharging();
            boolean allowed = connected && SukunaClientState.receivedState && !quickHealing
                && SukunaClientState.unlocked(wasCharging ? SESSION.skillId() : selected);
            ChargeSession.Cast released = SESSION.tick(cast, castEdge, allowed, selected, now);
            if (!allowed && castEdge) {
                if (!connected) {
                    SukunaClientState.showMessage("sukuna.hint.connection");
                } else if (!SukunaClientState.receivedState) {
                    SukunaClientState.showMessage("sukuna.hud.syncing");
                } else if (!quickHealing) {
                    SukunaClientState.onFeedback(selected, SukunaNet.FAIL_LOCKED);
                }
            }
            if (castEdge && allowed) {
                suppressRelease = selected == Skill.DOMAIN.netId && CastVisuals.domainActive(client);
                channel(selected, true);
                heartbeat = 0;
            }
            if (SESSION.isCharging() && ++heartbeat >= 5 && !suppressRelease) {
                channel(SESSION.skillId(), true);
                heartbeat = 0;
            }
            if (SESSION.isCharging() && SESSION.skillId() == Skill.MAHORAGA.netId) {
                client.player.input.moveVector = Vec2.ZERO;
                client.player.input.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
                client.player.setDeltaMovement(Vec3.ZERO);
            }
            if (released != null) {
                Skill skill = Skill.byId(released.skillId());
                if (suppressRelease || skill != null && skill.isHeal()) {
                    channel(released.skillId(), false);
                    suppressRelease = false;
                    return;
                }
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeInt(released.skillId());
                buf.writeFloat(released.seconds());
                SukunaClientPackets.send(SukunaNet.C2S_CAST, buf);
            }
        });
    }

    /** B: Gojo toggles Infinity with a press; Sukuna heals while it is held. */
    private static void handleQuick(boolean edge, boolean held, long now) {
        if (SukunaClientState.route == StageRules.GOJO) {
            if (edge && ClientPlayNetworking.canSend(SukunaNet.C2S_QUICK)) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeByte(SukunaNet.QUICK_INFINITY);
                SukunaClientPackets.send(SukunaNet.C2S_QUICK, buf);
            }
            return;
        }
        if (edge) {
            if (!SukunaClientState.unlocked(Skill.RCT.netId)) {
                SukunaClientState.onFeedback(Skill.RCT.netId, SukunaNet.FAIL_LOCKED);
                return;
            }
            cancelCharge(now);
            channel(Skill.RCT.netId, true);
            quickHealing = true;
            quickHeartbeat = 0;
            return;
        }
        if (quickHealing) {
            if (!held) {
                stopQuickHeal();
            } else if (++quickHeartbeat >= 5) {
                channel(Skill.RCT.netId, true);
                quickHeartbeat = 0;
            }
        }
    }

    private static void mobility(Minecraft client, int kind, long now) {
        if (!ClientPlayNetworking.canSend(SukunaNet.C2S_MOBILITY)) return;
        // A likely-successful press interrupts an unreleased charge or a held heal.
        float cd = kind == Mobility.DASH ? SukunaClientState.dashCooldown() : SukunaClientState.leapCooldown();
        boolean unlocked = SukunaClientState.stage >= Mobility.unlockStage(SukunaClientState.route, kind);
        boolean sealed = kind == Mobility.DASH && SukunaClientState.route == StageRules.GOJO && SukunaClientState.burnout() > 0.0f;
        if (unlocked && cd <= 0.05f && !sealed) {
            cancelCharge(now);
            stopQuickHeal();
        }
        Vec2 move = client.player.input.moveVector;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(kind);
        buf.writeFloat(move.y);
        buf.writeFloat(move.x);
        SukunaClientPackets.send(SukunaNet.C2S_MOBILITY, buf);
    }
}
