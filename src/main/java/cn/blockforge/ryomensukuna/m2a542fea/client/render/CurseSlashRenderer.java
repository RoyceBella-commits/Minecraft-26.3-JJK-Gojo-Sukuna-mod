package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader;
import cn.blockforge.ryomensukuna.m2a542fea.entity.CurseSlashEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class CurseSlashRenderer
extends PortEntityRenderer<CurseSlashEntity> {
    public CurseSlashRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    public Identifier getTexture(CurseSlashEntity e) {
        return SukunaMod.id("textures/misc/slash_dismantle.png");
    }

    public boolean shouldRender(CurseSlashEntity e, Frustum frustum, double x, double y, double z, float delta) {
        double max = e.getMode() == 2 ? 280.0 : 180.0;
        return e.distanceToSqr(x, y, z) <= max * max;
    }

    public void render(CurseSlashEntity e, float yaw, float delta, PoseStack m, PortBuffers v, int light) {
        Vec3 right = SlashShader.right(e.getYRot());
        Vec3 up = SlashShader.up(e.getYRot(), e.getXRot());
        float half = e.halfSize();
        float time = 0.18f + (float)Math.sin((double)((float)e.tickCount + delta) * 0.35) * 0.035f;
        if (e.getMode() == 1) {
            float i = -half;
            while ((double)i <= (double)half + 0.1) {
                SlashShader.blade(m, v, right, up, right.scale((double)i), 1.5707963267948966, half, 0.42f, time, 0, 0.43f);
                SlashShader.blade(m, v, right, up, up.scale((double)i), 0.0, half, 0.42f, time, 0, 0.67f);
                i += 3.0f;
            }
        } else {
            double angle = 0.0;
            SlashShader.blade(m, v, right, up, Vec3.ZERO, angle, half, e.getMode() == 2 ? 3.0f : 0.85f, time, e.getMode() == 2 ? 2 : 0, 0.52f);
            // World Cut is a single cut: no after-image trail.
            if (e.getMode() == 2) return;
            for (int i = 0; i < 5; ++i) {
                Vec3 trail = e.getTravelDir().scale((double)(-i) * 1.1);
                SlashShader.blade(m, v, right, up, trail, angle, half * (1.0f - (float)i * 0.07f), 0.22f, time + (float)i * 0.09f, 2, 0.2f + (float)i * 0.1f);
            }
        }
    }
}

