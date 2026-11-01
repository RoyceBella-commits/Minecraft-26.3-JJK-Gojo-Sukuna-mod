package cn.blockforge.ryomensukuna.m2a542fea.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainTerrain;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explosions leave the land of an open domain whole (and so drop nothing from it). */
@Mixin(ServerExplosion.class)
public abstract class DomainExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void sukuna$spareDomainLand(CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> blown = cir.getReturnValue();
        if (blown == null || blown.isEmpty()) return;
        List<BlockPos> kept = new ArrayList<>(blown.size());
        for (BlockPos p : blown) {
            if (!DomainTerrain.protects(this.level, p)) kept.add(p);
        }
        if (kept.size() != blown.size()) cir.setReturnValue(kept);
    }
}
