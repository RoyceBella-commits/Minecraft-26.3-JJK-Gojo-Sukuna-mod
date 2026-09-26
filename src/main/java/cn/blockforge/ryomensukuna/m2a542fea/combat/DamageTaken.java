package cn.blockforge.ryomensukuna.m2a542fea.combat;

import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Growth toughness: an awakened sorcerer only takes part of each hit, down to 1% at stage V, or 10%
 * when the blow comes from another Gojo / Sukuna sorcerer. NPCs count as stage V. /kill and the
 * void are never reduced.
 */
public final class DamageTaken {
    private DamageTaken() {
    }

    public static float apply(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        int stage;
        if (target instanceof ServerPlayer p) {
            CurseState s = CurseManager.of(p);
            if (!s.awakened()) return amount;
            stage = s.stage();
        } else if (target instanceof JjkNpcEntity) {
            stage = StageRules.MAX_STAGE;
        } else {
            return amount;
        }
        return amount * StageRules.takenFraction(stage, CurseManager.isSorcerer(source.getEntity()));
    }
}
