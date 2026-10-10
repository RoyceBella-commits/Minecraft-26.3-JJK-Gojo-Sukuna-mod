package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/** Original ray-cast domain shader, with explicit 26.3 reflection/depth passes. */
public final class DomainRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
        .withLocation(SukunaMod.id("pipeline/domain"))
        .withVertexShader(SukunaMod.id("core/domain"))
        .withFragmentShader(SukunaMod.id("core/domain"))
        .withBindGroupLayout(BindGroupLayout.builder().withUniform("DomainUniforms", UniformType.UNIFORM_BUFFER)
            .withUniform("SceneDepth", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("MirrorColor", UniformType.COMBINED_IMAGE_SAMPLER).build())
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
        .withDepthStencilState(Optional.empty())
        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT),GpuFormat.RGBA8_UNORM,15)).build());
    private static final ShrineModel MODEL = new ShrineModel();
    private static final float RADIUS = (float)ShrineEntity.RADIUS;
    /** Distance within which a shrine's dome is drawn (inside it or seen from outside). */
    private static final double VIEW_RANGE = 256.0;
    private static ShrineEntity selected;
    private static float fade;
    private static long frameTime;
    private static Frame frame;
    private static RenderTarget depth, reflection;
    private static StagedVertexBuffer mirrorVertices;
    private static GpuBuffer uniforms;
    private static final int UNIFORM_SIZE=128;
    private static final ByteBuffer uniformBytes=ByteBuffer.allocateDirect(UNIFORM_SIZE).order(ByteOrder.nativeOrder());
    /** voidCenter is relative to the shrine origin; voidRadius 0 means no Unlimited Void is clashing with it. */
    private record Frame(Matrix4f inverse, Vec3 cameraRelative, float radius, float seconds, float fade, List<PortBuffers.Batch> mirror, Vec3 voidCenter, float voidRadius, float depthFar, float depthSpan, boolean ourVoid) {}

    /**
     * How a depth-buffer value maps back to the projection's clip-space z: {@code ndc = far + span * depth}.
     * 26.3 keeps a reversed depth buffer (near = 1, sky = 0) whatever the projection's own z range is
     * (OpenGL style -1..1 or 0..1, forward or reversed), so both ends are read off the matrix itself.
     */
    static float[] depthMapping(Matrix4f projection) {
        org.joml.Vector4f nearPoint = projection.transform(new org.joml.Vector4f(0.0f, 0.0f, -0.05f, 1.0f));
        org.joml.Vector4f farPoint = projection.transform(new org.joml.Vector4f(0.0f, 0.0f, -100000.0f, 1.0f));
        float near = Math.round(nearPoint.z / nearPoint.w);
        float far = farPoint.z / farPoint.w;
        return new float[]{far, near - far};
    }

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(DomainRenderer::extract);
        // Feature callbacks run inside vanilla's open render pass. Texture copies and
        // independent passes must wait until that pass has closed.
        LevelRenderEvents.END_MAIN.register(ctx -> draw());
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            SukunaMod.id("domain_buffers"), (ResourceManagerReloadListener) manager -> release());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> release());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> release());
    }
    private static void release() {
        clearFrame();
        releaseBuffers();
    }
    private static void clearFrame() {
        selected=null;frame=null;fade=0;frameTime=0;
    }
    private static void releaseBuffers() {
        if(depth!=null) {depth.destroyBuffers();depth=null;}
        if(reflection!=null) {reflection.destroyBuffers();reflection=null;}
        if(mirrorVertices!=null) {mirrorVertices.close();mirrorVertices=null;}
        if(uniforms!=null) {uniforms.close();uniforms=null;}
    }
    private static float smooth(float x) { x=Mth.clamp(x,0,1);return x*x*(3-2*x); }
    private static void extract(LevelExtractionContext ctx) {
        if(selected!=null && selected.level()!=ctx.level()) clearFrame();
        Vec3 camera=ctx.camera().position();
        ShrineEntity nearest=null;double best=Double.MAX_VALUE;
        // The crimson dome is seen from outside too, so shrines are picked up from afar.
        for(ShrineEntity shrine:ctx.level().getEntities(SukunaMod.SHRINE,new AABB(camera,camera).inflate(VIEW_RANGE), e->!e.isRemoved())) {
            double distance=shrine.distanceToSqr(camera);
            if(distance<best) {best=distance;nearest=shrine;}
        }
        boolean current=selected!=null && !selected.isRemoved() && selected.level()==ctx.level() && selected.distanceToSqr(camera)<VIEW_RANGE*VIEW_RANGE;
        if(!current && fade<0.01f) selected=nearest;
        long now=System.nanoTime();float seconds=frameTime==0?0.016f:Math.min(0.1f,(now-frameTime)/1.0E9f);frameTime=now;
        boolean valid=selected!=null && !selected.isRemoved() && selected.level()==ctx.level();
        float target=valid?1:0;
        fade+=Mth.clamp(target-fade,-seconds*2.5f,seconds*3);
        if(selected==null || fade<=0.001f) {frame=null;return;}
        float life=selected.getVisualLife(ctx.deltaTracker().getGameTimeDeltaPartialTick(false));
        float expansion=smooth((life-35)/65);
        if(expansion<=0.001f) {frame=null;return;}
        var cameraState=ctx.levelState().cameraRenderState;
        Matrix4f inverse=new Matrix4f(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix).invert();
        PoseStack pose=new PoseStack();pose.mulPose(cameraState.viewRotationMatrix);
        pose.translate(selected.getX()-camera.x,selected.getY()+0.07-camera.y,selected.getZ()-camera.z);
        pose.scale(1,-1,1);pose.rotate(new Quaternionf().rotationY((float)Math.toRadians(180-selected.getYRot())));pose.scale(ShrineModel.SCALE,ShrineModel.SCALE,ShrineModel.SCALE);
        MODEL.animate(life);
        PortBuffers buffers=new PortBuffers();
        MODEL.getRoot().render(pose,buffers.getBuffer(RenderTypes.entityCutout(SukunaMod.id("textures/entity/malevolent_shrine_refined.png"))),15728880,OverlayTexture.NO_OVERLAY,-1);
        float partial=ctx.deltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 origin=new Vec3(selected.getX(),selected.getY()+0.035,selected.getZ());
        Vec3 voidCenter=Vec3.ZERO;float voidRadius=0;
        // Domain clash: the nearest standing Unlimited Void that overlaps this shrine takes its side of the seam.
        double bestVoid=Double.MAX_VALUE;
        for(VoidDomainEntity v:ctx.level().getEntities(SukunaMod.VOID_DOMAIN,new AABB(origin,origin).inflate(RADIUS*2+4),e->!e.isRemoved()&&e.closeAt()<0)) {
            float vr=(float)(VoidDomainEntity.RADIUS*VoidDomainEntity.openFraction(v.age()+partial));
            Vec3 vc=v.position().add(0,0.5,0).subtract(origin);
            double dist=vc.length();
            if(vr<0.3f||dist>=RADIUS*expansion+vr||dist>=bestVoid) continue;
            bestVoid=dist;voidCenter=vc;voidRadius=vr;
        }
        float[] depthMap=depthMapping(new Matrix4f(cameraState.projectionMatrix));
        // Other mods may put their own domain in the Void's place (the Fate mod's marble does); only
        // our Unlimited Void gets its cosmos painted on its side, any other rival is simply left its side.
        boolean ourVoid=voidRadius>0;
        frame=new Frame(inverse,new Vec3(camera.x-selected.getX(),camera.y-selected.getY()-0.035,camera.z-selected.getZ()),RADIUS*expansion,life/20,fade,buffers.snapshot(),voidCenter,voidRadius,depthMap[0],depthMap[1],ourVoid);
    }
    private static void draw() {
        Frame current=frame;if(current==null)return;
        var device=RenderSystem.getDevice();
        RenderTarget main=Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if(main.width<=0 || main.height<=0 || main.getDepthTexture()==null || main.getColorTextureView()==null) {
            releaseBuffers();
            return;
        }
        if(depth!=null && (depth.width!=main.width || depth.height!=main.height
            || depth.getDepthTexture().getFormat()!=main.getDepthTexture().getFormat())) {
            releaseBuffers();
        }
        if(depth==null) {
            depth=new TextureTarget("Sukuna domain depth",main.width,main.height,null,main.getDepthTexture().getFormat());
            reflection=new TextureTarget("Sukuna domain mirror",main.width,main.height,GpuFormat.RGBA8_UNORM,main.getDepthTexture().getFormat());
            mirrorVertices=new StagedVertexBuffer(()->"Sukuna mirror vertices",262144);
            uniforms=device.createBuffer(()->"Sukuna domain uniforms",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,UNIFORM_SIZE);
        }
        try {
        depth.copyDepthFrom(main);
        List<StagedVertexBuffer.Draw> draws=new ArrayList<>();
        List<PreparedRenderType> types=new ArrayList<>();
        for(var batch:current.mirror) {
            var draw=mirrorVertices.appendDraw(batch.type().format(),batch.type().primitiveTopology());
            var out=mirrorVertices.getVertexBuilder(draw);
            for(var v:batch.vertices()) out.addVertex(v.x(),v.y(),v.z()).setColor(v.color()).setUv(v.u(),v.v()).setOverlay(v.overlay()).setLight(v.light()).setNormal(v.nx(),v.ny(),v.nz()).setUv3(v.u3(),v.v3());
            draws.add(draw);
            var type=batch.type().prepare();
            types.add(new PreparedRenderType(type.name(),type.pipeline(),type.oitPipelineSet(),RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()),type.scissorState(),type.textures()));
        }
        mirrorVertices.upload();
        // getExecuteInfo may grow/upload the shared index buffer. Do this before
        // opening the reflection pass, too, rather than triggering another illegal upload.
        List<StagedVertexBuffer.ExecuteInfo> executions=new ArrayList<>();
        for(var draw:draws) executions.add(mirrorVertices.getExecuteInfo(draw));
        var encoder=device.createCommandEncoder();
        try(var pass=encoder.createRenderPass(()->"Sukuna reflected shrine",reflection.getColorTextureView(),Optional.of(new Vector4f(0,0,0,0)),reflection.getDepthTextureView(),OptionalDouble.of(0))) {
            for(int i=0;i<draws.size();i++) {
                var info=executions.get(i);
                // drawFromBuffer binds default uniforms and our identity transform
                // on this independent pass; no vanilla pass state is reused.
                if(info!=null)types.get(i).drawFromBuffer(info,pass);
            }
        }
        uniformBytes.clear();current.inverse.get(0,uniformBytes);uniformBytes.position(64);
        uniformBytes.putFloat((float)current.cameraRelative.x).putFloat((float)current.cameraRelative.y).putFloat((float)current.cameraRelative.z).putFloat(0);
        uniformBytes.putFloat(current.radius).putFloat(current.seconds).putFloat(current.fade).putFloat(RADIUS);
        uniformBytes.putFloat((float)current.voidCenter.x).putFloat((float)current.voidCenter.y).putFloat((float)current.voidCenter.z).putFloat(current.voidRadius);
        uniformBytes.putFloat(current.depthFar).putFloat(current.depthSpan).putFloat(current.ourVoid?1:0).putFloat(0);uniformBytes.flip();
        encoder.writeToBuffer(uniforms.slice(),uniformBytes);
        try(var pass=encoder.createRenderPass(()->"Sukuna domain composite",main.getColorTextureView(),Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            pass.setUniform("DomainUniforms",uniforms);
            pass.setUniform("SceneDepth",depth.getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("MirrorColor",reflection.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3,1,0,0);
        }
        encoder.submit();
        } finally {
            mirrorVertices.endFrame();
        }
    }
}
