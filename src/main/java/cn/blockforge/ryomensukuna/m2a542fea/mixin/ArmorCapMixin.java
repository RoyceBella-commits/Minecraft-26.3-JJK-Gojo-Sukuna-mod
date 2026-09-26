package cn.blockforge.ryomensukuna.m2a542fea.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.progression.Growth;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Awakened players: innate + worn armor together count for at most 20 points. */
@Mixin(LivingEntity.class)
public abstract class ArmorCapMixin {
    @Inject(method = "getArmorValue", at = @At("RETURN"), cancellable = true)
    private void sukuna$capArmor(CallbackInfoReturnable<Integer> cir) {
        if (!((Object)this instanceof Player player)) {
            return;
        }
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.hasModifier(Growth.GROWTH_ARMOR)) {
            cir.setReturnValue((int)StageRules.cappedArmor(cir.getReturnValueI()));
        }
    }
}
