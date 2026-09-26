package cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Adaptation {
    /** One adaptation stage every 3 s after the first exposure (5 stages: immune after 15 s). */
    public static final int STAGE_TICKS = 60;
    /** Stage length used by saves written before 2.1 (for migrating stored progress). */
    public static final int LEGACY_STAGE_TICKS = 6000;
    public final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    public long protectionReady;
    public long protectedUntil;

    public Entry get(String id, boolean effect) {
        return this.entries.get((effect ? "effect:" : "damage:") + id);
    }

    public Entry start(String id, boolean effect, Evolution evolution) {
        return this.entries.computeIfAbsent((effect ? "effect:" : "damage:") + id, key -> new Entry(id, effect, evolution, 0));
    }

    public int completed() {
        return (int)this.entries.values().stream().filter(e -> !e.effect && e.immune()).count();
    }

    public double attack() {
        return (double)this.completed() * 2.0;
    }

    public float healing() {
        return this.completed();
    }

    public boolean has(Evolution evolution) {
        return this.entries.values().stream().anyMatch(e -> e.evolution == evolution && e.evolved());
    }

    public List<Entry> tick() {
        ArrayList<Entry> advanced = new ArrayList<Entry>();
        for (Entry entry : this.entries.values()) {
            if (!entry.advance(1)) continue;
            advanced.add(entry);
        }
        return advanced;
    }

    public static final class Entry {
        public final String id;
        public final boolean effect;
        public final Evolution evolution;
        public int ticks;

        public Entry(String id, boolean effect, Evolution evolution, int ticks) {
            this.id = id;
            this.effect = effect;
            this.evolution = evolution;
            this.ticks = Math.max(0, Math.min(this.maximum(), ticks));
        }

        public int baseStages() {
            return this.effect ? 1 : 5;
        }

        public int maximum() {
            return (this.baseStages() + (this.evolution == Evolution.NONE ? 0 : 1)) * STAGE_TICKS;
        }

        public int stage() {
            return Math.min(this.baseStages(), this.ticks / STAGE_TICKS);
        }

        public boolean immune() {
            return this.ticks >= this.baseStages() * STAGE_TICKS;
        }

        public boolean evolved() {
            return this.evolution != Evolution.NONE && this.ticks >= this.maximum();
        }

        public float multiplier() {
            return this.effect ? (float)(!this.immune() ? 1 : 0) : (float)(5 - this.stage()) / 5.0f;
        }

        public boolean advance(int amount) {
            int previous = this.ticks / STAGE_TICKS;
            this.ticks = (int)Math.min((long)this.maximum(), (long)this.ticks + (long)Math.max(0, amount));
            return previous != this.ticks / STAGE_TICKS;
        }
    }

    public static enum Evolution {
        NONE("\u5b8c\u5168\u9002\u5e94"),
        WATER("\u6df1\u6d77\u9002\u5e94"),
        LAVA("\u7194\u5ca9\u884c\u8005"),
        HEAT("\u5fa1\u706b\u4e4b\u8eaf"),
        FLIGHT("\u7ffc\u5316\u751f\u5b58"),
        NIGHT("\u611f\u77e5\u8fdb\u5316"),
        FOOD("\u4ee3\u8c22\u8fdb\u5316"),
        FROST("\u971c\u5bd2\u4e4b\u8eaf"),
        VITALITY("\u4e0d\u5c48\u4e4b\u8eaf"),
        PROJECTILE("\u5f39\u9053\u9002\u5e94"),
        REGEN("\u518d\u751f\u8fdb\u5316"),
        SOUL("\u7075\u9b42\u5730\u9762\u9002\u5e94"),
        VOID("\u865a\u7a7a\u5f52\u8fd8");

        public final String title;

        private Evolution(String title) {
            this.title = title;
        }
    }
}

