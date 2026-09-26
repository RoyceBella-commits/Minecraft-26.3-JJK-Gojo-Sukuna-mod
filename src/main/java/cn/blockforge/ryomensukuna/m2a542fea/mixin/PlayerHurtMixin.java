package cn.blockforge.ryomensukuna.m2a542fea.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.progression.Growth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Growth gold hearts: after armor, resistance and vanilla (temporary) absorption have been
 * applied, the remaining health loss is taken from growth gold before red health.
 */
@Mixin(Player.class)
public abstract class PlayerHurtMixin {
    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"), index = 0)
    private float sukuna$growthGold(float newHealth) {
        Player self = (Player)(Object)this;
        if (!(self instanceof ServerPlayer player)) {
            return newHealth;
        }
        float loss = player.getHealth() - newHealth;
        if (loss <= 0.0f) {
            return newHealth;
        }
        return player.getHealth() - Growth.absorb(player, loss);
    }
}
