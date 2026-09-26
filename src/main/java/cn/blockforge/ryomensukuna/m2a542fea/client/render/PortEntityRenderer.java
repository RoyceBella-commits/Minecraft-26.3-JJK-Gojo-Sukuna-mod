package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

public abstract class PortEntityRenderer<T extends Entity> extends EntityRenderer<T, PortEntityRenderer.State> {
    public static final class State extends EntityRenderState { public List<PortBuffers.Batch> geometry = List.of(); }
    protected PortEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(T entity, State state, float delta) {
        super.extractRenderState(entity, state, delta);
        PortBuffers buffers = new PortBuffers();
        render(entity, entity.getYRot(delta), delta, new PoseStack(), buffers, getPackedLightCoords(entity, delta));
        state.geometry = buffers.snapshot();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        PortBuffers.submit(state.geometry, pose, collector);
    }
    protected abstract void render(T entity, float yaw, float delta, PoseStack pose, PortBuffers buffers, int light);
}
