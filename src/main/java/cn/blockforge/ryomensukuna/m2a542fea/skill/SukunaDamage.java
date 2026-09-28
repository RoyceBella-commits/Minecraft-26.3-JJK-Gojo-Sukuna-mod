package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class SukunaDamage {
    public static final ResourceKey<DamageType> WORLD_CUT = ResourceKey.create(Registries.DAMAGE_TYPE, SukunaMod.id("world_cut"));
    /** Domain sure-hit: passes through Infinity (but not through an overlapping enemy domain). */
    public static final ResourceKey<DamageType> SURE_HIT = ResourceKey.create(Registries.DAMAGE_TYPE, SukunaMod.id("sure_hit"));

    /** Hollow Purple fused in flight from Ao and Aka: ordinary damage, so armour softens it. */
    public static final ResourceKey<DamageType> FUSED_PURPLE = ResourceKey.create(Registries.DAMAGE_TYPE, SukunaMod.id("fused_purple"));

    /** Finishers (Hollow Purple, World Cut): about 1000 to anything but players, 60-70 to players. */
    public static final float FINISHER_DAMAGE = 1000.0f;

    private SukunaDamage() {
    }

    public static float finisher(net.minecraft.world.entity.LivingEntity target, net.minecraft.util.RandomSource random) {
        return target instanceof net.minecraft.world.entity.player.Player ? 60.0f + random.nextFloat() * 10.0f : FINISHER_DAMAGE;
    }

    public static DamageSource cursed(Level world, Entity attacker) {
        return world.damageSources().source(DamageTypes.MAGIC, attacker);
    }

    public static DamageSource worldCut(Level world, Entity attacker) {
        return new DamageSource(world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(WORLD_CUT), attacker);
    }

    public static DamageSource fusedPurple(Level world, Entity attacker) {
        return new DamageSource(world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(FUSED_PURPLE), attacker);
    }

    public static DamageSource sureHit(Level world, Entity attacker) {
        return new DamageSource(world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SURE_HIT), attacker);
    }

    public static DamageSource slash(Level world, Entity attacker) {
        return world.damageSources().source(DamageTypes.MOB_ATTACK, attacker);
    }

    public static DamageSource flame(Level world, Entity attacker) {
        return world.damageSources().source(DamageTypes.ON_FIRE, attacker);
    }
}
