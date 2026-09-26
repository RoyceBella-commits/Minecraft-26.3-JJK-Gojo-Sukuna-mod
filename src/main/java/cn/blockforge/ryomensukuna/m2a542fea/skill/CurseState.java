package cn.blockforge.ryomensukuna.m2a542fea.skill;

import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent per-player technique state. All fields added by the merge are optional so pre-merge
 * saves (energy/fingers/selected only) load unchanged and are migrated to the Sukuna route.
 */
public record CurseState(float energy, int fingers, int selected, int route, int stage, int hits, int usedMask,
                         int flags, int postDomainHits, float goldHp) {
    public static final int FLAG_HEAVY = 1;
    public static final int FLAG_DOMAIN = 2;
    public static final int FLAG_INFINITY = 4;

    public static final Codec<CurseState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.optionalFieldOf("energy", 0.0f).forGetter(CurseState::energy),
        Codec.INT.optionalFieldOf("fingers", 0).forGetter(CurseState::fingers),
        Codec.INT.optionalFieldOf("selected", Skill.KAI.netId).forGetter(CurseState::selected),
        Codec.INT.optionalFieldOf("route", StageRules.NONE).forGetter(CurseState::route),
        Codec.INT.optionalFieldOf("stage", 0).forGetter(CurseState::stage),
        Codec.INT.optionalFieldOf("hits", 0).forGetter(CurseState::hits),
        Codec.INT.optionalFieldOf("used", 0).forGetter(CurseState::usedMask),
        Codec.INT.optionalFieldOf("flags", 0).forGetter(CurseState::flags),
        Codec.INT.optionalFieldOf("postDomainHits", 0).forGetter(CurseState::postDomainHits),
        Codec.FLOAT.optionalFieldOf("goldHp", -1.0f).forGetter(CurseState::goldHp)
    ).apply(i, CurseState::new));
    public static final int MAX_FINGERS = 20;
    public static final float MAX_ENERGY = 1000.0f;

    public CurseState {
        energy = Float.isFinite(energy) ? Math.max(0.0f, Math.min(MAX_ENERGY, energy)) : 0.0f;
        fingers = Math.max(0, Math.min(MAX_FINGERS, fingers));
        if (route != StageRules.GOJO && route != StageRules.SUKUNA) route = StageRules.NONE;
        if (route == StageRules.NONE && fingers > 0) {
            route = StageRules.SUKUNA;
            stage = Math.max(stage, StageRules.legacyStage(fingers));
            goldHp = -1.0f;
        }
        stage = route == StageRules.NONE ? 0 : Math.max(1, StageRules.clampStage(stage));
        float goldMax = StageRules.goldHp(stage);
        goldHp = !Float.isFinite(goldHp) || goldHp < 0.0f ? goldMax : Math.min(goldMax, goldHp);
        hits = Math.max(0, hits);
        postDomainHits = Math.max(0, postDomainHits);
        Skill sel = Skill.byId(selected);
        if (sel == null || route != StageRules.NONE && sel.route != route) {
            selected = (route == StageRules.GOJO ? Skill.AO : Skill.KAI).netId;
        }
    }

    public static CurseState fresh() {
        return new CurseState(0.0f, 0, Skill.KAI.netId, StageRules.NONE, 0, 0, 0, 0, 0, 0.0f);
    }

    public float maxEnergy() {
        return MAX_ENERGY;
    }

    public boolean awakened() {
        return this.route != StageRules.NONE;
    }

    public boolean infiniteEnergy() {
        return this.stage >= StageRules.MAX_STAGE;
    }

    public boolean flag(int f) {
        return (this.flags & f) != 0;
    }

    public float goldMax() {
        return StageRules.goldHp(this.stage);
    }

    public int unlockedMask() {
        int mask = 0;
        for (Skill s : Skill.values()) {
            if (s.route == this.route && this.stage >= s.unlockStage) mask |= 1 << s.netId;
        }
        return mask;
    }

    public boolean unlocked(Skill s) {
        return (this.unlockedMask() & 1 << s.netId) != 0;
    }

    public int usedKinds() {
        return Integer.bitCount(this.usedMask);
    }

    public boolean practiceDone() {
        return StageRules.practiceDone(this.stage, this.hits, this.usedKinds(), this.flag(FLAG_HEAVY), this.flag(FLAG_DOMAIN), this.postDomainHits);
    }

    public CurseState withEnergy(float e) {
        return new CurseState(e, fingers, selected, route, stage, hits, usedMask, flags, postDomainHits, goldHp);
    }

    public CurseState withFingers(int f) {
        return new CurseState(energy, f, selected, route, stage, hits, usedMask, flags, postDomainHits, goldHp);
    }

    public CurseState withSelected(int sel) {
        return new CurseState(energy, fingers, sel, route, stage, hits, usedMask, flags, postDomainHits, goldHp);
    }

    public CurseState withGold(float g) {
        return new CurseState(energy, fingers, selected, route, stage, hits, usedMask, flags, postDomainHits, Math.max(0.0f, g));
    }

    public CurseState withFlag(int f, boolean on) {
        return new CurseState(energy, fingers, selected, route, stage, hits, usedMask, on ? flags | f : flags & ~f, postDomainHits, goldHp);
    }

    public CurseState withUsed(Skill s) {
        return new CurseState(energy, fingers, selected, route, stage, hits, usedMask | 1 << s.netId, flags, postDomainHits, goldHp);
    }

    /** One credited practice hit; post-domain hits only accumulate after the domain flag is set. */
    public CurseState withHit() {
        return new CurseState(energy, fingers, selected, route, stage, hits + 1, usedMask, flags,
            flag(FLAG_DOMAIN) ? postDomainHits + 1 : postDomainHits, goldHp);
    }

    public CurseState withProgress(int newRoute, int newStage, float newGold) {
        return new CurseState(energy, fingers, selected, newRoute, newStage, hits, usedMask, flags, postDomainHits, newGold);
    }
}
