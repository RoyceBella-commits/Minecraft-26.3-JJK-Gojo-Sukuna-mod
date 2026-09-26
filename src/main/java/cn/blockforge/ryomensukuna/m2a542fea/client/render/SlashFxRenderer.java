package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class SlashFxRenderer
extends PortEntityRenderer<SlashFxEntity> {
    public SlashFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    public Identifier getTexture(SlashFxEntity entity) {
        return SukunaMod.id("textures/misc/slash_cleave.png");
    }

    public boolean shouldRender(SlashFxEntity entity, Frustum frustum, double x, double y, double z, float delta) {
        return entity.distanceToSqr(x, y, z) <= 25600.0;
    }

    public void render(SlashFxEntity entity, float yaw, float delta, PoseStack matrices, PortBuffers consumers, int light) {
        float age = ((float)entity.tickCount + delta) / (float)entity.duration();
        Vec3 right = SlashShader.right(entity.getYRot());
        Vec3 up = SlashShader.up(entity.getYRot(), entity.getXRot());
        float scale = entity.getScale();
        if (entity.getMode() == 4) {
            float time = ((float)entity.tickCount + delta) % 24.0f / 24.0f;
            float swell = Math.min(1.0f, ((float)entity.tickCount + delta) / 8.0f) * (1.0f - age * 0.45f);
            for (int i = 0; i < 32; ++i) {
                double a = (double)i * Math.PI * 2.0 / 32.0;
                float radius = scale * swell * (0.25f + (float)(i % 4) * 0.19f);
                Vec3 center = new Vec3(Math.cos(a) * (double)radius, Math.sin((double)age * Math.PI) * (double)scale * 0.35, Math.sin(a) * (double)radius);
                SlashShader.blade(matrices, consumers, SlashShader.right(this.entityRenderDispatcher.camera.yRot()), new Vec3(0.0, 1.0, 0.0), center, 1.5707963267948966, scale * (0.55f + (float)(i % 3) * 0.16f) * (1.0f - age * 0.6f), scale * 0.24f, time, 4, (float)i / 33.0f);
            }
            return;
        }
        if (entity.getMode() == 5 || entity.getMode() == 7) {
            SlashShader.blade(matrices, consumers, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), Vec3.ZERO, 0.0, scale, scale, entity.getMode() == 5 ? ((float)entity.tickCount + delta) % 24.0f / 24.0f : age, entity.getMode(), 0.4f);
            return;
        }
        if (entity.getMode() == 3) {
            int i;
            for (i = -2; i <= 2; ++i) {
                SlashShader.blade(matrices, consumers, right, up, right.scale((double)i * 1.25), 1.5707963267948966, 2.8f, 0.12f, age, 0, (float)(i + 3) * 0.13f);
            }
            for (i = -1; i <= 1; ++i) {
                SlashShader.blade(matrices, consumers, right, up, up.scale((double)i * 1.45), 0.0, 3.7f, 0.12f, age, 0, (float)(i + 2) * 0.19f);
            }
            return;
        }
        if (entity.getMode() == SlashFxEntity.CLASH) {
            // Domain clash: crimson slashes with a white-hot core crossing the seam.
            SlashShader.blade(matrices, consumers, right, up, Vec3.ZERO, 0.22, scale * 1.9f, scale * 0.3f, age, 1, 0.37f, 0.85f);
            return;
        }
        int count = 1;
        for (int i = 0; i < count; ++i) {
            float time = Math.max(0.0f, age - (float)i * 0.075f);
            if (age < (float)i * 0.075f) continue;
            double angle = count == 1 ? 0.22 : (new double[]{-0.48, 0.92, 0.26})[i];
            SlashShader.blade(matrices, consumers, right, up, Vec3.ZERO, angle, scale * 1.9f, scale * 0.3f, time, entity.getMode(), (float)(i + 1) * 0.21f);
        }
    }
}

