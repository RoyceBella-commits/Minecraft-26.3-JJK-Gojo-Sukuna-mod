package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import java.util.Locale;

/**
 * Wheel skills of both routes. Ordinals are the network/save ids: the first eight keep the
 * original Sukuna ids so old saves' "selected" field stays valid; Gojo skills are appended.
 */
public enum Skill {
    KAI(4.0f, 0.35f, StageRules.SUKUNA, 1, 0.4f),
    CLEAVE(16.0f, 0.12f, StageRules.SUKUNA, 2, 3.0f),
    RCT(28.0f, 0.0f, StageRules.SUKUNA, 2, 8.0f),
    RED(30.0f, 0.55f, StageRules.SUKUNA, 3, 6.0f),
    WORLD_CUT(55.0f, 0.25f, StageRules.SUKUNA, 3, 15.0f),
    DOMAIN(90.0f, 0.0f, StageRules.SUKUNA, 4, 60.0f),
    MAHORAGA(75.0f, 0.0f, StageRules.SUKUNA, 4, 30.0f),
    CURSED_BARRAGE(22.0f, 2.5f, StageRules.SUKUNA, 2, 10.0f),
    AO(12.0f, 4.0f, StageRules.GOJO, 1, 3.0f),
    AKA(24.0f, 6.0f, StageRules.GOJO, 2, 6.0f),
    MURASAKI(120.0f, 20.0f, StageRules.GOJO, 3, 25.0f),
    GOJO_RCT(28.0f, 0.0f, StageRules.GOJO, 2, 8.0f),
    VOID(150.0f, 0.0f, StageRules.GOJO, 4, 60.0f);

    public final int netId = this.ordinal();
    public final float baseCost;
    public final float chargeCost;
    public final int route;
    public final int unlockStage;
    public final float cooldownSeconds;
    public static final float MAX_CHARGE_SECONDS = 2.5f;
    /** Murasaki must be fully assembled before release. */
    public static final float MURASAKI_MIN_CHARGE = 2.0f;
    /** Unlimited Void needs a completed hand seal. */
    public static final float VOID_MIN_CHARGE = 1.0f;
    public static final Skill[] SUKUNA_WHEEL = {KAI, CLEAVE, CURSED_BARRAGE, RCT, RED, WORLD_CUT, DOMAIN, MAHORAGA};
    public static final Skill[] GOJO_WHEEL = {AO, AKA, MURASAKI, GOJO_RCT, VOID};

    Skill(float baseCost, float chargeCost, int route, int unlockStage, float cooldownSeconds) {
        this.baseCost = baseCost;
        this.chargeCost = chargeCost;
        this.route = route;
        this.unlockStage = unlockStage;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String translationKey() {
        return "skill.sukuna." + this.name().toLowerCase(Locale.ROOT);
    }

    public float cost(float chargeSeconds) {
        return this.baseCost + this.chargeCost * Math.max(0.0f, Math.min(MAX_CHARGE_SECONDS, chargeSeconds));
    }

    public boolean isHeal() {
        return this == RCT || this == GOJO_RCT;
    }

    public boolean isDomain() {
        return this == DOMAIN || this == VOID;
    }

    /** Heavy attacks named by the stage IV practice requirement. */
    public boolean isHeavy() {
        return this == MURASAKI || this == RED || this == WORLD_CUT;
    }

    /** Skills sealed while the caster's technique is burnt out after a domain. */
    public boolean blockedByBurnout() {
        return switch (this) {
            case AO, AKA, MURASAKI, VOID, KAI, CLEAVE, RED, WORLD_CUT, DOMAIN, MAHORAGA -> true;
            default -> false;
        };
    }

    public static Skill[] wheel(int route) {
        return route == StageRules.GOJO ? GOJO_WHEEL : SUKUNA_WHEEL;
    }

    public static Skill byId(int id) {
        Skill[] v = Skill.values();
        return id >= 0 && id < v.length ? v[id] : null;
    }
}
