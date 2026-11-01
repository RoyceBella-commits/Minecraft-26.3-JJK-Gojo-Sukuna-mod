package cn.blockforge.ryomensukuna.m2a542fea.combat;

/**
 * NPC-versus-NPC rules, lined up with the Fate and Devil May Cry NPCs. Pure numbers and functions,
 * so the build can check them. They only apply when an NPC fights an NPC: no single blow from
 * another NPC takes more than {@link #HIT_CAP} of a technique NPC's health, World Cut and Hollow
 * Purple between NPCs take {@link #FINISHER_LOSS}, and a technique NPC heals slowly in a fight,
 * with a channelled reverse cursed technique once badly hurt. Players fight by the old rules.
 */
public final class DuelRules {
    private DuelRules() {
    }

    /** No single blow from another NPC takes more than this share of a technique NPC's max health. */
    public static final float HIT_CAP = 0.35f;
    /** World Cut / Hollow Purple from an NPC on a technique NPC: this share of its whole health. */
    public static final float FINISHER_LOSS = 0.30f;
    /** The fused Purple between NPCs. */
    public static final float FUSED_LOSS = 0.15f;
    /** The finishers come round this often for the NPCs (40 s). */
    public static final int FINISHER_COOLDOWN = 800;
    /** The Gojo NPC's fused Purple against another NPC (Mahoraga aside): no more often than this (45 s). */
    public static final int FUSED_COOLDOWN = 900;

    /** Health a second while fighting, and at rest (no target and unhurt for {@link #RESTING_AFTER}). */
    public static final float REGEN_FIGHTING = 0.5f;
    public static final float REGEN_RESTING = 2.5f;
    public static final int RESTING_AFTER = 100;

    /** Reverse cursed technique: below this share of health, channelled this long, heals this much. */
    public static final float RCT_BELOW = 0.4f;
    public static final int RCT_CHANNEL = 30;
    public static final float RCT_HEAL = 50.0f;
    public static final int RCT_COOLDOWN = 600;
    /** A channel broken by a blow of at least {@link #RCT_BREAK} health is tried again after this long. */
    public static final int RCT_INTERRUPTED_COOLDOWN = 300;
    public static final float RCT_BREAK = 10.0f;

    /**
     * A technique NPC's blows on the Fate spirits (200 health behind 150 gold hearts and a 10% share):
     * Gojo's, and Sukuna's, who has no Infinity to stand behind while he trades blows.
     */
    public static final float GOJO_VS_FATE = 3.0f;
    public static final float SUKUNA_VS_FATE = 5.0f;
    /** ... and on the Devil May Cry brothers. */
    public static final float VS_DMC = 1.0f;

    /** An opponent's finisher is answered in kind this often, only if one's own is ready within this long. */
    public static final float COUNTER_CHANCE = 0.5f;
    public static final int COUNTER_READY_WITHIN = 200;

    /** Recovery (ticks) after a light technique (Ao shot, Dismantle, barrage) and a medium one (Aka, Cleave, Flame Arrow). */
    public static final int LIGHT_MIN = 30;
    public static final int LIGHT_MAX = 60;
    public static final int MEDIUM_MIN = 60;
    public static final int MEDIUM_MAX = 100;

    /** Health lost to one blow from another NPC, capped. */
    public static float capped(float lost, float maxHealth) {
        return Math.min(lost, maxHealth * HIT_CAP);
    }

    /** Share of a technique NPC's whole health a World Cut / Hollow Purple from an NPC takes. */
    public static float finisherShare(boolean fused) {
        return fused ? FUSED_LOSS : FINISHER_LOSS;
    }

    public static float vsFate(boolean gojo) {
        return gojo ? GOJO_VS_FATE : SUKUNA_VS_FATE;
    }

    public static float regen(boolean resting) {
        return resting ? REGEN_RESTING : REGEN_FIGHTING;
    }

    /** A recovery between {@code min} and {@code max} from a roll in [0, 1). */
    public static int recovery(int min, int max, float roll01) {
        return min + Math.round((max - min) * Math.max(0.0f, Math.min(1.0f, roll01)));
    }

    /**
     * Weight of a technique that was just used {@code repeats} times in a row: halved each time, so
     * the same move twice is rare and three times rarer still.
     */
    public static float repeatWeight(float weight, int repeats) {
        return weight / (float)(1 << Math.max(0, Math.min(repeats, 8)));
    }

    /** Index picked from {@code weights} by a roll in [0, 1); -1 if all weights are zero. */
    public static int pick(float roll01, float... weights) {
        float total = 0.0f;
        for (float w : weights) total += Math.max(0.0f, w);
        if (total <= 0.0f) return -1;
        float r = roll01 * total;
        for (int i = 0; i < weights.length; ++i) {
            float w = Math.max(0.0f, weights[i]);
            if (r < w) return i;
            r -= w;
        }
        for (int i = weights.length - 1; i >= 0; --i) if (weights[i] > 0.0f) return i;
        return -1;
    }

    /**
     * Lead for a shot at {@code speed} blocks a tick at a foe {@code distance} away moving at
     * {@code (vx, vy, vz)} a tick: where to aim relative to its centre, at most {@code maxLead} blocks.
     */
    public static double[] lead(double distance, double speed, double vx, double vy, double vz, double maxLead) {
        if (speed <= 1.0E-3) return new double[]{0.0, 0.0, 0.0};
        double t = distance / speed;
        double x = vx * t, y = vy * t * 0.5, z = vz * t;
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len > maxLead && len > 1.0E-6) {
            double k = maxLead / len;
            x *= k;
            y *= k;
            z *= k;
        }
        return new double[]{x, y, z};
    }
}
