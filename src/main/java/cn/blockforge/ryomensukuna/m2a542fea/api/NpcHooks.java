package cn.blockforge.ryomensukuna.m2a542fea.api;

import net.minecraft.world.entity.Entity;

/**
 * Stable hook for other mods (a mod answers through a mixin on this method; it answers false here):
 * {@link #restrained} says a technique NPC is held right now (bound by chains, say), so it neither
 * moves with its mobility nor uses its follow-ups, dodges or reverse cursed technique.
 */
public final class NpcHooks {
    private NpcHooks() {
    }

    public static boolean restrained(Entity npc) {
        return false;
    }
}
