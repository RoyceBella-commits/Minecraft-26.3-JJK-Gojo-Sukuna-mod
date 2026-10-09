package cn.blockforge.ryomensukuna.m2a542fea.client;
import static cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.*;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
public final class SukunaClientNetworking {
    public static void registerClientReceivers() {
        SukunaClientPackets.receive(S2C_POSE, (client, buf) -> {
            UUID uuid = buf.readUUID();
            int skill = buf.readInt();
            boolean held = buf.readBoolean();
            Vec3 anchor = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> CastVisuals.receive(uuid, skill, held, anchor));
        });
        SukunaClientPackets.receive(S2C_PUNCH, (client, buf) -> {
            UUID uuid = buf.readUUID();
            int index = buf.readInt();
            boolean left = buf.readBoolean();
            Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 direction = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float charge = buf.readFloat();
            client.execute(() -> CastVisuals.punch(uuid, index, left, origin, direction, charge));
        });
        SukunaClientPackets.receive(S2C_STATE, (client, buf) -> {
            float energy = buf.readFloat();
            float max = buf.readFloat();
            int fingers = buf.readInt();
            int mask = buf.readInt();
            int selected = buf.readInt();
            float cd = buf.readFloat();
            int route = buf.readByte();
            int stage = buf.readByte();
            int hits = buf.readInt();
            int used = buf.readByte();
            int flags = buf.readByte();
            int postHits = buf.readInt();
            float gold = buf.readFloat();
            float goldMax = buf.readFloat();
            float burnout = buf.readFloat();
            float dash = buf.readFloat();
            float leap = buf.readFloat();
            boolean infinity = buf.readBoolean();
            client.execute(() -> SukunaClientState.setState(energy, max, fingers, mask, selected, cd, route, stage, hits, used, flags,
                postHits, gold, goldMax, burnout, dash, leap, infinity));
        });
        SukunaClientPackets.receive(S2C_FEEDBACK, (client, buf) -> {
            int skillId = buf.readInt();
            byte reason = buf.readByte();
            client.execute(() -> SukunaClientState.onFeedback(skillId, reason));
        });
        SukunaClientPackets.receive(S2C_FIST, (client, buf) -> {
            Vec3 at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 dir = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            boolean flash = buf.readBoolean();
            client.execute(() -> CastVisuals.fist(at, dir, flash));
        });
        SukunaClientPackets.receive(cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet.S2C_WHEEL, (client, buf) -> {
            int entityId = buf.readInt();
            boolean shown = buf.readBoolean();
            int steps = buf.readInt();
            long turnedAt = buf.readLong();
            client.execute(() -> cn.blockforge.ryomensukuna.m2a542fea.client.render.WheelRenderer.receive(entityId, shown, steps, turnedAt));
        });
        SukunaClientPackets.receive(S2C_DANGER, (client, buf) -> {
            int skillId = buf.readInt();
            Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> SukunaClientState.danger(skillId, from));
        });
    }
}
