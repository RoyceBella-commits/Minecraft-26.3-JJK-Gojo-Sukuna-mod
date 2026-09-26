package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class SkillDispatcher {
    private SkillDispatcher() {
    }

    public static void cast(ServerPlayer player, Skill skill, float chargeSeconds) {
        if (player.isRemoved() || !player.isAlive() || player.isSpectator() || skill == null || !Float.isFinite(chargeSeconds)) {
            return;
        }
        chargeSeconds = Math.max(0.0f, Math.min(Skill.MAX_CHARGE_SECONDS, chargeSeconds));
        boolean aoSpawned = skill == Skill.AO && ChannelCasting.spawnedAo(player);
        Vec3 summonAnchor = ChannelCasting.shadow(player);
        ChannelCasting.end(player);
        if (skill.isHeal()) {
            return;
        }
        if (skill == Skill.MAHORAGA && ChannelCasting.consumeRecall(player)) {
            return;
        }
        if (skill == Skill.DOMAIN && DomainSkill.dismiss(player)) {
            return;
        }
        // Releasing the key leaves a circling Ao where it is; a hold that summoned one never fires a second.
        if (skill == Skill.AO && (cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity.release(player) || aoSpawned)) {
            return;
        }
        if (!ChannelCasting.ready(player, skill)) {
            CurseManager.sync(player);
            return;
        }
        CurseState state = CurseManager.of(player);
        if (state.selected() != skill.netId) {
            CurseManager.setState(player, state.withSelected(skill.netId));
        }
        if (skill == Skill.MURASAKI && chargeSeconds < Skill.MURASAKI_MIN_CHARGE) {
            // Released early: cancelled, the invested charge is spent and only a short cooldown applies.
            CurseManager.spend(player, skill.chargeCost * chargeSeconds);
            CurseManager.setCooldown(player, skill, 1.0f);
            SukunaNet.sendFeedback(player, skill.netId, SukunaNet.FAIL_CANCELLED);
            return;
        }
        if (skill == Skill.VOID && chargeSeconds < Skill.VOID_MIN_CHARGE) {
            CurseManager.setCooldown(player, skill, 1.0f);
            SukunaNet.sendFeedback(player, skill.netId, SukunaNet.FAIL_CANCELLED);
            return;
        }
        if (skill == Skill.VOID && GojoSkills.voidActive(player)) {
            SukunaNet.sendFeedback(player, skill.netId, SukunaNet.FAIL_COOLDOWN);
            return;
        }
        float cost = skill.cost(chargeSeconds);
        if (!CurseManager.spend(player, cost)) {
            SukunaNet.sendFeedback(player, skill.netId, SukunaNet.FAIL_ENERGY);
            CurseManager.sync(player);
            return;
        }
        int castId = Progression.beginCast(player);
        CurseManager.setCooldown(player, skill, skill.cooldownSeconds);
        switch (skill) {
            case KAI -> CombatSkills.fireKai(player, chargeSeconds);
            case CLEAVE -> CombatSkills.fireCleave(player, chargeSeconds);
            case RED -> CombatSkills.fireRed(player, chargeSeconds);
            case WORLD_CUT -> CombatSkills.fireWorldCut(player, chargeSeconds);
            case DOMAIN -> DomainSkill.cast(player, chargeSeconds);
            case MAHORAGA -> MahoragaSkill.castAt(player, chargeSeconds, summonAnchor);
            case CURSED_BARRAGE -> CombatSkills.fireCursedBarrage(player, chargeSeconds);
            case AO -> GojoSkills.fireAo(player, chargeSeconds, castId);
            case AKA -> GojoSkills.fireAka(player, chargeSeconds, castId);
            case MURASAKI -> GojoSkills.fireMurasaki(player, chargeSeconds, castId);
            case VOID -> GojoSkills.expandVoid(player, castId);
            default -> { }
        }
        Progression.recordUse(player, skill);
        SukunaNet.pose(player, skill.netId, false, player.position());
        SukunaNet.sendFeedback(player, skill.netId, SukunaNet.CAST_OK);
        CurseManager.sync(player);
    }
}
