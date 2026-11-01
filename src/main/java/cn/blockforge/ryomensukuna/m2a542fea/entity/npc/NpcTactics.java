package cn.blockforge.ryomensukuna.m2a542fea.entity.npc;

/**
 * The fight rules of a technique NPC that need no game classes, so the build can check them: when a
 * foe it fights at range has chased it down for good (it turns and fights hand to hand), when it is
 * stuck below or behind cover (it uses its mobility to get out), and the launch of a leap that lands
 * where it aims.
 */
public final class NpcTactics {
    /** A foe this close (blocks) is on top of a sorcerer that wants range. */
    public static final double CLOSE = 5.0;
    /** A first close-in is answered after this many ticks: a break-away if mobility is ready. */
    public static final int REACT_TICKS = 6;
    /** Kept that close this long (1.5 s), the sorcerer stops backing off and fights back. */
    public static final int CLOSE_LIMIT = 30;
    /** A second close-in within this window (10 s) of the first is a chase: it fights back at once. */
    public static final int CHASE_WINDOW = 200;
    /** Hand-to-hand counter against a chaser (4 s), dropped early once the foe backs off this far. */
    public static final int COUNTER_TICKS = 80;
    public static final double COUNTER_DROP = 10.0;
    /** Checks (every 10 ticks) without progress while cut off from the foe before mobility is used. */
    public static final int STUCK_LIMIT = 30;
    /** Getting this much closer, or moving this far since the last check, is progress. */
    public static final double PROGRESS = 1.0;
    public static final double MOVED = 2.5;
    /**
     * Mob physics in the air: gravity, vertical drag, horizontal friction (per tick). On the launch
     * tick the leaper still stands on the ground, so that tick's horizontal friction is the ground's
     * (0.6 x 0.91).
     */
    public static final double GRAVITY = 0.08;
    public static final double DRAG_Y = 0.98;
    public static final double FRICTION = 0.91;
    public static final double GROUND_FRICTION = 0.6 * 0.91;
    /** Launch limits: a rise of about 13 blocks, a reach of about 16. */
    public static final double MAX_UP = 1.6;
    public static final double MAX_FLAT = 2.4;

    public enum Reaction { NONE, BREAK_AWAY, COUNTER }

    private int closeTicks;
    private int closeIns;
    private long firstCloseIn = -1_000_000L;
    private int stuck;
    private double best = Double.MAX_VALUE;

    /** A new foe (or none): nothing carries over. */
    public void reset() {
        this.closeTicks = 0;
        this.closeIns = 0;
        this.firstCloseIn = -1_000_000L;
        this.stuck = 0;
        this.best = Double.MAX_VALUE;
    }

    /** A hand-to-hand window starts: the current close-in streak is over (earlier close-ins still count). */
    public void calm() {
        this.closeTicks = 0;
    }

    /**
     * One tick of keeping range. The first time the foe closes in, the sorcerer breaks away (when its
     * mobility is ready) or backs off; a foe that stays on it for 1.5 s, or closes in again within
     * 10 s, has chased it down and is fought hand to hand.
     */
    public Reaction pressed(boolean close, boolean mobilityReady, long now) {
        if (!close) {
            this.closeTicks = 0;
            return Reaction.NONE;
        }
        if (this.closeTicks++ == 0) {
            if (now - this.firstCloseIn > CHASE_WINDOW) {
                this.firstCloseIn = now;
                this.closeIns = 0;
            }
            ++this.closeIns;
        }
        if (this.closeTicks >= CLOSE_LIMIT || this.closeIns >= 2 && this.closeTicks >= REACT_TICKS) {
            this.closeTicks = 0;
            this.closeIns = 0;
            this.firstCloseIn = -1_000_000L;
            return Reaction.COUNTER;
        }
        return this.closeIns == 1 && this.closeTicks >= REACT_TICKS && mobilityReady ? Reaction.BREAK_AWAY : Reaction.NONE;
    }

    /**
     * Every 10 ticks: {@code cutOff} = the foe can neither be reached on foot nor (where that matters)
     * seen. True once that has lasted {@link #STUCK_LIMIT} ticks without getting closer or moving
     * about, and mobility is ready to get out; the count then starts again.
     */
    public boolean stuck(boolean cutOff, double distance, double moved, boolean mobilityReady) {
        if (!cutOff || moved >= MOVED || distance < this.best - PROGRESS) {
            this.stuck = 0;
            this.best = distance;
            return false;
        }
        this.stuck = Math.min(this.stuck + 10, STUCK_LIMIT);
        if (this.stuck < STUCK_LIMIT || !mobilityReady) return false;
        this.stuck = 0;
        this.best = distance;
        return true;
    }

    /**
     * Launch velocity {x, y, z} for a leap from the ground to a point {@code (dx, dy, dz)} away that
     * rises at least {@code arc} blocks above the higher end. Speeds are capped, so a point very far or
     * very high is not quite reached.
     */
    public static double[] leap(double dx, double dy, double dz, double arc) {
        double rise = Math.max(0.0, dy) + Math.max(0.5, arc);
        double up = 0.3;
        while (up < MAX_UP && apex(up) < rise) up += 0.02;
        up = Math.min(up, MAX_UP);
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0E-6) return new double[]{0.0, up, 0.0};
        int ticks = airTicks(up, Math.min(dy, apex(up)));
        double speed = Math.min(MAX_FLAT, flat / reach(1.0, ticks));
        return new double[]{dx / flat * speed, up, dz / flat * speed};
    }

    /** Highest point (blocks above the start) of a jump launched upward at {@code up}. */
    public static double apex(double up) {
        double y = 0.0, v = up, top = 0.0;
        for (int t = 0; t < 400 && v > 0.0; ++t) {
            y += v;
            top = Math.max(top, y);
            v = (v - GRAVITY) * DRAG_Y;
        }
        return top;
    }

    /** Ticks in the air until a jump launched upward at {@code up} comes back down to {@code dy}. */
    public static int airTicks(double up, double dy) {
        double y = 0.0, v = up;
        for (int t = 1; t <= 400; ++t) {
            y += v;
            v = (v - GRAVITY) * DRAG_Y;
            if (v < 0.0 && y <= dy) return t;
        }
        return 400;
    }

    /** Horizontal distance covered in {@code ticks} by a launch from the ground at {@code speed}. */
    public static double reach(double speed, int ticks) {
        if (ticks <= 0) return 0.0;
        return speed * (1.0 + GROUND_FRICTION * (1.0 - Math.pow(FRICTION, ticks - 1)) / (1.0 - FRICTION));
    }
}
