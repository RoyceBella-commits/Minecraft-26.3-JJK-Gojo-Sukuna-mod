package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Domain music. Unlimited Void: when one opens around the player, its theme (the expansion voice
 * followed by the music) plays from the start; about a second before it ends the music loop fades
 * in and carries on until the domain closes or the player leaves. Malevolent Shrine keeps its track.
 */
public final class DomainPostFx {
    /** Length of the Void theme in ticks (21.8 s). */
    private static final int THEME_TICKS = 436;
    /** The theme only starts for a domain that has just opened. */
    private static final int THEME_START_WINDOW = 60;
    private static final double THEME_HEARING = 16.0;
    private static DomainMusic shrine;
    private static DomainMusic theme;
    private static DomainMusic loop;
    private static int themedVoid = -1;
    private static int themeQueuedAt;
    private static int clientTicks;

    private DomainPostFx() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(DomainPostFx::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DomainPostFx.stop(client));
    }

    /** The nearest standing Unlimited Void whose sphere (plus a margin) reaches the player. */
    private static VoidDomainEntity nearVoid(Minecraft c, double margin) {
        if (c.level == null || c.player == null) return null;
        double r = VoidDomainEntity.RADIUS + margin;
        VoidDomainEntity best = null;
        double bestSq = r * r;
        for (VoidDomainEntity v : c.level.getEntities(SukunaMod.VOID_DOMAIN, c.player.getBoundingBox().inflate(r), e -> e.closeAt() < 0)) {
            double d = v.distanceToSqr(c.player);
            if (d <= bestSq) {
                bestSq = d;
                best = v;
            }
        }
        return best;
    }

    private static boolean playing(Minecraft client, DomainMusic music) {
        return music != null && !music.isStopped() && client.getSoundManager().isActive(music);
    }

    private static void tick(Minecraft client) {
        ++clientTicks;
        if (client.level == null || client.player == null) {
            DomainPostFx.stop(client);
            return;
        }
        VoidDomainEntity near = nearVoid(client, THEME_HEARING);
        if (near != null && near.getId() != themedVoid && near.age() < THEME_START_WINDOW) {
            themedVoid = near.getId();
            if (theme != null) client.getSoundManager().stop((SoundInstance)theme);
            theme = new DomainMusic(SukunaSounds.VOID_THEME, c -> nearVoid(c, THEME_HEARING) != null, SoundSource.PLAYERS, false, 1.0f, 1.0f);
            client.getSoundManager().queueTickingSound((TickableSoundInstance)theme);
            themeQueuedAt = clientTicks;
        }
        boolean themeOn = theme != null && !theme.isStopped() && (clientTicks - themeQueuedAt < 5 || playing(client, theme));
        boolean themeEnding = themeOn && theme.age() >= THEME_TICKS - 20;
        boolean inVoid = CastVisuals.voidActive(client);
        if (inVoid && (!themeOn || themeEnding)) {
            if (loop == null || loop.isStopped()) {
                loop = new DomainMusic(SukunaSounds.VOID_LOOP, CastVisuals::voidActive, SoundSource.PLAYERS, true, 0.02f, 1.0f);
                client.getSoundManager().queueTickingSound((TickableSoundInstance)loop);
            }
        } else if (loop != null) {
            loop.stopFade();
            if (loop.isStopped()) loop = null;
        }
        // The Void is closed: when both overlap, its music wins.
        boolean inShrine = !inVoid && !themeOn && CastVisuals.domainActive(client);
        if (inShrine) {
            if (shrine == null || shrine.isStopped()) {
                shrine = new DomainMusic(SukunaSounds.DOMAIN_BGM, CastVisuals::domainActive);
                client.getSoundManager().queueTickingSound((TickableSoundInstance)shrine);
            }
        } else if (shrine != null) {
            shrine.stopFade();
            if (shrine.isStopped()) shrine = null;
        }
    }

    private static void stop(Minecraft client) {
        for (DomainMusic music : new DomainMusic[]{shrine, theme, loop}) {
            if (music != null) {
                music.stopFade();
                client.getSoundManager().stop((SoundInstance)music);
            }
        }
        shrine = theme = loop = null;
        themedVoid = -1;
    }
}
