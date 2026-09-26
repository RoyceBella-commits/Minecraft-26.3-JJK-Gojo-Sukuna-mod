package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader;
import cn.blockforge.ryomensukuna.m2a542fea.entity.RedBlastEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class RedBlastRenderer
extends PortEntityRenderer<RedBlastEntity> {
    public RedBlastRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    public Identifier getTexture(RedBlastEntity e) {
        return SukunaMod.id("textures/misc/red_flame.png");
    }

    public void render(RedBlastEntity e, float yaw, float delta, PoseStack m, PortBuffers v, int light) {
        Vec3 right;
        Vec3 forward = e.getDeltaMovement().normalize();
        if (forward.lengthSqr() < 0.01) {
            forward = Vec3.directionFromRotation((float)e.getXRot(), (float)e.getYRot());
        }
        if ((right = forward.cross(new Vec3(0.0, 1.0, 0.0))).lengthSqr() < 0.001) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        float pulse = ((float)e.tickCount + delta) % 16.0f / 16.0f;
        for (int i = 0; i < 4; ++i) {
            Vec3 cross = right.scale(Math.cos((double)i * Math.PI / 4.0)).add(up.scale(Math.sin((double)i * Math.PI / 4.0)));
            SlashShader.blade(m, v, forward, cross, forward.scale(-1.0), 0.0, 3.0f + e.getCharge(), 0.35f + e.getCharge() * 0.18f, pulse, 4, 0.2f + (float)i * 0.18f);
            SlashShader.blade(m, v, forward, cross, Vec3.ZERO, 0.4, 1.0f, 0.25f, pulse, 4, 0.8f);
            SlashShader.blade(m, v, forward, cross, Vec3.ZERO, -0.4, 1.0f, 0.25f, pulse, 4, 0.6f);
        }
    }
}

