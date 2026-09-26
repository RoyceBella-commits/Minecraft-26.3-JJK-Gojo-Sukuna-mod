package cn.blockforge.ryomensukuna.m2a542fea.client.render;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
public final class MahoragaRenderer extends PortEntityRenderer<MahoragaEntity> {
    private final MahoragaModel model = new MahoragaModel();
    public MahoragaRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius=0.65f; }
    @Override public void render(MahoragaEntity e,float yaw,float delta,PoseStack pose,PortBuffers buffers,int light) {
        pose.pushPose();
        pose.rotate(new Quaternionf().rotationY((float)Math.toRadians(180.0f-e.yBodyRot)));
        pose.scale(-0.85f,-0.85f,0.85f); pose.translate(0,-1.501,0);
        model.setAngles(e,e.walkAnimation.position(delta),e.walkAnimation.speed(delta),e.tickCount+delta,e.yHeadRot-e.yBodyRot,e.getXRot(delta));
        model.root().render(pose,buffers.getBuffer(RenderTypes.entityCutout(SukunaMod.id("textures/entity/mahoraga_refined.png"))),light,OverlayTexture.NO_OVERLAY,-1);
        pose.popPose();
    }
}
