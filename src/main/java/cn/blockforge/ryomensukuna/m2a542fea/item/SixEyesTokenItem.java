package cn.blockforge.ryomensukuna.m2a542fea.item;

import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
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

/** Gojo growth item (Six Eyes training certificate). Consumed only on a successful use. */
public class SixEyesTokenItem extends Item {
    public SixEyesTokenItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!Progression.useGrowthItem(sp, StageRules.GOJO)) {
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.sukuna.six_eyes_token.route").withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("item.sukuna.growth.lock").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.sukuna.growth.rule").withStyle(ChatFormatting.DARK_GRAY));
    }
}
