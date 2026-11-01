package cn.blockforge.ryomensukuna.m2a542fea.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The land inside an open domain cannot be changed by anything but the domain itself: every block
 * change there (digging, fire, pistons, fluids, endermen, other mods) is refused.
 */
@Mixin(Level.class)
public abstract class DomainLandMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void sukuna$domainLand(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (DomainTerrain.protects((Level)(Object)this, pos)) cir.setReturnValue(false);
    }

    /** destroyBlock drops its loot before it changes the block: refuse it first, so nothing is duplicated. */
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void sukuna$domainLandDestroy(BlockPos pos, boolean drop, net.minecraft.world.entity.Entity breaker, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (DomainTerrain.protects((Level)(Object)this, pos)) cir.setReturnValue(false);
    }
}
