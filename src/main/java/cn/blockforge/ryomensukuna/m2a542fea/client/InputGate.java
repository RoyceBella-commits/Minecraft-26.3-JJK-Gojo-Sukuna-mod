package cn.blockforge.ryomensukuna.m2a542fea.client;

/** Requires a release after entering an eligible context; drains clicks while disabled. */
public final class InputGate {
    private boolean armed;
    private boolean down;

    public boolean update(boolean enabled, boolean held, boolean clicked) {
        if (!enabled) {
            reset();
            return false;
        }
        if (!armed) {
            if (!held && !clicked) armed = true;
            down = held;
            return false;
        }
        boolean edge = !down && (held || clicked);
        down = held;
        return edge;
    }

    public boolean held() { return armed && down; }
    public void reset() { armed = false; down = false; }
}
