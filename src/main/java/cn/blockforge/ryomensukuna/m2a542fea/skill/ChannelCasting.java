package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.SixEyes;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Growth;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Held (charging / channelled) technique sessions. Only one per player at a time. */
public final class ChannelCasting {
    private static final Map<UUID, Session> ACTIVE = new HashMap<UUID, Session>();
    /** A press that recalled Mahoraga: its heartbeats and release must not summon a new one. */
    private static final java.util.Set<UUID> RECALLED = new java.util.HashSet<UUID>();
    /** Holding the key this long with no Ao out summons one circling the caster. */
    private static final float AO_HOLD_SECONDS = 0.4f;
    private static final Identifier HEAL_SLOW = SukunaMod.id("rct_slow");

    public static void clear() {
        ACTIVE.clear();
        RECALLED.clear();
    }

    /** Whether the current Ao press already summoned an orbiting Ao (so its release must not fire another). */
    public static boolean spawnedAo(ServerPlayer p) {
        Session s = ACTIVE.get(p.getUUID());
        return s != null && s.skill == Skill.AO && s.aoSpawned;
    }

    /** Consumes the release of a press that recalled Mahoraga. */
    public static boolean consumeRecall(ServerPlayer p) {
        return RECALLED.remove(p.getUUID());
    }

    public static boolean active(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    public static boolean healing(ServerPlayer p) {
        Session s = ACTIVE.get(p.getUUID());
        return s != null && s.skill.isHeal();
    }

    public static Skill activeSkill(ServerPlayer p) {
        Session s = ACTIVE.get(p.getUUID());
        return s == null ? null : s.skill;
    }

    public static Vec3 shadow(ServerPlayer p) {
        Session s = ACTIVE.get(p.getUUID());
        return s == null ? ChannelCasting.ground(p) : s.shadow;
    }

    public static Vec3 ground(ServerPlayer p) {
        Vec3 pos = p.position().add(Vec3.directionFromRotation((float)0.0f, (float)p.getYRot()).scale(4.0));
        int y = (int)Math.floor(pos.y) + 2;
        while ((double)y > pos.y - 24.0) {
            BlockPos b = BlockPos.containing((double)pos.x, (double)y, (double)pos.z);
            if (!p.level().getBlockState(b).getCollisionShape((BlockGetter)p.level(), b).isEmpty()) {
                return new Vec3(pos.x, (double)y + 1.02, pos.z);
            }
            --y;
        }
        return pos;
    }

    /** Shared gate for starting any technique: route, stage, burnout, cooldown. Sends feedback. */
    public static boolean ready(ServerPlayer p, Skill skill) {
        if (!CurseManager.unlocked(p, skill)) {
            SukunaNet.sendFeedback(p, skill.netId, SukunaNet.FAIL_LOCKED);
            return false;
        }
        // Held by Unlimited Void, the one thing left to try is opening one's own domain.
        if (skill != Skill.DOMAIN && cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity.stunned(p)) {
            SukunaNet.sendFeedback(p, skill.netId, SukunaNet.FAIL_STUNNED);
            return false;
        }
        if (skill.blockedByBurnout() && CurseManager.burntOut(p)) {
            SukunaNet.sendFeedback(p, skill.netId, SukunaNet.FAIL_BURNOUT);
            return false;
        }
        if (CurseManager.cooldownLeft(p, skill) > 0.0f) {
            SukunaNet.sendFeedback(p, skill.netId, SukunaNet.FAIL_COOLDOWN);
            return false;
        }
        return true;
    }

    public static void input(ServerPlayer p, int id, boolean held) {
        if (!held) {
            RECALLED.remove(p.getUUID());
            ChannelCasting.end(p);
            return;
        }
        Skill skill = Skill.byId(id);
        if (skill == null || !p.isAlive() || p.isSpectator()) {
            return;
        }
        Session prior = ACTIVE.get(p.getUUID());
        if (prior != null && prior.skill == skill) {
            prior.last = p.level().getGameTime();
            if (prior.aoSpawned) cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity.grab(p);
            return;
        }
        if (skill == Skill.MAHORAGA && RECALLED.contains(p.getUUID())) {
            return;
        }
        if (skill == Skill.AO && cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity.grab(p)) {
            // An existing Ao circles the caster while the key is held instead of casting a new one.
            return;
        }
        if (skill == Skill.DOMAIN && DomainSkill.dismiss(p)) {
            SukunaNet.pose(p, -1, false, p.position());
            return;
        }
        if (skill == Skill.MAHORAGA && cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaSkill.recall(p)) {
            RECALLED.add(p.getUUID());
            SukunaNet.pose(p, -1, false, p.position());
            return;
        }
        if (!ready(p, skill)) {
            return;
        }
        float need = skill.isHeal() ? 0.25f : skill.cost(0.0f);
        if (!CurseManager.canAfford(p, need)) {
            SukunaNet.sendFeedback(p, id, SukunaNet.FAIL_ENERGY);
            return;
        }
        if (skill.isHeal() && !needsHealing(p)) {
            SukunaNet.sendFeedback(p, id, SukunaNet.FAIL_FULL);
            return;
        }
        if (prior != null) {
            ChannelCasting.end(p);
        }
        Session s = new Session(p, skill);
        ACTIVE.put(p.getUUID(), s);
        SukunaNet.pose(p, id, true, s.shadow);
        if (skill.isHeal()) {
            AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(HEAL_SLOW, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (skill == Skill.DOMAIN) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SukunaSounds.DOMAIN_CLAP, SoundSource.PLAYERS, 2.2f, 0.85f);
        }
        if (skill == Skill.VOID) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
        }
        if (skill == Skill.DOMAIN || skill == Skill.VOID || skill == Skill.WORLD_CUT) {
            SixEyes.warn(p, skill);
        }
    }

