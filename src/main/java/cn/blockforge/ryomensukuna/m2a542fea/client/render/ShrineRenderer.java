package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.ShrineModel;
import cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

public class ShrineRenderer
extends PortEntityRenderer<ShrineEntity> {
    private static final Identifier TEXTURE = SukunaMod.id("textures/entity/malevolent_shrine_refined.png");
    private final ShrineModel model = new ShrineModel();

    public ShrineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    public Identifier getTexture(ShrineEntity e) {
        return TEXTURE;
    }

    public boolean shouldRender(ShrineEntity e, Frustum f, double x, double y, double z, float delta) {
        return true;
    }

    public void render(ShrineEntity e, float yaw, float delta, PoseStack m, PortBuffers v, int light) {
        m.pushPose();
        this.model.animate(e.getVisualLife(delta));
        m.rotate(new Quaternionf().rotationY((float)Math.toRadians(180.0f - e.getYRot())));
        m.scale(ShrineModel.SCALE, ShrineModel.SCALE, ShrineModel.SCALE);
        this.model.getRoot().render(m, v.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout((Identifier)TEXTURE)), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
        m.popPose();
    }
}

