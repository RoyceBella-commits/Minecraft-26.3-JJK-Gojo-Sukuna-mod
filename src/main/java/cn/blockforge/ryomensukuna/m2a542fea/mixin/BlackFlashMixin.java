package cn.blockforge.ryomensukuna.m2a542fea.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.combat.BlackFlash;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Incoming damage rules: a rolled Black Flash lands at three times its damage, then the target's
 * growth toughness decides how much of it is actually taken.
 */
@Mixin(LivingEntity.class)
public abstract class BlackFlashMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float sukuna$blackFlash(float amount, @Local(argsOnly = true) DamageSource source) {
        float dealt = BlackFlash.modify(source, amount);
        return cn.blockforge.ryomensukuna.m2a542fea.combat.DamageTaken.apply((net.minecraft.world.entity.LivingEntity)(Object)this, source, dealt);
    }
}
