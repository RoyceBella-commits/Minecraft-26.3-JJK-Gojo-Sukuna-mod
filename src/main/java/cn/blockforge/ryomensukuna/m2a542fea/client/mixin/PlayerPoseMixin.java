package cn.blockforge.ryomensukuna.m2a542fea.client.mixin;

import cn.blockforge.ryomensukuna.m2a542fea.client.CastVisuals;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerModel.class})
public abstract class PlayerPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at=@At("TAIL"))
    private void sukuna$pose(net.minecraft.client.renderer.entity.state.AvatarRenderState state, CallbackInfo ci) {
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(level==null || !(level.getEntity(state.id) instanceof Player player)) return;
        LivingEntity entity=player;
        float progress=state.ageInTicks;
        boolean barrage;
        CastVisuals.Pose pose = CastVisuals.pose(player);
        if (pose == null) {
            return;
        }
        PlayerModel model = (PlayerModel)(Object)this;
        float blend = CastVisuals.blend(player, pose, progress - (float)entity.tickCount);
        boolean bow = pose.skill() == Skill.RED.netId;
        boolean shadow = pose.skill() == Skill.MAHORAGA.netId;
        boolean domain = pose.skill() == Skill.DOMAIN.netId;
        boolean bl = barrage = pose.skill() == Skill.CURSED_BARRAGE.netId;
        if (barrage) {
            CastVisuals.PunchVisual punch = CastVisuals.punch(player);
            float hit = CastVisuals.punchProgress(player, punch, progress - (float)entity.tickCount);
            boolean left = punch != null && punch.left();
            float strike = punch == null ? 0.0f : PlayerPoseMixin.smoothOut(hit);
            float leftPitch = left ? -1.18f - strike * 0.78f : -0.55f + strike * 0.16f;
            float rightPitch = left ? -0.55f + strike * 0.16f : -1.18f - strike * 0.78f;
            float leftYaw = left ? 0.34f + strike * 0.36f : 0.18f;
            float rightYaw = left ? -0.18f : -0.34f - strike * 0.36f;
            float leftRoll = left ? -0.2f - strike * 0.2f : -0.08f;
            float rightRoll = left ? 0.08f : 0.2f + strike * 0.2f;
            model.leftArm.xRot = Mth.lerp((float)blend, (float)model.leftArm.xRot, (float)leftPitch);
            model.rightArm.xRot = Mth.lerp((float)blend, (float)model.rightArm.xRot, (float)rightPitch);
            model.leftArm.yRot = Mth.lerp((float)blend, (float)model.leftArm.yRot, (float)leftYaw);
            model.rightArm.yRot = Mth.lerp((float)blend, (float)model.rightArm.yRot, (float)rightYaw);
            model.leftArm.zRot = Mth.lerp((float)blend, (float)model.leftArm.zRot, (float)leftRoll);
            model.rightArm.zRot = Mth.lerp((float)blend, (float)model.rightArm.zRot, (float)rightRoll);
        } else if (domain) {
            float sealPitch = -0.92f + model.head.xRot * 0.35f;
            model.leftArm.xRot = Mth.lerp((float)blend, (float)model.leftArm.xRot, (float)sealPitch);
            model.rightArm.xRot = Mth.lerp((float)blend, (float)model.rightArm.xRot, (float)sealPitch);
            model.leftArm.yRot = Mth.lerp((float)blend, (float)model.leftArm.yRot, (float)0.52f);
            model.rightArm.yRot = Mth.lerp((float)blend, (float)model.rightArm.yRot, (float)-0.52f);
            model.leftArm.zRot = Mth.lerp((float)blend, (float)model.leftArm.zRot, (float)-0.18f);
            model.rightArm.zRot = Mth.lerp((float)blend, (float)model.rightArm.zRot, (float)0.18f);
        } else {
            float armPitch = shadow ? -0.7f : -1.32f + model.head.xRot;
            model.leftArm.xRot = Mth.lerp((float)blend, (float)model.leftArm.xRot, (float)(armPitch - 0.1f));
            model.rightArm.xRot = Mth.lerp((float)blend, (float)model.rightArm.xRot, (float)(armPitch + 0.1f));
            model.leftArm.yRot = Mth.lerp((float)blend, (float)model.leftArm.yRot, (float)(bow ? 0.1f : 0.26f));
            model.rightArm.yRot = Mth.lerp((float)blend, (float)model.rightArm.yRot, (float)(bow ? -0.35f : -0.12f));
            model.leftArm.zRot = Mth.lerp((float)blend, (float)model.leftArm.zRot, (float)(shadow ? -0.4f : -0.1f));
            model.rightArm.zRot = Mth.lerp((float)blend, (float)model.rightArm.zRot, (float)(shadow ? 0.4f : 0.1f));
        }

    }

    private static float smoothOut(float value) {
        value = Mth.clamp((float)value, (float)0.0f, (float)1.0f);
        return 1.0f - (1.0f - value) * (1.0f - value);
    }
}

