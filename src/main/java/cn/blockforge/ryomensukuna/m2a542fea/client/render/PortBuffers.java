package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Captures geometry during extraction; render callbacks only read immutable vertex snapshots. */
public final class PortBuffers {
    private final Map<RenderType, Recorder> buffers = new LinkedHashMap<>();
    public VertexConsumer getBuffer(RenderType type) { return buffers.computeIfAbsent(type, key -> new Recorder()); }
    public record Vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz, float u3, float v3) {}
    public record Batch(RenderType type, List<Vertex> vertices) {}
    public List<Batch> snapshot() {
        return buffers.entrySet().stream().map(e -> {
            e.getValue().finish(); return new Batch(e.getKey(), List.copyOf(e.getValue().vertices));
        }).toList();
    }
    public static void submit(List<Batch> batches, PoseStack pose, SubmitNodeCollector collector) {
        for (Batch batch : batches) collector.submitCustomGeometry(pose, batch.type(), (entry, out) -> {
            for (Vertex v : batch.vertices()) out.addVertex(entry.pose(), v.x, v.y, v.z)
                .setColor(v.color).setUv(v.u, v.v).setOverlay(v.overlay).setLight(v.light)
                .setNormal(entry, v.nx, v.ny, v.nz).setUv3(v.u3, v.v3);
        });
    }
    private static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private boolean active;
        private float x,y,z,u,v,nx,ny,nz,u3,v3;
        private int color=-1,overlay=655360,light=15728880;
        void finish() { if(active) { vertices.add(new Vertex(x,y,z,color,u,v,overlay,light,nx,ny,nz,u3,v3)); active=false; } }
        public VertexConsumer addVertex(float x,float y,float z) { finish();this.x=x;this.y=y;this.z=z;active=true;return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return setColor(a<<24|r<<16|g<<8|b); }
        public VertexConsumer setColor(int c) {color=c;return this;}
        public VertexConsumer setUv(float u,float v) {this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v) {overlay=u|(v<<16);return this;}
        public VertexConsumer setUv2(int u,int v) {light=u|(v<<16);return this;}
        public VertexConsumer setUv3(float u,float v) {u3=u;v3=v;return this;}
        public VertexConsumer setLineWidth(float width) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) {nx=x;ny=y;nz=z;return this;}
    }
}
