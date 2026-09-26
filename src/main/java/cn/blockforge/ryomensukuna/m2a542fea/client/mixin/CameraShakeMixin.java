package cn.blockforge.ryomensukuna.m2a542fea.client.mixin;
import cn.blockforge.ryomensukuna.m2a542fea.client.CastVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow private Entity entity;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("TAIL"))
    private void sukuna$shake(DeltaTracker tracker,CallbackInfo ci) {
        float delta=tracker.getGameTimeDeltaPartialTick(false);
        float strength=CastVisuals.shake(entity,delta);
        var prefs=cn.blockforge.ryomensukuna.m2a542fea.client.SukunaHud.preferences();
        if(prefs!=null && prefs.reduceShake()) strength*=0.25f;
        if(strength<=0 || entity==null) return;
        double time=(entity.tickCount+delta)*2.7;
        setRotation(yRot+(float)Math.sin(time*1.37)*strength,xRot+(float)Math.cos(time)*strength*0.65f);
    }
}
