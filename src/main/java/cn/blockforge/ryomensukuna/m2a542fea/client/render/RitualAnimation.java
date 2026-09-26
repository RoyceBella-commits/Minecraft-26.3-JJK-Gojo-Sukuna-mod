package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import net.minecraft.client.model.geom.ModelPart;

final class RitualAnimation {
    private final Track[] tracks;

    RitualAnimation(Track ... tracks) {
        this.tracks = tracks;
    }

    Binding bind(ModelPart root) {
        ModelPart[] parts = new ModelPart[this.tracks.length];
        for (int i = 0; i < this.tracks.length; ++i) {
            ModelPart part = root;
            for (String name : this.tracks[i].path().split("/")) {
                part = part.getChild(name);
            }
            parts[i] = part;
        }
        return new Binding(parts);
    }

    record Track(String path, int channel, float[] keys) {
    }

    final class Binding {
        private final ModelPart[] parts;

        Binding(ModelPart[] parts) {
            this.parts = parts;
        }

        void apply(float time, float weight) {
            block5: for (int i = 0; i < RitualAnimation.this.tracks.length; ++i) {
                Track track = RitualAnimation.this.tracks[i];
                float[] keys = track.keys();
                int a = 0;
                while (a + 4 < keys.length && keys[a + 4] <= time) {
                    a += 4;
                }
                int b = Math.min(a + 4, keys.length - 4);
                float t = a == b ? 0.0f : Math.max(0.0f, Math.min(1.0f, (time - keys[a]) / (keys[b] - keys[a])));
                t = t * t * (3.0f - 2.0f * t);
                float x = (keys[a + 1] + (keys[b + 1] - keys[a + 1]) * t) * weight;
                float y = (keys[a + 2] + (keys[b + 2] - keys[a + 2]) * t) * weight;
                float z = (keys[a + 3] + (keys[b + 3] - keys[a + 3]) * t) * weight;
                ModelPart part = this.parts[i];
                switch (track.channel()) {
                    case 0: {
                        part.x += x;
                        part.y += y;
                        part.z += z;
                        continue block5;
                    }
                    case 1: {
                        part.xRot += x;
                        part.yRot += y;
                        part.zRot += z;
                        continue block5;
                    }
                    case 2: {
                        part.xScale *= 1.0f - weight + x;
                        part.yScale *= 1.0f - weight + y;
                        part.zScale *= 1.0f - weight + z;
                        continue block5;
                    }
                    default: {
                        throw new IllegalStateException("Unknown animation channel");
                    }
                }
            }
        }
    }
}

