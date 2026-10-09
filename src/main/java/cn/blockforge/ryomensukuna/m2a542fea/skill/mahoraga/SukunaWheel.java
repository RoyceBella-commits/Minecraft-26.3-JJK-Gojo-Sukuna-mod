package cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sukuna bearing Mahoraga's wheel: while a Mahoraga he called stands, a wheel of his own hangs over
 * his head and adapts to whatever hurts him, exactly as Mahoraga's does (one stage every 3 s from the
 * first hit, each stage taking a fifth off that kind of damage; immune after 15 s). Only damage is
 * adapted to, none of Mahoraga's evolutions. The wheel and all it learned vanish once that Mahoraga
 * is gone (destroyed or sent back).
 */
public final class SukunaWheel {
    /** Visual state is re-sent this often so players who come close see the wheel too. */
    private static final int BROADCAST_TICKS = 20;
    private static final Map<UUID, Wheel> WHEELS = new ConcurrentHashMap<>();

    private SukunaWheel() {
    }

    private static final class Wheel {
        final Adaptation state = new Adaptation();
        LivingEntity owner;
        MahoragaEntity mahoraga;
        int steps;
        long turnedAt;
    }

    public static void clear() {
        WHEELS.clear();
    }

    public static boolean active(LivingEntity e) {
        return WHEELS.containsKey(e.getUUID());
    }

    /** A Mahoraga was called for {@code owner}: the wheel appears (or follows the new Mahoraga). */
    public static void attach(LivingEntity owner, MahoragaEntity mahoraga) {
        Wheel w = WHEELS.computeIfAbsent(owner.getUUID(), k -> new Wheel());
        boolean fresh = w.owner == null;
        w.owner = owner;
        w.mahoraga = mahoraga;
        if (fresh) {
            w.turnedAt = owner.level().getGameTime() - 12L;
            if (owner instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("sukuna.wheel.appear"), true);
        }
        broadcast(owner, w, true);
    }

    /** The wheel fades at once (its Mahoraga fell); the caller tells the bearer. */
    public static void end(LivingEntity owner) {
        Wheel w = WHEELS.remove(owner.getUUID());
        if (w == null) return;
        broadcast(w.owner != null ? w.owner : owner, w, false);
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Wheel>> it = WHEELS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Wheel> e = it.next();
            Wheel w = e.getValue();
            LivingEntity owner = resolveOwner(server, e.getKey(), w);
            if (owner == null) {
                it.remove();
                continue;
            }
            if (!mahoragaStands(owner, w)) {
                it.remove();
                broadcast(owner, w, false);
                continue;
            }
            long now = owner.level().getGameTime();
            for (Adaptation.Entry entry : w.state.tick()) {
                ++w.steps;
                w.turnedAt = now;
                owner.level().playSound(null, owner.getX(), owner.getY() + owner.getBbHeight(), owner.getZ(), SukunaSounds.MAHORAGA_ADAPT, SoundSource.PLAYERS, 0.8f, 1.15f);
                message(owner, entry.immune()
                    ? Component.translatable("sukuna.wheel.immune", MahoragaAdaptation.damageLabel(entry.id))
                    : Component.translatable("sukuna.wheel.stage", entry.stage(), MahoragaAdaptation.damageLabel(entry.id)));
                broadcast(owner, w, true);
            }
            if (now % BROADCAST_TICKS == 0) broadcast(owner, w, true);
        }
    }

    /** The bearer, followed through respawns (a new player object) and dimension changes. */
    private static LivingEntity resolveOwner(MinecraftServer server, UUID id, Wheel w) {
        if (w.owner != null && !w.owner.isRemoved() && w.owner.isAlive()) return w.owner;
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            // A player keeps the wheel through death as long as the Mahoraga is still standing.
            w.owner = player;
            return player;
        }
        if (w.owner != null && w.owner.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof LivingEntity moved) {
                    w.owner = moved;
                    return moved;
                }
            }
        }
        return null;
    }

    /** Whether the bearer's Mahoraga is still out (it may have been re-created by a dimension change). */
    private static boolean mahoragaStands(LivingEntity owner, Wheel w) {
        if (w.mahoraga != null && !w.mahoraga.isRemoved() && w.mahoraga.isAlive()) return true;
        String id = owner.getStringUUID();
        MahoragaEntity found = owner.level().getEntitiesOfClass(MahoragaEntity.class, owner.getBoundingBox().inflate(256.0),
            m -> m.isAlive() && id.equals(m.ownerUuid())).stream().findFirst().orElse(null);
        w.mahoraga = found;
        return found != null;
    }

    /** ALLOW_DAMAGE hook: the wheel starts reading each new kind of harm and turns fully adapted harm away. */
    public static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        Wheel w = WHEELS.get(target.getUUID());
        if (w == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        String id = MahoragaAdaptation.damageId(source);
        Adaptation.Entry entry = w.state.get(id, false);
        if (entry == null) {
            w.state.start(id, false, Adaptation.Evolution.NONE);
            message(target, Component.translatable("sukuna.wheel.start", MahoragaAdaptation.damageLabel(id)));
            return true;
        }
        return !entry.immune();
    }

    /** Share of the hit that still lands through the wheel's adaptation (1 when it has none). */
    public static float scale(LivingEntity target, DamageSource source, float amount) {
        Wheel w = WHEELS.get(target.getUUID());
        if (w == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        Adaptation.Entry entry = w.state.get(MahoragaAdaptation.damageId(source), false);
        return entry == null ? amount : amount * entry.multiplier();
    }

    private static void message(LivingEntity owner, Component text) {
        if (owner instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.translatable("sukuna.wheel.prefix").append(text), true);
        }
    }

    private static void broadcast(LivingEntity owner, Wheel w, boolean active) {
        if (owner.level() instanceof ServerLevel level) {
            SukunaNet.wheel(level, owner, active, w.steps, w.turnedAt);
        }
    }
}
