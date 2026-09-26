package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader;
import cn.blockforge.ryomensukuna.m2a542fea.entity.WorldCutFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class WorldCutFxRenderer
extends PortEntityRenderer<WorldCutFxEntity> {
    public WorldCutFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    public Identifier getTexture(WorldCutFxEntity entity) {
        return SukunaMod.id("textures/misc/slash_world.png");
    }

    public void render(WorldCutFxEntity entity, float yaw, float delta, PoseStack matrices, PortBuffers consumers, int light) {
        float age = ((float)(60 - entity.getLife()) + delta) / 60.0f;
        Vec3 right = SlashShader.right(entity.getYRot());
        Vec3 up = SlashShader.up(entity.getYRot(), entity.getXRot());
        float half = entity.getScale() * 0.5f;
        SlashShader.blade(matrices, consumers, right, up, Vec3.ZERO, 0.0, half, 0.85f, age, 2, 0.37f);
        for (int i = 0; i < 2; ++i) {
            float t = age - 0.04f * (float)(i + 1);
            if (t < 0.0f) continue;
            SlashShader.blade(matrices, consumers, right, up, up.scale(i == 0 ? 0.23 : -0.23), i == 0 ? 0.014 : -0.012, half * 0.92f, 0.1f, t, 2, 0.64f + (float)i * 0.1f);
        }
    }
}

