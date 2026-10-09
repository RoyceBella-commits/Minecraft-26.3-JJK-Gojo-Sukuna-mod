package cn.blockforge.ryomensukuna.m2a542fea.net;

import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Fixed-size, typed payloads retain the original wire fields without accepting unbounded data. */
public final class SukunaPackets {
    public record Payload(CustomPacketPayload.Type<Payload> type, byte[] data) implements CustomPacketPayload {}
    private static final Map<Identifier, CustomPacketPayload.Type<Payload>> TYPES = new LinkedHashMap<>();
    private static final Map<Identifier, Integer> LENGTHS = new LinkedHashMap<>();
    public static void registerTypes() {
        register(SukunaNet.C2S_CHANNEL, 5, true);
        register(SukunaNet.C2S_CAST, 8, true);
        register(SukunaNet.C2S_SELECT, 4, true);
        register(SukunaNet.S2C_POSE, 45, false);
        register(SukunaNet.S2C_PUNCH, 73, false);
        register(SukunaNet.S2C_STATE, SukunaNet.STATE_LENGTH, false);
        register(SukunaNet.C2S_MOBILITY, 9, true);
        register(SukunaNet.C2S_QUICK, 1, true);
        register(SukunaNet.S2C_DANGER, 28, false);
        register(SukunaNet.S2C_FEEDBACK, 5, false);
        register(SukunaNet.S2C_FIST, SukunaNet.FIST_LENGTH, false);
        register(SukunaNet.S2C_WHEEL, SukunaNet.WHEEL_LENGTH, false);
    }
    private static void register(Identifier id, int length, boolean c2s) {
        var type = new CustomPacketPayload.Type<Payload>(id);
        TYPES.put(id, type); LENGTHS.put(id, length);
        StreamCodec<RegistryFriendlyByteBuf, Payload> codec = StreamCodec.of((buf, payload) -> {
            if (payload.data.length != length) throw new IllegalArgumentException("Invalid payload size: " + id);
            buf.writeBytes(payload.data);
        }, buf -> {
            if (buf.readableBytes() != length) throw new IllegalArgumentException("Invalid payload size: " + id);
            byte[] data = new byte[length]; buf.readBytes(data); return new Payload(type, data);
        });
        if (c2s) PayloadTypeRegistry.serverboundPlay().register(type, codec);
        else PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }
    public static CustomPacketPayload.Type<Payload> type(Identifier id) { return TYPES.get(id); }
    public static Payload payload(Identifier id, FriendlyByteBuf buffer) {
        try {
            if (buffer.readableBytes() != LENGTHS.get(id)) throw new IllegalArgumentException("Invalid payload size: " + id);
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            return new Payload(type(id), bytes);
        } finally { buffer.release(); }
    }
    public interface Receiver { void receive(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buffer); }
    public static void receive(Identifier id, Receiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(type(id), (payload, context) -> {
            var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
            try { receiver.receive(context.server(), context.player(), buf); }
            finally { buf.release(); }
        });
    }
    public static void send(ServerPlayer player, Identifier id, FriendlyByteBuf buffer) {
        ServerPlayNetworking.send(player, payload(id, buffer));
    }
}
