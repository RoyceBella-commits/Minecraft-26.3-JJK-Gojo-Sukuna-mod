package cn.blockforge.ryomensukuna.m2a542fea.combat;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bare-handed strikes of awakened players (and technique NPCs). A full-strength punch may become a
 * Black Flash: triple damage with a red-black spark of distorted cursed energy.
 */
public final class BlackFlash {
    /** Attacker -> game time of a rolled Black Flash waiting for its damage call. */
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    private BlackFlash() {
    }

    public static void clear() {
        PENDING.clear();
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity target) {
                strike(sp, target);
            }
            return InteractionResult.PASS;
        });
    }

    private static void strike(ServerPlayer p, LivingEntity target) {
        CurseState s = CurseManager.of(p);
        if (!s.awakened() || p.isSpectator() || !p.getMainHandItem().isEmpty() || VoidDomainEntity.stunned(p)) return;
        if (p.getAttackStrengthScale(0.5f) < 0.9f) return;
        boolean flash = p.getRandom().nextFloat() < StageRules.blackFlashChance(s.stage());
        Vec3 dir = p.getViewVector(1.0f);
        Vec3 at = target.getBoundingBox().getCenter().subtract(dir.scale(Math.min(0.6, target.getBbWidth() * 0.5 + 0.1)));
        ServerLevel level = p.level();
        if (flash) {
            PENDING.put(p.getUUID(), level.getGameTime());
            effects(level, at, dir, target);
            SukunaNet.actionBar(p, "sukuna.hint.black_flash");
        } else {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9f, 0.7f);
            CurseFx.particles(level, ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.3);
        }
        SukunaNet.fist(level, at, dir, flash);
    }

    /** Called with every incoming hit; multiplies a player's pending Black Flash melee blow. */
    public static float modify(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer p) || source.getDirectEntity() != p || !source.is(DamageTypes.PLAYER_ATTACK)) return amount;
        Long at = PENDING.remove(p.getUUID());
        if (at == null || p.level().getGameTime() - at > 1L) return amount;
        return amount * StageRules.BLACK_FLASH_MULTIPLIER;
    }

    /** NPC melee: rolls at the full-level rate and returns the damage multiplier (effects included). */
    public static float npcStrike(LivingEntity npc, LivingEntity target) {
        Vec3 dir = target.getBoundingBox().getCenter().subtract(npc.getEyePosition());
        dir = dir.lengthSqr() < 1.0E-4 ? npc.getViewVector(1.0f) : dir.normalize();
        Vec3 at = target.getBoundingBox().getCenter().subtract(dir.scale(Math.min(0.6, target.getBbWidth() * 0.5 + 0.1)));
        boolean flash = npc.getRandom().nextFloat() < StageRules.MAX_BLACK_FLASH;
        ServerLevel level = (ServerLevel)npc.level();
        if (flash) effects(level, at, dir, target);
        SukunaNet.fist(level, at, dir, flash);
        return flash ? StageRules.BLACK_FLASH_MULTIPLIER : 1.0f;
    }

    /** Red and black sparks, a shock of distorted space and a heavy crack. */
    private static void effects(ServerLevel level, Vec3 at, Vec3 dir, LivingEntity target) {
        DustParticleOptions red = new DustParticleOptions(0xE0101A, 2.2f);
        DustParticleOptions black = new DustParticleOptions(0x050505, 2.6f);
        CurseFx.particles(level, red, at.x, at.y, at.z, 40, 0.55, 0.55, 0.55, 0.0);
        CurseFx.particles(level, black, at.x, at.y, at.z, 40, 0.65, 0.65, 0.65, 0.0);
        for (int i = 0; i < 24; ++i) {
            Vec3 v = new Vec3(level.getRandom().nextGaussian(), level.getRandom().nextGaussian() * 0.6, level.getRandom().nextGaussian()).normalize().scale(0.6).add(dir.scale(0.4));
            level.sendParticles(i % 2 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.SMOKE, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
        }
        CurseFx.particles(level, ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SukunaSounds.BLACK_FLASH, SoundSource.PLAYERS, 1.6f, 1.0f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.5f);
        target.push(dir.x * 0.9, 0.35, dir.z * 0.9);
        target.needsSync = true;
    }
}
