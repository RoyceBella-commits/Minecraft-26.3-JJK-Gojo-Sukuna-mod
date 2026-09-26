package cn.blockforge.ryomensukuna.m2a542fea.client.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.client.SixEyesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Six Eyes: enemies the Gojo player can see are outlined, including in the dark. */
@Mixin(Minecraft.class)
public abstract class SixEyesGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void sukuna$sixEyes(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SixEyesClient.marked(entity)) {
            cir.setReturnValue(true);
        }
    }
}
