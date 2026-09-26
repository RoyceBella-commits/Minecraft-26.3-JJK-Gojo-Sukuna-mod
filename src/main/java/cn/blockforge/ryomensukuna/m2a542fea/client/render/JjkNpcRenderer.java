package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Player-shaped renderer with a fixed skin for the Gojo / Sukuna spawn-egg sorcerers. */
public class JjkNpcRenderer<T extends JjkNpcEntity> extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;

    public JjkNpcRenderer(EntityRendererProvider.Context ctx, Identifier texture) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.texture = texture;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return this.texture;
    }
}
