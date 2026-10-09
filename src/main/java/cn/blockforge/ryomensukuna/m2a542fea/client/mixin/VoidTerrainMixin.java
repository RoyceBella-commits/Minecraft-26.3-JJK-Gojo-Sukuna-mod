package cn.blockforge.ryomensukuna.m2a542fea.client.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.client.VoidSight;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inside Unlimited Void there is no ground: no terrain is drawn, only the cosmos around the viewer. */
@Mixin(ChunkSectionsToRender.class)
public abstract class VoidTerrainMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
    private void sukuna$voidHidesTerrain(CallbackInfo ci) {
        if (VoidSight.hidesTerrain()) ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void sukuna$voidHidesTranslucentTerrain(CallbackInfo ci) {
        if (VoidSight.hidesTerrain()) ci.cancel();
    }
}
