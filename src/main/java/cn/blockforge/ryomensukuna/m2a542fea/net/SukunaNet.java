package cn.blockforge.ryomensukuna.m2a542fea.net;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.ChannelCasting;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import cn.blockforge.ryomensukuna.m2a542fea.skill.SkillDispatcher;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class SukunaNet {
    public static final Identifier C2S_CHANNEL = SukunaMod.id("channel");
    public static final Identifier S2C_POSE = SukunaMod.id("pose");
    public static final Identifier S2C_PUNCH = SukunaMod.id("punch");
    public static final Identifier C2S_CAST = SukunaMod.id("cast");
    public static final Identifier C2S_SELECT = SukunaMod.id("select");
    public static final Identifier S2C_STATE = SukunaMod.id("state2");
    public static final Identifier S2C_FEEDBACK = SukunaMod.id("feedback");
    public static final Identifier C2S_MOBILITY = SukunaMod.id("mobility");
    public static final Identifier C2S_QUICK = SukunaMod.id("quick");
    public static final Identifier S2C_DANGER = SukunaMod.id("danger");
    public static final Identifier S2C_FIST = SukunaMod.id("fist");
    public static final int FIST_LENGTH = 49;
    public static final int STATE_LENGTH = 57;
    public static final byte FAIL_LOCKED = 0;
    public static final byte FAIL_ENERGY = 1;
    public static final byte FAIL_COOLDOWN = 2;
    public static final byte CAST_OK = 3;
    public static final byte FAIL_BURNOUT = 4;
    public static final byte FAIL_FULL = 5;
    public static final byte FAIL_CANCELLED = 6;
    public static final byte FAIL_STUNNED = 7;
    public static final byte QUICK_INFINITY = 0;

    private SukunaNet() {
    }

    public static void pose(ServerPlayer player, int skill, boolean held, Vec3 anchor) {
        for (ServerPlayer viewer : player.level().players()) {
            if (!ServerPlayNetworking.canSend(viewer, S2C_POSE) || viewer.distanceToSqr(player) > 65536.0) continue;
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeUUID(player.getUUID());
            packet.writeInt(skill);
            packet.writeBoolean(held);
            packet.writeDouble(anchor.x);
            packet.writeDouble(anchor.y);
            packet.writeDouble(anchor.z);
            SukunaPackets.send(viewer, S2C_POSE, packet);
        }
    }

    public static void punch(ServerPlayer player, int index, boolean left, Vec3 origin, Vec3 direction, float charge) {
        for (ServerPlayer viewer : player.level().players()) {
            if (!ServerPlayNetworking.canSend(viewer, S2C_PUNCH) || viewer.distanceToSqr(player) > 65536.0) continue;
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeUUID(player.getUUID());
            packet.writeInt(index);
            packet.writeBoolean(left);
            packet.writeDouble(origin.x);
            packet.writeDouble(origin.y);
            packet.writeDouble(origin.z);
            packet.writeDouble(direction.x);
            packet.writeDouble(direction.y);
            packet.writeDouble(direction.z);
            packet.writeFloat(charge);
            SukunaPackets.send(viewer, S2C_PUNCH, packet);
        }
    }

    /** A landed bare-handed strike (Black Flash or not) for the cool hit visuals on nearby clients. */
    public static void fist(net.minecraft.server.level.ServerLevel level, Vec3 at, Vec3 dir, boolean blackFlash) {
        for (ServerPlayer viewer : level.players()) {
            if (!ServerPlayNetworking.canSend(viewer, S2C_FIST) || viewer.distanceToSqr(at) > 4096.0) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeDouble(at.x);
            buf.writeDouble(at.y);
            buf.writeDouble(at.z);
            buf.writeDouble(dir.x);
            buf.writeDouble(dir.y);
            buf.writeDouble(dir.z);
            buf.writeBoolean(blackFlash);
            SukunaPackets.send(viewer, S2C_FIST, buf);
        }
    }

    public static void danger(ServerPlayer viewer, Skill skill, Vec3 source) {
        if (!ServerPlayNetworking.canSend(viewer, S2C_DANGER)) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(skill.netId);
        buf.writeDouble(source.x);
        buf.writeDouble(source.y);
        buf.writeDouble(source.z);
        SukunaPackets.send(viewer, S2C_DANGER, buf);
    }

    public static void registerServerReceivers() {
        SukunaPackets.registerTypes();
        SukunaPackets.receive(C2S_CHANNEL, (server, player, buf) -> {
            if (buf.readableBytes() != 5) {
                return;
            }
            int id = buf.readInt();
            boolean held = buf.readBoolean();
            server.execute(() -> ChannelCasting.input(player, id, held));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ChannelCasting.end(handler.player));
        SukunaPackets.receive(C2S_CAST, (server, player, buf) -> {
            if (buf.readableBytes() != 8) {
                return;
            }
            int skillId = buf.readInt();
            float charge = buf.readFloat();
            server.execute(() -> SkillDispatcher.cast(player, Skill.byId(skillId), charge));
        });
        SukunaPackets.receive(C2S_SELECT, (server, player, buf) -> {
            if (buf.readableBytes() != 4) {
                return;
            }
            int skillId = buf.readInt();
            server.execute(() -> {
                Skill s = Skill.byId(skillId);
                if (s == null || !CurseManager.unlocked(player, s)) {
                    CurseManager.sync(player);
                    return;
                }
                CurseState st = CurseManager.of(player);
                CurseManager.setState(player, st.withSelected(skillId));
                CurseManager.sync(player);
            });
        });
        SukunaPackets.receive(C2S_MOBILITY, (server, player, buf) -> {
            if (buf.readableBytes() != 9) {
                return;
            }
            int kind = buf.readByte();
            float forward = buf.readFloat();
            float strafe = buf.readFloat();
            if (!Float.isFinite(forward) || !Float.isFinite(strafe)) return;
            float f = Math.max(-1.0f, Math.min(1.0f, forward));
            float s = Math.max(-1.0f, Math.min(1.0f, strafe));
            server.execute(() -> Mobility.handle(player, kind, f, s));
        });
        SukunaPackets.receive(C2S_QUICK, (server, player, buf) -> {
            if (buf.readableBytes() != 1) {
                return;
            }
            byte action = buf.readByte();
            server.execute(() -> {
                if (action == QUICK_INFINITY) Infinity.toggle(player);
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CurseManager.sync(handler.player));
    }

    public static void sendState(ServerPlayer player, CurseState state, float selectedCooldown, float burnout, float dashCd, float leapCd, boolean infinity) {
        if (!ServerPlayNetworking.canSend(player, S2C_STATE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(state.energy());
        buf.writeFloat(state.maxEnergy());
        buf.writeInt(state.fingers());
        buf.writeInt(state.unlockedMask());
        buf.writeInt(state.selected());
        buf.writeFloat(selectedCooldown);
        buf.writeByte(state.route());
        buf.writeByte(state.stage());
        buf.writeInt(state.hits());
        buf.writeByte(state.usedKinds());
        buf.writeByte(state.flags());
        buf.writeInt(state.postDomainHits());
        buf.writeFloat(state.goldHp());
        buf.writeFloat(state.goldMax());
        buf.writeFloat(burnout);
        buf.writeFloat(dashCd);
        buf.writeFloat(leapCd);
        buf.writeBoolean(infinity);
        SukunaPackets.send(player, S2C_STATE, buf);
    }

    public static void sendFeedback(ServerPlayer player, int skillId, byte reason) {
        if (!ServerPlayNetworking.canSend(player, S2C_FEEDBACK)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(skillId);
        buf.writeByte(reason);
        SukunaPackets.send(player, S2C_FEEDBACK, buf);
    }

    public static void actionBar(ServerPlayer player, String key, Object ... args) {
        player.connection.send(new ClientboundSystemChatPacket(Component.translatable(key, args), true));
    }

    public static void title(ServerPlayer player, String titleKey, String subtitleKey, int fadeIn, int stay, int fadeOut) {
        SukunaNet.titleComponent(player, Component.translatable(titleKey), Component.translatable(subtitleKey));
    }

    public static void titleComponent(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    /** Stage label helper for server-built messages. */
    public static String stageLabel(int stage) {
        return StageRules.roman(stage);
    }
}
