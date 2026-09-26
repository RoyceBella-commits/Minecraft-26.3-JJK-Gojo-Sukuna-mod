package cn.blockforge.ryomensukuna.m2a542fea.client.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.client.CastVisuals;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FirstPersonHandsAndItemsRenderer.class})
public abstract class HeldCastMixin {
    @Inject(method="submitHandsWithItems",at=@At("HEAD"),cancellable=true)
    private void sukuna$hands(float delta, PoseStack m, SubmitNodeCollector v, net.minecraft.client.renderer.state.level.PlayerRenderState playerState, net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        LocalPlayer p=Minecraft.getInstance().player;
        if(p==null || playerState.avatarRenderState==null) return;
        int light=playerState.avatarRenderState.lightCoords;
        CastVisuals.Pose pose = CastVisuals.pose((Player)p);
        if (pose == null || p.isInvisible()) {
            return;
        }
        float blend = CastVisuals.blend((Player)p, pose, delta);
        boolean barrage = pose.skill() == Skill.CURSED_BARRAGE.netId;
        boolean bow = pose.skill() == Skill.RED.netId;
        boolean domain = pose.skill() == Skill.DOMAIN.netId;
        CastVisuals.PunchVisual punch = CastVisuals.punch((Player)p);
        float strike = barrage && punch != null ? HeldCastMixin.smoothOut(CastVisuals.punchProgress((Player)p, punch, delta)) : 0.0f;
        for (HumanoidArm arm : HumanoidArm.values()) {
            float roll;
            float yaw;
            float pitch;
            float z;
            float x;
            boolean left = arm == HumanoidArm.LEFT;
            float side = left ? -1.0f : 1.0f;
            m.pushPose();
            if (barrage) {
                boolean active = punch != null && punch.left() == left;
                float handStrike = active ? strike : 0.0f;
                x = side * (0.34f + (active ? 0.1f * handStrike : 0.0f));
                z = -(1.02f + (active ? 0.72f * handStrike : 0.0f));
                pitch = -54.0f - (active ? 42.0f * handStrike : 0.0f);
                yaw = side * (left ? -14.0f : 14.0f) + side * (active ? 18.0f * handStrike : 0.0f);
                roll = side * (left ? -11.0f : 11.0f) + side * (active ? 16.0f * handStrike : 0.0f);
            } else {
                x = side * (domain ? 0.12f : 0.24f);
                z = domain ? -0.86f : (left ? -0.82f : -1.02f);
                pitch = domain ? -65.0f : (left ? -67.0f : -79.0f);
                yaw = domain ? side * 32.0f : (left ? -27.0f : 12.0f);
                roll = domain ? side * 10.0f : (left ? -8.0f : 6.0f);
            }
            m.translate((double)x, -0.42 - (double)(1.0f - blend) * 0.45, (double)z);
            m.rotate(new Quaternionf().rotationXYZ((float)Math.toRadians(pitch * blend), (float)Math.toRadians(yaw * blend), (float)Math.toRadians(roll * blend)));
            AvatarRenderer<?> renderer = (AvatarRenderer<?>)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer((Entity)p);
            if (left) {
                renderer.renderLeftHand(m, v, light, playerState.avatarRenderState.skin.body().texturePath(), true);
            } else {
                renderer.renderRightHand(m, v, light, playerState.avatarRenderState.skin.body().texturePath(), true);
            }
            if (barrage) {
                float auraProgress = punch != null && punch.left() == left ? Math.max(0.35f, strike) : 0.48f;
                m.pushPose();
                m.translate(0.0, -0.04, -0.22 - (double)(punch != null && punch.left() == left ? 0.28f * strike : 0.0f));
                PortBuffers aura = new PortBuffers();
                CastVisuals.aura(new PoseStack(), aura, new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, -1.0), new Vec3(1.0, 0.0, 0.0), ((float)p.level().getGameTime() + delta) / 24.0f, 1.0f, auraProgress);
                PortBuffers.submit(aura.snapshot(), m, v);
                m.popPose();
            }
            m.popPose();
        }
        ci.cancel();
    }

    private static float smoothOut(float value) {
        value = Math.max(0.0f, Math.min(1.0f, value));
        return 1.0f - (1.0f - value) * (1.0f - value);
    }
}

