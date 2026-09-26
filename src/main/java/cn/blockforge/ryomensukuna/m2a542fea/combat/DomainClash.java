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
 * Active domains and their contest rules: overlap cancels sure-hit, and a caster that takes
 * half of (red max + growth gold max) in real damage during the domain loses it.
 */
public final class DomainClash {
    public interface Collapsible {
        void collapse(String reasonKey);
    }

    public static final class Domain {
        public final UUID owner;
        public final Entity entity;
        public final Vec3 center;
        public final double radius;
        public final boolean closed;
        final float threshold;
        float stagger;

        Domain(UUID owner, Entity entity, Vec3 center, double radius, boolean closed, float threshold) {
            this.owner = owner;
            this.entity = entity;
            this.center = center;
            this.radius = radius;
            this.closed = closed;
            this.threshold = threshold;
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

    /** True when the target stands inside another caster's domain: sure-hit is cancelled there. */
    public static boolean sureHitSuppressed(Entity ownDomain, Vec3 targetPos) {
        Domain own = of(ownDomain);
        for (Domain d : active()) {
            if (d.entity == ownDomain || own != null && d.owner.equals(own.owner)) continue;
            if (d.entity.level() != ownDomain.level()) continue;
            if (d.contains(targetPos)) return true;
        }
        return false;
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
