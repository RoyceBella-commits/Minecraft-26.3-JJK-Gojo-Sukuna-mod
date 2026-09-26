package cn.blockforge.ryomensukuna.m2a542fea.config;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /jjk terrain on|off — operators toggle technique terrain destruction (damage and control stay). */
public final class JjkCommands {
    private JjkCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jjk")
            .then(Commands.literal("terrain")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable(JjkConfig.terrainDestruction() ? "sukuna.command.terrain.on" : "sukuna.command.terrain.off"), false);
                    return 1;
                })));
    }

    private static int set(CommandSourceStack source, boolean on) {
        JjkConfig.setTerrainDestruction(on);
        source.sendSuccess(() -> Component.translatable(on ? "sukuna.command.terrain.on" : "sukuna.command.terrain.off"), true);
        return 1;
    }
}
