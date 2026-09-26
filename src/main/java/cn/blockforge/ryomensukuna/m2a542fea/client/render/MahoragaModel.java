package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.client.render.MahoragaGeometry;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.RitualAnimation;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class MahoragaModel
{
    private final ModelPart root = MahoragaGeometry.create();
    private final ModelPart[] parts = (ModelPart[])this.root.getAllParts().toArray(ModelPart[]::new);
    private final ModelPart body = this.root.getChild("body");
    private final ModelPart head = this.body.getChild("head");
    private final ModelPart wheel = this.body.getChild("wheel");
    private final RitualAnimation.Binding emerge = MahoragaGeometry.EMERGE.bind(this.root);
    private final RitualAnimation.Binding leap = MahoragaGeometry.LEAP.bind(this.root);
    private final RitualAnimation.Binding fall = MahoragaGeometry.FALL.bind(this.root);
    private final RitualAnimation.Binding idle = MahoragaGeometry.IDLE.bind(this.root);
    private final RitualAnimation.Binding walk = MahoragaGeometry.WALK.bind(this.root);
    private final RitualAnimation.Binding sprint = MahoragaGeometry.SPRINT.bind(this.root);
    private final RitualAnimation.Binding[] attacks = new RitualAnimation.Binding[]{MahoragaGeometry.CLEAVE.bind(this.root), MahoragaGeometry.SWEEP.bind(this.root), MahoragaGeometry.SLAM.bind(this.root), MahoragaGeometry.THRUST.bind(this.root)};

    public ModelPart root() {
        return this.root;
    }

    public void setAngles(MahoragaEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        for (ModelPart part : this.parts) {
            part.resetPose();
            part.zScale = 1.0f;
            part.yScale = 1.0f;
            part.xScale = 1.0f;
        }
        float delta = Mth.clamp((float)(animationProgress - (float)entity.tickCount), (float)0.0f, (float)1.0f);
        if (entity.emergence(delta) < 60.0f) {
            this.emerge.apply(entity.emergence(delta) / 20.0f, 1.0f);
            return;
        }
        if (entity.airState() != 0) {
            float t = entity.airTicks(delta) / 20.0f;
            if (entity.airState() == 1) {
                this.leap.apply(t, 1.0f);
            } else if (entity.airState() == 2) {
                float blend = Mth.clamp((float)(t / 0.2f), (float)0.0f, (float)1.0f);
                this.leap.apply(0.8f, 1.0f - blend);
                this.fall.apply(0.8f, blend);
            } else {
                this.attacks[2].apply(0.65f + t, 1.0f);
            }
            return;
        }
        float attackTime = entity.getAttackTicks(delta);
        boolean attacking = entity.getAttackKind() > 0 && attackTime < 24.0f;
        this.idle.apply(animationProgress / 20.0f % 3.0f, attacking ? 0.25f : 1.0f);
        float locomotionWeight = Math.min(1.0f, limbDistance * 2.0f);
        if (entity.isChasing()) {
            this.sprint.apply(limbAngle * 0.205f % 1.0f, locomotionWeight);
        } else {
            this.walk.apply(limbAngle * 0.106f % 1.0f, locomotionWeight);
        }
        this.head.yRot += Mth.clamp((float)headYaw, (float)-45.0f, (float)45.0f) * ((float)Math.PI / 180);
        this.head.xRot += Mth.clamp((float)headPitch, (float)-25.0f, (float)25.0f) * ((float)Math.PI / 180);
        this.wheel.yRot += entity.getWheelRotation(delta);
        if (attacking) {
            float blend = Mth.clamp((float)(attackTime / 7.0f), (float)0.0f, (float)1.0f);
            blend = blend * blend * (3.0f - 2.0f * blend);
            this.attacks[Mth.clamp((int)(entity.getAttackKind() - 1), (int)0, (int)(this.attacks.length - 1))].apply(attackTime / 20.0f, blend);
        }
    }
}

