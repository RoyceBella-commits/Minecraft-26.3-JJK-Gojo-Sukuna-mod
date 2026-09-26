package cn.blockforge.ryomensukuna.m2a542fea.gojo;

import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import net.minecraft.server.level.ServerPlayer;

/** Server half of the Six Eyes: warns nearby Gojo players of dangerous wind-ups. */
public final class SixEyes {
    private static final double WARN_RANGE_SQ = 64.0 * 64.0;

    private SixEyes() {
    }

    public static void warn(ServerPlayer caster, Skill skill) {
        for (ServerPlayer viewer : caster.level().players()) {
            if (viewer == caster || viewer.distanceToSqr(caster) > WARN_RANGE_SQ) continue;
            if (CurseManager.of(viewer).route() != StageRules.GOJO) continue;
            SukunaNet.danger(viewer, skill, caster.position());
        }
    }
}
