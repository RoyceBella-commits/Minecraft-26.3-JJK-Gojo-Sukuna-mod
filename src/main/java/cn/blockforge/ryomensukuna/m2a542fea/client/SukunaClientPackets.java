package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaPackets;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class SukunaClientPackets {
    public interface Receiver { void receive(Minecraft client, FriendlyByteBuf buffer); }
    public static void receive(Identifier id, Receiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(SukunaPackets.type(id), (payload, context) -> {
            var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
            try { receiver.receive(context.client(), buf); }
            finally { buf.release(); }
        });
    }
    public static void send(Identifier id, FriendlyByteBuf buffer) {
        ClientPlayNetworking.send(SukunaPackets.payload(id, buffer));
    }
}
