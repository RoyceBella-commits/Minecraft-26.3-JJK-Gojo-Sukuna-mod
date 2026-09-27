package cn.blockforge.ryomensukuna.m2a542fea.combat;

import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** World Cut and Hollow Purple: the other side is warned, and technique NPCs answer in kind. */
public final class Finishers {
    private static final double WARN_RANGE = 128.0;

    private Finishers() {
    }

    public static void released(LivingEntity caster, boolean worldCut) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Component name = Component.translatable(worldCut ? "skill.sukuna.world_cut" : "skill.sukuna.murasaki");
        int warned = worldCut ? StageRules.GOJO : StageRules.SUKUNA;
        for (ServerPlayer viewer : level.players()) {
            if (viewer == caster || viewer.distanceToSqr(caster) > WARN_RANGE * WARN_RANGE) continue;
            if (CurseManager.of(viewer).route() != warned) continue;
            SukunaNet.actionBar(viewer, "sukuna.hint.incoming", name);
        }
        JjkNpcEntity.onFinisher(caster, worldCut);
    }
}
