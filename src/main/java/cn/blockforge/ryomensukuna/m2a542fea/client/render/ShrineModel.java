package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.client.render.RitualAnimation;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.ShrineGeometry;
import net.minecraft.client.model.geom.ModelPart;

public final class ShrineModel {
    /** The shrine is drawn 1.75x its original size (entity and mirrored reflection alike). */
    public static final float SCALE = 1.75f;
    private final ModelPart root = ShrineGeometry.create();
    private final ModelPart[] parts = (ModelPart[])this.root.getAllParts().toArray(ModelPart[]::new);
    private final RitualAnimation.Binding assembly = ShrineGeometry.ASSEMBLE.bind(this.root);

    public void animate(float ticks) {
        for (ModelPart part : this.parts) {
            part.resetPose();
            part.zScale = 1.0f;
            part.yScale = 1.0f;
            part.xScale = 1.0f;
        }
        this.assembly.apply(Math.max(0.0f, ticks) / 20.0f, 1.0f);
    }

    public ModelPart getRoot() {
        return this.root;
    }
}

