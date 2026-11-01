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
    /** Share of a Gojo / Sukuna character's whole health (red + growth gold) one World Cut or Purple takes. */
    public static final float CHARACTER_LOSS = 0.75f;
    /** True while such a strike lands: its amount is the loss itself, no stage or NPC shares apply. */
    private static boolean exact;

    /** Gojo / Sukuna characters: awakened players and the technique NPCs (not Mahoraga, not other mods). */
    public static boolean character(LivingEntity e) {
        if (e instanceof JjkNpcEntity) return true;
        return e instanceof ServerPlayer p && CurseManager.of(p).awakened();
    }

    public static boolean exact() {
        return exact;
    }

    /**
     * World Cut or Hollow Purple on a Gojo / Sukuna character: takes {@code share} of its whole health
     * (75%, the fused Purple half that) whatever its growth stage. Sukuna's wheel may still have adapted
     * to it; armour only counts for damage types that do not pass through it.
     */
    public static void strikeCharacter(ServerLevel level, net.minecraft.world.entity.Entity attacker, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, int castId, float share) {
        float whole = target.getMaxHealth() + (target instanceof ServerPlayer p ? CurseManager.of(p).goldMax() : 0.0f);
        // NPC against NPC: a heavy blow, no longer the end of the fight.
        if (target instanceof JjkNpcEntity && !(attacker instanceof net.minecraft.world.entity.player.Player)) {
            share = DuelRules.finisherShare(share < CHARACTER_LOSS);
        }
        exact = true;
        try {
            CurseManager.damage(level, attacker, target, whole * share, source, castId);
        } finally {
            exact = false;
        }
    }
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
