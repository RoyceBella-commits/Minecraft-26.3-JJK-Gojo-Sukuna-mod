package cn.blockforge.ryomensukuna.m2a542fea.combat;

import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Growth toughness: an awakened sorcerer only takes part of each hit, down to 1% at stage V. The
 * rule is written as an exception list: every attacker (vanilla, other mods, the environment) is
 * held to the 1% floor, except the "characters" who fight at 10% — Gojo / Sukuna sorcerers and
 * the Fate kings mod. NPCs count as stage V, and their bigger health pool is offset by scaling
 * every hit they take, so a fight lasts as long as before. /kill and the void are never reduced.
 */
public final class DamageTaken {
    /** Namespace of the Fate kings mod (its entities, damage types and regalia). */
    private static final String FATE_MOD = "fatekings";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private DamageTaken() {
    }

    public static float apply(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        // Sukuna's own adaptation wheel (while his Mahoraga stands) takes its share first.
        amount = cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.SukunaWheel.scale(target, source, amount);
        int stage;
        if (target instanceof ServerPlayer p) {
            CurseState s = CurseManager.of(p);
            if (!s.awakened()) return amount;
            stage = s.stage();
        } else if (target instanceof JjkNpcEntity) {
            stage = StageRules.MAX_STAGE;
            amount *= JjkNpcEntity.DAMAGE_SCALE;
        } else {
            return amount;
        }
        return amount * StageRules.takenFraction(stage, fromCharacter(source));
    }

    /** The exceptions to the 1% floor: Gojo / Sukuna sorcerers and anything from the Fate kings mod. */
    public static boolean fromCharacter(DamageSource source) {
        Entity attacker = source.getEntity();
        if (CurseManager.isSorcerer(attacker)) return true;
        if (fromFateMod(attacker) || fromFateMod(source.getDirectEntity())) return true;
        return source.typeHolder().unwrapKey().map(k -> FATE_MOD.equals(k.identifier().getNamespace())).orElse(false);
    }

    private static boolean fromFateMod(Entity e) {
        if (e == null) return false;
        if (FATE_MOD.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) return true;
        if (!(e instanceof LivingEntity living)) return false;
        // A king is a player (or mob) wearing the full regalia of that mod.
        for (EquipmentSlot slot : ARMOR) {
            if (!FATE_MOD.equals(BuiltInRegistries.ITEM.getKey(living.getItemBySlot(slot).getItem()).getNamespace())) return false;
        }
        return true;
    }
}
