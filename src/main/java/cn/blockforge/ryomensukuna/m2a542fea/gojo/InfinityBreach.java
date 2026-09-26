package cn.blockforge.ryomensukuna.m2a542fea.gojo;

/**
 * Pure bookkeeping for breaking one sorcerer's Infinity: three hits of the same kind (no more
 * than 10 s apart) break that kind for 5 s; the third hit already gets through. Repeatable.
 */
public final class InfinityBreach {
    public enum Category { SLASH, FLAME, FIST, MAHORAGA }

    public enum Result { OPEN, BROKE, COUNTED }

    public static final int HITS_TO_BREAK = 3;
    public static final long OPEN_TICKS = 100L;
    public static final long RESET_TICKS = 200L;
    private final int[] counts = new int[Category.values().length];
    private final long[] lastHit = new long[Category.values().length];
    private final long[] openUntil = new long[Category.values().length];

    public InfinityBreach() {
        java.util.Arrays.fill(this.lastHit, Long.MIN_VALUE / 2);
        java.util.Arrays.fill(this.openUntil, Long.MIN_VALUE / 2);
    }

    public boolean open(Category c, long now) {
        return now < this.openUntil[c.ordinal()];
    }

    public int count(Category c) {
        return this.counts[c.ordinal()];
    }

    /** Registers a hit of this kind at {@code now}; OPEN and BROKE pass through Infinity, COUNTED is blocked. */
    public Result hit(Category c, long now) {
        int i = c.ordinal();
        if (now < this.openUntil[i]) return Result.OPEN;
        if (now - this.lastHit[i] > RESET_TICKS) this.counts[i] = 0;
        this.lastHit[i] = now;
        if (++this.counts[i] >= HITS_TO_BREAK) {
            this.counts[i] = 0;
            this.openUntil[i] = now + OPEN_TICKS;
            return Result.BROKE;
        }
        return Result.COUNTED;
    }
}
