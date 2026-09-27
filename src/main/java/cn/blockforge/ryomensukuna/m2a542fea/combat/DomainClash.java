package cn.blockforge.ryomensukuna.m2a542fea.combat;

import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Active domains and their contest rules. When two rivals' domains meet it is a close contest:
 * both are cut short to 6 s (the one opened first holds 2 s longer); creatures caught in the
 * middle suffer both domains while the two casters themselves are untouched by each other's.
 * A caster that takes half of (red max + growth gold max) in real damage loses the domain.
 */
public final class DomainClash {
    public interface Collapsible {
        void collapse(String reasonKey);

        /** Ticks until this domain ends on its own. */
        int remainingTicks();

        void setRemainingTicks(int ticks);
    }

    /** Remaining time of both domains once they clash. */
    public static final int CLASH_TICKS = 120;
    /** Extra time for the domain that was opened first. */
    public static final int FIRST_BONUS_TICKS = 40;

    public static final class Domain {
        public final UUID owner;
        public final Entity entity;
        public final Vec3 center;
        public final double radius;
        public final boolean closed;
        final float threshold;
        float stagger;
        final long openedAt;
        /** Owner of the domain this one is clashing with, or null. */
        UUID rival;

        Domain(UUID owner, Entity entity, Vec3 center, double radius, boolean closed, float threshold) {
            this.owner = owner;
            this.entity = entity;
            this.center = center;
            this.radius = radius;
            this.closed = closed;
            this.threshold = threshold;
            this.openedAt = entity.level().getGameTime();
        }

        public UUID rival() {
            return this.rival;
        }

        public boolean contains(Vec3 p) {
            return p.distanceToSqr(this.center) <= this.radius * this.radius;
        }

        public float stability() {
            return Math.max(0.0f, 1.0f - this.stagger / Math.max(1.0f, this.threshold));
        }
    }

    private static final List<Domain> DOMAINS = new ArrayList<>();

    private DomainClash() {
    }

    public static void clear() {
        DOMAINS.clear();
    }

    public static Domain register(LivingEntity caster, Entity entity, Vec3 center, double radius, boolean closed) {
        unregister(entity);
        float gold = caster instanceof ServerPlayer sp ? CurseManager.of(sp).goldMax() : 0.0f;
        float threshold = (caster.getMaxHealth() + gold) * 0.5f;
        Domain d = new Domain(caster.getUUID(), entity, center, radius, closed, threshold);
        DOMAINS.add(d);
        return d;
    }

    public static void unregister(Entity entity) {
        DOMAINS.removeIf(d -> d.entity == entity);
    }

    public static Domain of(Entity entity) {
        for (Domain d : DOMAINS) if (d.entity == entity) return d;
        return null;
    }

    public static List<Domain> active() {
        DOMAINS.removeIf(d -> d.entity.isRemoved());
        return DOMAINS;
    }

    /** Owner of the rival domain this one is clashing with (its effects must spare them), or null. */
    public static UUID clashRival(Entity domain) {
        Domain d = of(domain);
        return d == null ? null : d.rival;
    }

    /** Once per server tick: two rivals' domains that meet start a clash (once per pair). */
    public static void tick() {
        List<Domain> list = new ArrayList<>(active());
        for (int i = 0; i < list.size(); ++i) {
            Domain a = list.get(i);
            for (int j = i + 1; j < list.size(); ++j) {
                Domain b = list.get(j);
                if (a.owner.equals(b.owner) || a.entity.level() != b.entity.level()) continue;
                if (a.rival != null && b.rival != null) continue;
                if (a.center.distanceTo(b.center) >= a.radius + b.radius) continue;
                a.rival = b.owner;
                b.rival = a.owner;
                shorten(a, a.openedAt < b.openedAt);
                shorten(b, b.openedAt < a.openedAt);
            }
        }
    }

    private static void shorten(Domain d, boolean first) {
        if (!(d.entity instanceof Collapsible c)) return;
        int cap = CLASH_TICKS + (first ? FIRST_BONUS_TICKS : 0);
        if (c.remainingTicks() > cap) c.setRemainingTicks(cap);
        if (d.entity.level() instanceof net.minecraft.server.level.ServerLevel level
            && level.getPlayerByUUID(d.owner) instanceof ServerPlayer p) {
            SukunaNet.actionBar(p, first ? "sukuna.hint.domain_clash_first" : "sukuna.hint.domain_clash");
        }
    }

    /** Real damage (gold + red) taken by a caster; enough of it breaks their domain. */
    public static void recordDamage(LivingEntity player, float amount) {
        if (amount <= 0.0f) return;
        for (Domain d : new ArrayList<>(active())) {
            if (!d.owner.equals(player.getUUID())) continue;
            d.stagger += amount;
            if (d.stagger >= d.threshold && d.entity instanceof Collapsible c) {
                if (player instanceof ServerPlayer sp) SukunaNet.actionBar(sp, "sukuna.hint.domain_shaken");
                c.collapse("sukuna.hint.domain_shaken");
            }
        }
    }
}
