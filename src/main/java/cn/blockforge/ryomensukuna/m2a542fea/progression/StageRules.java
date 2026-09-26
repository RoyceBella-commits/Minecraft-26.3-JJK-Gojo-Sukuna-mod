package cn.blockforge.ryomensukuna.m2a542fea.progression;

/** Pure progression tables and rules; no game classes so they can be checked headlessly. */
public final class StageRules {
    public static final int NONE = 0;
    public static final int GOJO = 1;
    public static final int SUKUNA = 2;
    public static final int MAX_STAGE = 5;
    public static final float MAX_ARMOR_TOTAL = 20.0f;

    /** "Three times the range" is read as three times the volume: radius x cbrt(3). */
    public static final double VOLUME_X3 = Math.cbrt(3.0);
    /** "Twice the range" as twice the volume: radius x cbrt(2). */
    public static final double VOLUME_X2 = Math.cbrt(2.0);
    /** Both domains share one radius: the 2.1 Void (20 x cbrt 3) doubled in volume, i.e. 20 x cbrt 6 (~36.3). */
    public static final double DOMAIN_RADIUS = 20.0 * Math.cbrt(6.0);
    /** Lowest share of damage a stage V sorcerer still takes: 1% in general, 10% from Gojo / Sukuna sorcerers. */
    public static final float MIN_TAKEN = 0.01f;
    public static final float MIN_TAKEN_FROM_SORCERER = 0.10f;
    public static final float MAX_BLACK_FLASH = 0.2f;
    public static final float BLACK_FLASH_MULTIPLIER = 3.0f;
    public static final int DOMAIN_TICKS = 400;
    public static final int DOMAIN_TICKS_MAX = 600;

    private static final float[] GOLD_HP = {0.0f, 20.0f, 30.0f, 40.0f, 50.0f, 60.0f};
    private static final float[] ARMOR = {0.0f, 10.0f, 12.0f, 14.0f, 16.0f, 20.0f};
    private static final float[] UNARMED = {1.0f, 8.0f, 11.0f, 14.0f, 17.0f, 20.0f};
    private static final float[] BLACK_FLASH = {0.0f, 0.04f, 0.08f, 0.12f, 0.16f, 0.2f};

    public enum UseResult { AWAKENED, ADVANCED, WRONG_ROUTE, NOT_READY, MAXED }

    private StageRules() {
    }

    public static float goldHp(int stage) {
        return GOLD_HP[clampStage(stage)];
    }

    public static float armor(int stage) {
        return ARMOR[clampStage(stage)];
    }

    /** Bare-handed melee damage (the vanilla fist deals 1). */
    public static float unarmedDamage(int stage) {
        return UNARMED[clampStage(stage)];
    }

    /** Chance that a full-strength bare-handed hit becomes a Black Flash (x3 damage), at most 20%. */
    public static float blackFlashChance(int stage) {
        return Math.min(MAX_BLACK_FLASH, BLACK_FLASH[clampStage(stage)]);
    }

    /** Domain duration once fully expanded: 20 s at stage IV, 30 s at stage V. */
    public static int domainTicks(int stage) {
        return clampStage(stage) >= MAX_STAGE ? DOMAIN_TICKS_MAX : DOMAIN_TICKS;
    }

    /** Share of incoming damage actually taken at a stage; falls linearly to 1% (10% between sorcerers) at stage V. */
    public static float takenFraction(int stage, boolean fromSorcerer) {
        float floor = fromSorcerer ? MIN_TAKEN_FROM_SORCERER : MIN_TAKEN;
        return 1.0f - (1.0f - floor) * clampStage(stage) / (float)MAX_STAGE;
    }

    public static int clampStage(int stage) {
        return Math.max(0, Math.min(MAX_STAGE, stage));
    }

    /** Stage granted to a pre-merge save from its finger count; the original unlocks are all contained. */
    public static int legacyStage(int fingers) {
        if (fingers <= 0) return 0;
        if (fingers == 1) return 1;
        if (fingers <= 9) return 2;
        if (fingers <= 16) return 3;
        return 4;
    }

    /** Whether the practice for leaving {@code stage} (towards stage + 1) is complete. */
    public static boolean practiceDone(int stage, int hits, int usedSkillKinds, boolean heavyDone, boolean domainDone, int postDomainHits) {
        return switch (stage) {
            case 1 -> hits >= 5;
            case 2 -> hits >= 15 && usedSkillKinds >= 2;
            case 3 -> hits >= 25 && heavyDone;
            case 4 -> domainDone && postDomainHits >= 5;
            default -> false;
        };
    }

    public static UseResult evaluateUse(int currentRoute, int itemRoute, int stage, boolean practiceDone) {
        if (currentRoute == NONE || stage <= 0) return UseResult.AWAKENED;
        if (currentRoute != itemRoute) return UseResult.WRONG_ROUTE;
        if (stage >= MAX_STAGE) return UseResult.MAXED;
        return practiceDone ? UseResult.ADVANCED : UseResult.NOT_READY;
    }

    /** Gold capacity added by an upgrade; previously lost gold is not refilled. */
    public static float goldAfterUpgrade(float currentGold, int oldStage, int newStage) {
        if (oldStage <= 0) return goldHp(newStage);
        return Math.min(goldHp(newStage), currentGold + goldHp(newStage) - goldHp(oldStage));
    }

    /** Splits a health loss between growth gold and red health. Returns {goldUsed, redUsed}. */
    public static float[] splitDamage(float gold, float loss) {
        if (loss <= 0.0f) return new float[]{0.0f, 0.0f};
        float fromGold = Math.min(Math.max(0.0f, gold), loss);
        return new float[]{fromGold, loss - fromGold};
    }

    public static float cappedArmor(float armor) {
        return Math.min(MAX_ARMOR_TOTAL, armor);
    }

    public static String roman(int stage) {
        return switch (clampStage(stage)) {
            case 1 -> "Ⅰ";
            case 2 -> "Ⅱ";
            case 3 -> "Ⅲ";
            case 4 -> "Ⅳ";
            case 5 -> "Ⅴ";
            default -> "-";
        };
    }
}