    private static boolean needsHealing(ServerPlayer p) {
        CurseState s = CurseManager.of(p);
        return p.getHealth() < p.getMaxHealth() - 0.001f || s.goldHp() < s.goldMax() - 0.001f;
    }

    public static void end(ServerPlayer p) {
        Session ended = ACTIVE.remove(p.getUUID());
        if (ended != null) {
            if (ended.skill.isHeal()) {
                AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(HEAL_SLOW);
            }
            SukunaNet.pose(p, -1, false, p.position());
        }
    }

    public static void tick(ServerPlayer p) {
        Session s = ACTIVE.get(p.getUUID());
        if (s == null) {
            return;
        }
        long now = p.level().getGameTime();
        if (!p.isAlive() || p.isSpectator() || p.level() != s.world || now - s.last > 15L) {
            ChannelCasting.end(p);
            return;
        }
        if (s.skill == Skill.MAHORAGA) {
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0.0f;
            if (p.position().distanceToSqr(s.anchor) > 0.0025) {
                p.teleportTo(s.anchor.x, s.anchor.y, s.anchor.z);
            }
        }
        if (s.skill == Skill.AO) {
            ChannelCasting.tickAoHold(p, s, now);
        }
        if (s.skill.isHeal()) {
            if (!CurseManager.spend(p, 0.5f)) {
                SukunaNet.sendFeedback(p, s.skill.netId, SukunaNet.FAIL_ENERGY);
                ChannelCasting.end(p);
                return;
            }
            float seconds = (float)(now - s.start) / 20.0f;
            if ((now - s.start) % 30L == 0L) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SukunaSounds.RCT_HEAL, SoundSource.PLAYERS, 0.9f, 1.0f);
            }
            // Active reverse technique: a large, quickly accelerating heal.
            if (!Growth.heal(p, Math.min(2.0f, 0.4f + seconds * 0.8f))) {
                SukunaNet.sendFeedback(p, s.skill.netId, SukunaNet.FAIL_FULL);
                ChannelCasting.end(p);
                CurseManager.sync(p);
                return;
            }
            if ((now - s.start) % 4L == 0L) {
                CurseFx.particlesExcept(p.level(), p, ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.35, 0.6, 0.35, 0.01);
            }
        }
        chargeFx(p, s, now);
        if ((now - s.start) % 10L == 0L) {
            SukunaNet.pose(p, s.skill.netId, true, s.shadow);
        }
    }

    /** Held Ao: after a short hold with no Ao out, summon one circling the caster; keep feeding it charge. */
    private static void tickAoHold(ServerPlayer p, Session s, long now) {
        float held = Math.min(Skill.MAX_CHARGE_SECONDS, (float)(now - s.start) / 20.0f);
        cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity ao = cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity.of(p);
        if (!s.aoSpawned) {
            if (held < AO_HOLD_SECONDS || ao != null) return;
            if (!CurseManager.spend(p, Skill.AO.cost(held))) {
                SukunaNet.sendFeedback(p, Skill.AO.netId, SukunaNet.FAIL_ENERGY);
                ChannelCasting.end(p);
                return;
            }
            int castId = cn.blockforge.ryomensukuna.m2a542fea.progression.Progression.beginCast(p);
            CurseManager.setCooldown(p, Skill.AO, Skill.AO.cooldownSeconds);
            cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity.summonOrbiting(p, held, castId);
            cn.blockforge.ryomensukuna.m2a542fea.progression.Progression.recordUse(p, Skill.AO);
            s.aoSpawned = true;
            SukunaNet.sendFeedback(p, Skill.AO.netId, SukunaNet.CAST_OK);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.6f);
            return;
        }
        if (ao == null || held <= ao.charge() + 0.05f) return;
        if (CurseManager.spend(p, (held - ao.charge()) * Skill.AO.chargeCost)) ao.grow(held);
    }

    /** Charge particles: full for everyone around, half as many on the caster's own screen. */
    private static void chargeParticles(ServerLevel level, ServerPlayer p, net.minecraft.core.particles.ParticleOptions effect, Vec3 at, int count, double spread, double speed) {
        CurseFx.particlesExcept(level, p, effect, at.x, at.y, at.z, count, spread, spread, spread, speed);
        CurseFx.particlesTo(p, effect, at.x, at.y, at.z, Math.max(1, count / 2), spread, spread, spread, speed);
    }

    /** Server-side charge particles so everyone (the caster too, lighter) sees the build-up of Gojo techniques. */
    private static void chargeFx(ServerPlayer p, Session s, long now) {
        ServerLevel level = p.level();
        float t = Math.min(2.5f, (now - s.start) / 20.0f);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        Vec3 hand = eye.add(look.scale(0.9)).add(0.0, -0.25, 0.0);
        switch (s.skill) {
            case AO -> {
                if (s.aoSpawned) break;
                chargeParticles(level, p, new DustParticleOptions(0x2F7BFF, 0.9f + t * 0.35f), hand, 4, 0.1, 0.0);
                if ((now - s.start) % 10L == 0L) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5f, 1.4f + t * 0.1f);
                }
            }
            case AKA -> {
                chargeParticles(level, p, new DustParticleOptions(0xFF2A2A, 0.9f + t * 0.35f), hand, 4, 0.1, 0.0);
                if ((now - s.start) % 10L == 0L) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.5f, 0.8f + t * 0.2f);
                }
            }
            case MURASAKI -> {
                double gap = Math.max(0.0, 1.1 * (1.0 - t / Skill.MURASAKI_MIN_CHARGE));
                if (gap > 0.02) {
                    // Blue and red spiral toward each other.
                    double spin = (now - s.start) * 0.3;
                    Vec3 up = right.cross(look).normalize();
                    Vec3 off = right.scale(Math.cos(spin) * gap).add(up.scale(Math.sin(spin) * gap));
                    chargeParticles(level, p, new DustParticleOptions(0x2F7BFF, 1.6f), hand.subtract(off), 5, 0.1, 0.0);
                    chargeParticles(level, p, new DustParticleOptions(0xFF2A2A, 1.6f), hand.add(off), 5, 0.1, 0.0);
                } else {
                    chargeParticles(level, p, new DustParticleOptions(0x9B30FF, 2.4f), hand, 10, 0.28, 0.0);
                    chargeParticles(level, p, ParticleTypes.PORTAL, hand, 8, 0.3, 0.3);
                }
                if (now - s.start == (long)(Skill.MURASAKI_MIN_CHARGE * 20.0f)) {
                    // The moment of fusion: a flash of light and a deep hum.
                    for (int i = 0; i < 24; ++i) {
                        double a = i * Math.PI * 2.0 / 24.0;
                        Vec3 out = right.scale(Math.cos(a)).add(0.0, Math.sin(a), 0.0).scale(0.35);
                        level.sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 0, out.x, out.y, out.z, 1.0);
                    }
                    chargeParticles(level, p, new DustParticleOptions(0xC08CFF, 2.6f), hand, 24, 0.4, 0.0);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.7f);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 1.2f);
                }
                if ((now - s.start) % 8L == 0L) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.4f + t * 0.3f, 0.6f + t * 0.3f);
                }
            }
            case VOID -> CurseFx.particlesExcept(level, p, ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 4, 1.2, 0.8, 1.2, 0.02);
            default -> { }
        }
    }

    private static final class Session {
        Skill skill;
        boolean aoSpawned;
        long start;
        long last;
        Vec3 anchor;
        Vec3 shadow;
        Level world;

        Session(ServerPlayer p, Skill s) {
            this.skill = s;
            this.world = p.level();
            this.start = this.last = this.world.getGameTime();
            this.anchor = p.position();
            this.shadow = ChannelCasting.ground(p);
        }
    }
}
