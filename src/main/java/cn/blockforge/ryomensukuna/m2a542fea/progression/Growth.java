package cn.blockforge.ryomensukuna.m2a542fea.progression;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash;
import cn.blockforge.ryomensukuna.m2a542fea.skill.ChannelCasting;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Growth gold hearts and innate armor; values are stage totals, never stacked per use. */
public final class Growth {
    public static final Identifier GROWTH_ARMOR = SukunaMod.id("growth_armor");
    /** Pre-2.1 Sukuna-only +3 attack bonus; removed from players on sight. */
    public static final Identifier CURSED_STRENGTH = SukunaMod.id("cursed_strength");
    /** Bare-handed strike bonus; only present while the main hand is empty. */
    public static final Identifier UNARMED = SukunaMod.id("unarmed");
    private static final float PASSIVE_RCT_HEAL = 1.0f;
    private static final float PASSIVE_RCT_COST = 0.5f;
    private static final int REGEN_DELAY_TICKS = 200;
    private static final int REGEN_INTERVAL_TICKS = 40;
    private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();

    private Growth() {
    }

    public static void clear() {
        LAST_HURT.clear();
    }

    public static void applyAttributes(ServerPlayer player) {
        CurseState s = CurseManager.of(player);
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            float value = StageRules.armor(s.stage());
            AttributeModifier current = armor.getModifier(GROWTH_ARMOR);
            if (!s.awakened()) {
                armor.removeModifier(GROWTH_ARMOR);
            } else if (current == null || current.amount() != value) {
                armor.addOrReplacePermanentModifier(new AttributeModifier(GROWTH_ARMOR, value, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.hasModifier(CURSED_STRENGTH)) {
            attack.removeModifier(CURSED_STRENGTH);
        }
        updateUnarmed(player, s);
    }

    /** Awakened fists hit like the stage table says, but only while nothing is held in the main hand. */
    private static void updateUnarmed(ServerPlayer player, CurseState s) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        boolean bare = s.awakened() && player.getMainHandItem().isEmpty();
        if (!bare) {
            if (attack.hasModifier(UNARMED)) attack.removeModifier(UNARMED);
            return;
        }
        double bonus = StageRules.unarmedDamage(s.stage()) - 1.0;
        AttributeModifier current = attack.getModifier(UNARMED);
        if (current == null || current.amount() != bonus) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(UNARMED, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * Called with the health a hit is about to remove (after armor and vanilla absorption).
     * Growth gold takes it first; returns the part left for red health.
     */
    public static float absorb(ServerPlayer player, float healthLoss) {
        if (healthLoss <= 0.0f) return healthLoss;
        CurseState s = CurseManager.of(player);
        float[] split = StageRules.splitDamage(s.awakened() ? s.goldHp() : 0.0f, healthLoss);
        float red = Math.min(split[1], player.getHealth());
        if (split[0] > 0.0f) {
            CurseManager.setState(player, s.withGold(s.goldHp() - split[0]));
            player.level().sendParticles(new DustParticleOptions(0xFFD34A, 0.9f), player.getX(), player.getY() + 1.1, player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        }
        LAST_HURT.put(player.getUUID(), player.level().getGameTime());
        DomainClash.recordDamage(player, split[0] + red);
        if (split[0] > 0.0f) CurseManager.sync(player);
        return split[1];
    }

    public static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now % 20L == 0L) {
            applyAttributes(player);
        }
        CurseState s = CurseManager.of(player);
        updateUnarmed(player, s);
        if (!s.awakened() || !player.isAlive() || ChannelCasting.healing(player)) return;
        if (now % 20L == 0L) {
            passiveReverse(player, s);
            s = CurseManager.of(player);
        }
        if (s.goldHp() >= s.goldMax()) return;
        long last = LAST_HURT.getOrDefault(player.getUUID(), 0L);
        if (now - last >= REGEN_DELAY_TICKS && (now - last) % REGEN_INTERVAL_TICKS == 0L) {
            CurseManager.setState(player, s.withGold(Math.min(s.goldMax(), s.goldHp() + 2.0f)));
            CurseManager.sync(player);
        }
    }

    /** Reverse technique is always on once learned (stage II): a slow trickle of healing. */
    private static void passiveReverse(ServerPlayer player, CurseState s) {
        if (s.stage() < 2) return;
        boolean hurt = player.getHealth() < player.getMaxHealth() - 0.001f || s.goldHp() < s.goldMax() - 0.001f;
        if (!hurt || !CurseManager.spend(player, PASSIVE_RCT_COST)) return;
        heal(player, PASSIVE_RCT_HEAL);
        CurseManager.sync(player);
    }

    /** Reverse technique: red health first, then growth gold. Returns false once both are full. */
    public static boolean heal(ServerPlayer player, float amount) {
        float missingRed = player.getMaxHealth() - player.getHealth();
        if (missingRed > 0.001f) {
            float red = Math.min(missingRed, amount);
            player.heal(red);
            amount -= red;
        }
        CurseState s = CurseManager.of(player);
        if (amount > 0.0f && s.goldHp() < s.goldMax()) {
            CurseManager.setState(player, s.withGold(Math.min(s.goldMax(), s.goldHp() + amount)));
        }
        CurseState after = CurseManager.of(player);
        return player.getHealth() < player.getMaxHealth() - 0.001f || after.goldHp() < after.goldMax() - 0.001f;
    }

    public static void onRespawn(ServerPlayer player) {
        CurseState s = CurseManager.of(player);
        CurseManager.setState(player, s.withGold(s.goldMax()));
        LAST_HURT.remove(player.getUUID());
        applyAttributes(player);
        CurseManager.sync(player);
    }
}
