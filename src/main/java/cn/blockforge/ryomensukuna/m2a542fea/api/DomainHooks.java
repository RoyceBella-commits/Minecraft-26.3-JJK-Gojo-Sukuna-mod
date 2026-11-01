package cn.blockforge.ryomensukuna.m2a542fea.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stable hooks for other mods' characters (a mod answers through a mixin on these methods; both
 * answer false here):
 * <ul>
 *   <li>{@link #immune}: shrugs off every domain right now: not held, frozen, pulled in or kept
 *       inside, and no sure-hit lands on it;</li>
 *   <li>{@link #hostileTo}: an enemy of the domain's caster although it is not a monster, so
 *       Unlimited Void holds it.</li>
 * </ul>
 */
public final class DomainHooks {
    private DomainHooks() {
    }

    public static boolean immune(Entity e) {
        return false;
    }

    public static boolean hostileTo(Entity e, LivingEntity owner) {
        return false;
    }
}
