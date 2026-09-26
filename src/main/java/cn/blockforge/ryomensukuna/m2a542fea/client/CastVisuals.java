package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader;
import cn.blockforge.ryomensukuna.m2a542fea.entity.CurseSlashEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class CastVisuals {
    private static final Map<UUID, Pose> POSES = new HashMap<UUID, Pose>();
    private static final Map<UUID, PunchVisual> PUNCHES = new HashMap<UUID, PunchVisual>();
    private static final java.util.List<FistVisual> FISTS = new java.util.ArrayList<FistVisual>();
    private static final int FIST_TICKS = 8;

    private CastVisuals() {
    }

    public static void receive(UUID id, int skill, boolean held, Vec3 anchor) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        long now = world.getGameTime();
        if (skill < 0) {
            POSES.remove(id);
            return;
        }
        Pose old = POSES.get(id);
        long start = old != null && old.skill == skill ? old.started : now;
        POSES.put(id, new Pose(skill, held, start, now + (long)(held ? 18 : 22), anchor));
    }

    public static void punch(UUID id, int index, boolean left, Vec3 origin, Vec3 direction, float charge) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        long now = world.getGameTime();
        PUNCHES.put(id, new PunchVisual(index, left, origin, direction.normalize(), charge, now, now + 7L));
    }

    /** A landed bare-handed strike: shockwave ring and speed lines; Black Flash adds red-black lightning. */
    public static void fist(Vec3 at, Vec3 direction, boolean blackFlash) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        Vec3 dir = direction.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        if (FISTS.size() > 32) FISTS.remove(0);
        FISTS.add(new FistVisual(at, dir, blackFlash, world.getGameTime(), world.getRandom().nextFloat()));
        if (blackFlash) {
            for (int i = 0; i < 16; ++i) {
                world.addParticle(new net.minecraft.core.particles.DustParticleOptions(i % 2 == 0 ? 0xE0101A : 0x080808, 1.6f),
                    at.x + (world.getRandom().nextDouble() - 0.5) * 1.2, at.y + (world.getRandom().nextDouble() - 0.5) * 1.2, at.z + (world.getRandom().nextDouble() - 0.5) * 1.2, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void renderFists(PoseStack m, PortBuffers v, Vec3 camera, long now, float delta, net.minecraft.client.Camera cam) {
        FISTS.removeIf(f -> now - f.started > FIST_TICKS);
        Vec3 cr = SlashShader.right(cam.yRot());
        Vec3 cu = SlashShader.up(cam.yRot(), cam.xRot());
        for (FistVisual f : FISTS) {
            float age = Mth.clamp(((float)(now - f.started) + delta) / (float)FIST_TICKS, 0.0f, 0.98f);
            Vec3 c = f.at.subtract(camera);
            Vec3 side = f.dir.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-4) side = new Vec3(1.0, 0.0, 0.0);
            side = side.normalize();
            Vec3 up = side.cross(f.dir).normalize();
            float tint = f.blackFlash ? 0.5f : 1.0f;
            float scale = f.blackFlash ? 2.6f : 1.3f;
            // Shockwave ring facing along the punch, plus one facing the camera for readability.
            SlashShader.blade(m, v, side, up, c, 0.0, scale, scale, age, 7, 0.3f, tint);
            SlashShader.blade(m, v, cr, cu, c, 0.0, scale * 0.7f, scale * 0.7f, Math.min(0.98f, age + 0.1f), 7, 0.6f, tint);
            // Speed lines trailing the fist.
            for (int i = -1; i <= 1; i += 2) {
                Vec3 o = c.subtract(f.dir.scale(0.9)).add(side.scale(i * 0.25)).add(up.scale(0.1 * i));
                CastVisuals.segment(m, v, o.subtract(f.dir.scale(0.8)), o, up, 0.05f, age, 0, 0.2f + i * 0.1f);
            }
            if (f.blackFlash) {
                // Black lightning with a red rim, crackling outward from the impact.
                for (int i = 0; i < 6; ++i) {
                    double a = i * Math.PI / 3.0 + f.seed * 6.0;
                    Vec3 out = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                    Vec3 kink = c.add(out.scale(0.7)).add(f.dir.scale(0.15 * Math.sin(a * 3.0)));
                    Vec3 end = c.add(out.scale(1.6 + 0.6 * Math.sin(a * 5.0 + f.seed * 9.0))).add(side.scale(0.2 * Math.cos(a * 7.0)));
                    CastVisuals.segment(m, v, c, kink, f.dir, 0.14f, age * 0.9f, 1, (i + 1) * 0.13f, 0.5f);
                    CastVisuals.segment(m, v, kink, end, f.dir, 0.1f, age, 1, (i + 2) * 0.11f, 0.5f);
                }
            }
        }
    }

    public static Pose pose(Player p) {
        Pose value = POSES.get(p.getUUID());
        return value != null && p.level().getGameTime() <= value.expires ? value : null;
    }

    public static PunchVisual punch(Player p) {
        PunchVisual value = PUNCHES.get(p.getUUID());
        return value != null && p.level().getGameTime() <= value.expires ? value : null;
    }

    public static float punchProgress(Player p, PunchVisual punch, float delta) {
        if (punch == null) {
            return 0.0f;
        }
        return Mth.clamp((float)(((float)(p.level().getGameTime() - punch.started) + delta) / 5.0f), (float)0.0f, (float)1.0f);
    }

    /** Standing inside a Malevolent Shrine. */
    public static boolean domainActive(Minecraft c) {
        if (c.level == null || c.player == null) {
            return false;
        }
        double r = cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity.RADIUS;
        return !c.level.getEntities(SukunaMod.SHRINE, c.player.getBoundingBox().inflate(r + 2.0), e -> e.distanceToSqr(c.player.getX(), c.player.getY(), c.player.getZ()) <= r * r).isEmpty();
    }

    /** Standing inside an Unlimited Void that is still standing. */
    public static boolean voidActive(Minecraft c) {
        if (c.level == null || c.player == null) {
            return false;
        }
        double r = cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity.RADIUS;
        return !c.level.getEntities(SukunaMod.VOID_DOMAIN, c.player.getBoundingBox().inflate(r + 2.0),
            e -> e.closeAt() < 0 && e.distanceToSqr(c.player.getX(), c.player.getY(), c.player.getZ()) <= r * r).isEmpty();
    }

    /**
     * Hollow Purple being assembled: blue and red spiral in from both sides and fuse into purple;
     * afterwards a small blue and a small red keep circling the purple mass.
     */
    private static void hollowPurple(PoseStack m, PortBuffers v, Vec3 hand, Vec3 side, Vec3 up, Vec3 cr, Vec3 cu, float ticks, float t, float size, float dim, float phase) {
        if (t < 1.0f) {
            double gap = 0.55 * (1.0 - t) * size;
            double spin = t * t * Math.PI * 3.0 + ticks * 0.15;
            Vec3 off = side.scale(Math.cos(spin) * gap).add(up.scale(Math.sin(spin) * gap));
            float r = 0.28f * size;
            SlashShader.orb(m, v, hand.subtract(off), cr, cu, r * 1.6f, phase, SlashShader.ORB_BLUE, 0.6f);
            SlashShader.orb(m, v, hand.subtract(off), cr, cu, r, phase, SlashShader.ORB_BLUE, dim);
            SlashShader.orb(m, v, hand.add(off), cr, cu, r * 1.6f, phase, SlashShader.ORB_RED, 0.6f);
            SlashShader.orb(m, v, hand.add(off), cr, cu, r, phase, SlashShader.ORB_RED, dim);
            if (t > 0.7f) {
                // The purple core starts to form as the two meet.
                SlashShader.orb(m, v, hand, cr, cu, 0.42f * size * (t - 0.7f) / 0.3f, phase, SlashShader.ORB_PURPLE, dim);
            }
            return;
        }
        float pulse = 1.0f + 0.08f * (float)Math.sin(ticks * 0.5f);
        float r = 0.42f * size * pulse;
        SlashShader.orb(m, v, hand, cr, cu, r * 1.6f, phase, SlashShader.ORB_PURPLE, 0.6f);
        SlashShader.orb(m, v, hand, cr, cu, r, phase, SlashShader.ORB_PURPLE, dim);
        double angle = ticks * 0.35;
        for (int i = 0; i < 2; ++i) {
            double a = angle + i * Math.PI;
            Vec3 orbit = side.scale(Math.cos(a) * 0.5 * size).add(up.scale(Math.sin(a) * 0.5 * size));
            SlashShader.orb(m, v, hand.add(orbit), cr, cu, 0.1f * size, phase, i == 0 ? SlashShader.ORB_BLUE : SlashShader.ORB_RED, dim);
        }
    }

    public static float blend(Player p, Pose pose, float delta) {
        float in = Mth.clamp((float)(((float)(p.level().getGameTime() - pose.started) + delta) / 5.0f), (float)0.0f, (float)1.0f);
        float out = pose.held ? 1.0f : Mth.clamp((float)(((float)(pose.expires - p.level().getGameTime()) - delta) / 5.0f), (float)0.0f, (float)1.0f);
        float v = Math.min(in, out);
        return v * v * (3.0f - 2.0f * v);
    }

    public static float shake(Entity viewer, float delta) {
        if (viewer == null) {
            return 0.0f;
        }
        float result = 0.0f;
        long now = viewer.level().getGameTime();
        for (FistVisual f : FISTS) {
            if (!f.blackFlash) continue;
            float left = 1.0f - ((float)(now - f.started) + delta) / (float)FIST_TICKS;
            double d = Math.sqrt(viewer.distanceToSqr(f.at));
            if (left > 0.0f && d < 12.0) result = Math.max(result, 1.1f * left * (float)(1.0 - d / 12.0));
        }
        for (Entity e : viewer.level().getEntities(viewer, viewer.getBoundingBox().inflate(90.0))) {
            if (e instanceof CurseSlashEntity slash && slash.getMode() == 2) {
                result = Math.max(result, (float)(1.5 * (double)Math.max(0.0f, 1.0f - viewer.distanceTo(slash) / 90.0f)));
            }
            if (!(e instanceof SlashFxEntity s) || s.getMode() != 4 && s.getMode() != 7 && s.getMode() != 8) continue;
            result = Math.max(result, (float)((s.getMode() == 8 ? 1.15 : 1.4) * (double)Math.max(0.0f, 1.0f - viewer.distanceTo(s) / (s.getMode() == 8 ? 90.0f : 60.0f)) * (double)Math.max(0.0f, 1.0f - ((float)s.tickCount + delta) / (float)s.duration())));
        }
        return result;
    }

    private static java.util.List<PortBuffers.Batch> extracted = java.util.List.of();
    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            POSES.clear();
            PUNCHES.clear();
            FISTS.clear();
            extracted = java.util.List.of();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> PortBuffers.submit(extracted, ctx.poseStack(), ctx.submitNodeCollector()));
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            PortBuffers buffers = new PortBuffers();
            if (!FISTS.isEmpty()) {
                PoseStack fm = new PoseStack();
                CastVisuals.renderFists(fm, buffers, ctx.camera().position(), ctx.level().getGameTime(), ctx.deltaTracker().getGameTimeDeltaPartialTick(false), ctx.camera());
            }
            for (Player p : ctx.level().players()) {
                Pose pose = CastVisuals.pose(p);
                if (pose == null) continue;
                PoseStack m = new PoseStack();
                Vec3 camera = ctx.camera().position();
                Vec3 position = new Vec3(Mth.lerp((double)ctx.deltaTracker().getGameTimeDeltaPartialTick(false), (double)p.xo, (double)p.getX()), Mth.lerp((double)ctx.deltaTracker().getGameTimeDeltaPartialTick(false), (double)p.yo, (double)p.getY()), Mth.lerp((double)ctx.deltaTracker().getGameTimeDeltaPartialTick(false), (double)p.zo, (double)p.getZ()));
                m.pushPose();
                m.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
                float phase = ((float)ctx.level().getGameTime() + ctx.deltaTracker().getGameTimeDeltaPartialTick(false)) % 24.0f / 24.0f;
                if (pose.skill == Skill.RED.netId && pose.held && (p != Minecraft.getInstance().player || !Minecraft.getInstance().options.getCameraType().isFirstPerson())) {
                    Vec3 f = p.getViewVector(ctx.deltaTracker().getGameTimeDeltaPartialTick(false));
                    Vec3 r = SlashShader.right(p.getYRot());
                    Vec3 u = SlashShader.up(p.getYRot(), p.getXRot());
                    Vec3 center = new Vec3(0.0, (double)p.getEyeHeight() - 0.22, 0.0).add(f.scale(1.25)).add(r.scale(-0.4));
                    CastVisuals.bow(m, buffers, center, f, r, u, phase, Math.min(1.0f, (float)(ctx.level().getGameTime() - pose.started) / 50.0f));
                }
                boolean ownFirstPerson = p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
                if ((pose.skill == Skill.AO.netId || pose.skill == Skill.AKA.netId || pose.skill == Skill.MURASAKI.netId) && pose.held) {
                    float d = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);
                    Vec3 f = p.getViewVector(d);
                    Vec3 side = SlashShader.right(p.getYRot());
                    Vec3 cr = SlashShader.right(ctx.camera().yRot());
                    Vec3 cu = SlashShader.up(ctx.camera().yRot(), ctx.camera().xRot());
                    Vec3 hand = new Vec3(0.0, (double)p.getEyeHeight() - 0.3, 0.0).add(f.scale(0.85));
                    // In one's own first-person view the orb sits in the middle, just under the crosshair:
                    // clearly visible (it may cover part of the view), only lightly faded.
                    float size = 1.0f;
                    float dim = 0.0f;
                    if (ownFirstPerson) {
                        Vec3 camUp = SlashShader.up(p.getYRot(), p.getXRot());
                        hand = new Vec3(0.0, (double)p.getEyeHeight(), 0.0).add(f.scale(1.0)).add(camUp.scale(-0.22));
                        size = 0.85f;
                        dim = 0.1f;
                    }
                    float ticks = (float)(ctx.level().getGameTime() - pose.started) + d;
                    float t = Math.min(1.0f, ticks / 40.0f);
                    if (pose.skill == Skill.AO.netId || pose.skill == Skill.AKA.netId) {
                        float kind = pose.skill == Skill.AO.netId ? SlashShader.ORB_BLUE : SlashShader.ORB_RED;
                        float r = (0.14f + 0.14f * t) * size;
                        SlashShader.orb(m, buffers, hand, cr, cu, r * 1.7f, phase, kind, 0.6f);
                        SlashShader.orb(m, buffers, hand, cr, cu, r, phase, kind, dim);
                    } else {
                        CastVisuals.hollowPurple(m, buffers, hand, side, SlashShader.up(p.getYRot(), p.getXRot()), cr, cu, ticks, t, size, dim, phase);
                    }
                }
                if ((pose.skill == Skill.RCT.netId || pose.skill == Skill.GOJO_RCT.netId) && pose.held && !ownFirstPerson) {
                    for (int i = 0; i < 24; ++i) {
                        double a = (double)i * Math.PI / 6.0 + (double)phase * Math.PI * 2.0;
                        Vec3 c = new Vec3(Math.cos(a) * 0.65, ((double)i / 24.0 * 2.4 + (double)phase) % 2.4, Math.sin(a) * 0.65);
                        SlashShader.blade(m, buffers, SlashShader.right(ctx.camera().yRot()), new Vec3(0.0, 1.0, 0.0), c, 1.5707963267948966, 0.2f, 0.1f, phase, 6, (float)i / 24.0f);
                    }
                }
                if (pose.skill == Skill.MAHORAGA.netId && pose.held) {
                    Vec3 c = pose.anchor.subtract(position);
                    SlashShader.blade(m, buffers, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), c, 0.0, 2.8f, 2.8f, phase, 5, 0.4f);
                }
                if (pose.skill == Skill.CURSED_BARRAGE.netId) {
                    CastVisuals.renderBarrage(m, buffers, p, position, ctx.deltaTracker().getGameTimeDeltaPartialTick(false), pose, phase);
                }
                m.popPose();
            }
            extracted = buffers.snapshot();
        });
    }

    private static void renderBarrage(PoseStack m, PortBuffers consumers, Player p, Vec3 position, float delta, Pose pose, float phase) {
        if (p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        PunchVisual punch = CastVisuals.punch(p);
        float charge = Mth.clamp((float)(((float)(p.level().getGameTime() - pose.started) + delta) / 30.0f), (float)0.0f, (float)1.0f);
        Vec3 right = SlashShader.right(p.getYRot());
        Vec3 forward = new Vec3(-right.z, 0.0, right.x);
        if (forward.lengthSqr() < 0.001) {
            forward = new Vec3(0.0, 0.0, 1.0);
        }
        forward = forward.normalize();
        for (boolean left : new boolean[]{true, false}) {
            boolean active = punch != null && punch.left == left;
            float progress = active ? CastVisuals.punchProgress(p, punch, delta) : 0.0f;
            Vec3 hand = CastVisuals.handOrigin(position, p, left, active ? progress : 0.0f, forward, right);
            CastVisuals.aura(m, consumers, hand, forward, right, phase, Math.max(0.45f, charge), active ? progress : 0.25f);
        }
        if (punch != null) {
            float progress = CastVisuals.punchProgress(p, punch, delta);
            Vec3 f = new Vec3(punch.direction.x, 0.0, punch.direction.z);
            if (f.lengthSqr() < 0.001) {
                f = forward;
            }
            f = f.normalize();
            Vec3 r = new Vec3(-f.z, 0.0, f.x).normalize();
            Vec3 center = punch.origin.subtract(position).add(f.scale(0.22));
            CastVisuals.shockwave(m, consumers, center, f, r, progress, phase, punch.index);
        }
    }

    public static Vec3 handOrigin(Vec3 position, Player p, boolean left, float progress, Vec3 forward, Vec3 right) {
        double side = left ? -0.58 : 0.58;
        double extension = 0.1 + 0.92 * (double)Mth.clamp((float)progress, (float)0.0f, (float)1.0f);
        double lift = 0.02 + 0.08 * (double)(1.0f - Mth.clamp((float)progress, (float)0.0f, (float)1.0f));
        return new Vec3(0.0, (double)p.getEyeHeight() - 0.62 + lift, 0.0).add(right.scale(side)).add(forward.scale(extension));
    }

    public static void aura(PoseStack m, PortBuffers v, Vec3 center, Vec3 forward, Vec3 right, float phase, float scale, float progress) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        float pulse = 0.78f + 0.22f * (float)Math.sin((double)phase * Math.PI * 2.0);
        float size = scale * pulse;
        SlashShader.blade(m, v, right, up, center, (double)phase * Math.PI * 2.0, 0.38f * size, 0.42f * size, Math.min(0.98f, progress), 6, 0.11f);
        SlashShader.blade(m, v, forward, up, center.add(up.scale(0.04)), (double)(-phase) * Math.PI * 2.0, 0.3f * size, 0.34f * size, Math.min(0.98f, progress + 0.08f), 6, 0.47f);
        SlashShader.blade(m, v, right.add(forward).normalize(), up, center, (double)phase * Math.PI * 3.0, 0.22f * size, 0.58f * size, Math.min(0.98f, progress + 0.16f), 6, 0.82f);
    }

    private static void shockwave(PoseStack m, PortBuffers v, Vec3 center, Vec3 forward, Vec3 right, float progress, float phase, int index) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        float age = Mth.clamp((float)progress, (float)0.0f, (float)0.98f);
        float scale = 1.05f + age * 1.2f;
        SlashShader.blade(m, v, right, up, center, (double)(phase * 2.0f) + (double)index * 0.7, scale, 0.42f * scale, age, 7, 0.2f + (float)index * 0.13f);
        SlashShader.blade(m, v, forward, up, center, (double)(-phase * 2.0f) + (double)index * 0.4, scale * 0.82f, 0.26f * scale, Math.min(0.98f, age + 0.06f), 7, 0.63f);
    }

    public static void bow(PoseStack m, PortBuffers v, Vec3 c, Vec3 f, Vec3 r, Vec3 u, float phase, float charge) {
        CastVisuals.bow(m, v, c, f, r, u, phase, charge, 1.0f);
    }

    public static void bow(PoseStack m, PortBuffers v, Vec3 c, Vec3 f, Vec3 r, Vec3 u, float phase, float charge, float scale) {
        float span = 1.45f * scale;
        for (int i = 0; i < 18; ++i) {
            double a = -1.3 + (double)i * 2.6 / 18.0;
            double b = -1.3 + (double)(i + 1) * 2.6 / 18.0;
            Vec3 start = c.add(u.scale(Math.sin(a) * (double)span)).add(f.scale(Math.cos(a) * 0.55 * (double)scale));
            Vec3 end = c.add(u.scale(Math.sin(b) * (double)span)).add(f.scale(Math.cos(b) * 0.55 * (double)scale));
            CastVisuals.segment(m, v, start, end, r, (0.14f + charge * 0.09f) * scale, phase, 4, (float)i / 20.0f);
        }
        Vec3 pull = c.subtract(f.scale((0.2 + (double)charge * 0.6) * (double)scale));
        CastVisuals.segment(m, v, c.add(u.scale((double)span)).add(f.scale(0.15 * (double)scale)), pull, r, 0.035f * scale, phase, 4, 0.7f);
        CastVisuals.segment(m, v, c.add(u.scale((double)(-span))).add(f.scale(0.15 * (double)scale)), pull, r, 0.035f * scale, phase, 4, 0.6f);
        CastVisuals.segment(m, v, pull, c.add(f.scale(1.8 * (double)scale)), u, (0.09f + charge * 0.07f) * scale, phase, 4, 0.3f);
    }

    public static void segment(PoseStack m, PortBuffers v, Vec3 a, Vec3 b, Vec3 cross, float width, float time, int mode, float seed) {
        CastVisuals.segment(m, v, a, b, cross, width, time, mode, seed, 1.0f);
    }

    public static void segment(PoseStack m, PortBuffers v, Vec3 a, Vec3 b, Vec3 cross, float width, float time, int mode, float seed, float tint) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-4 || cross.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 dir = d.normalize();
        Vec3 c = cross.subtract(dir.scale(cross.dot(dir)));
        if (c.lengthSqr() < 1.0E-4) {
            return;
        }
        SlashShader.blade(m, v, dir, c.normalize(), a.add(b).scale(0.5), 0.0, (float)d.length() * 0.55f, width, time, mode, seed, tint);
    }

    public record Pose(int skill, boolean held, long started, long expires, Vec3 anchor) {
    }

    public record FistVisual(Vec3 at, Vec3 dir, boolean blackFlash, long started, float seed) {
    }

    public record PunchVisual(int index, boolean left, Vec3 origin, Vec3 direction, float charge, long started, long expires) {
    }
}

