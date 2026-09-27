package cn.blockforge.ryomensukuna.m2a542fea.item;

import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Admin / single-player shortcut: right-click raises the route one stage without practice,
 * sneak + right-click goes straight to stage V. Never consumed and has no recipe.
 */
public class ForceGrowthItem extends Item {
    private final int route;

    public ForceGrowthItem(Item.Properties settings, int route) {
        super(settings);
        this.route = route;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!NpcSealItem.permitted(sp)) {
            SukunaNet.actionBar(sp, "sukuna.hint.seal_denied");
            return InteractionResult.FAIL;
        }
        int stage = CurseManager.of(sp).route() == StageRules.NONE ? 0 : CurseManager.of(sp).stage();
        int target = sp.isShiftKeyDown() ? StageRules.MAX_STAGE : stage + 1;
        if (!Progression.forceAdvance(sp, this.route, target)) {
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(this.route == StageRules.GOJO ? "item.sukuna.force_growth.gojo" : "item.sukuna.force_growth.sukuna")
            .withStyle(this.route == StageRules.GOJO ? ChatFormatting.AQUA : ChatFormatting.RED));
        builder.accept(Component.translatable("item.sukuna.force_growth.tip").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.sukuna.seal.permission").withStyle(ChatFormatting.DARK_GRAY));
    }
}
