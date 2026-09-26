package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;

public final class ChargeSession {
    private boolean down;
    private int skill = -1;
    private long started;

    public Cast tick(boolean held, boolean pressed, boolean allowed, int selected, long now) {
        boolean edge = !this.down && (held || pressed);
        this.down = held;
        if (!allowed) {
            this.cancel();
            return null;
        }
        if (edge && !this.isCharging() && Skill.byId(selected) != null) {
            this.skill = selected;
            this.started = now;
        }
        if (this.isCharging() && !held) {
            Cast result = new Cast(this.skill, Math.max(0.05f, this.seconds(now)));
            this.cancel();
            return result;
        }
        return null;
    }

    public boolean isCharging() {
        return this.skill >= 0;
    }

    public int skillId() {
        return this.skill;
    }

    public float seconds(long now) {
        return this.isCharging() ? Math.max(0.0f, Math.min(2.5f, (float)(now - this.started) / 1.0E9f)) : 0.0f;
    }

    public void cancel() {
        this.skill = -1;
    }

    public void reset() {
        this.cancel();
        this.down = false;
    }

    public record Cast(int skillId, float seconds) {
    }
}

