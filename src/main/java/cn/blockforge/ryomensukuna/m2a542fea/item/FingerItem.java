package cn.blockforge.ryomensukuna.m2a542fea.item;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Sukuna growth item. Eating it only succeeds (and is only consumed) when growth is allowed. */
public class FingerItem
extends Item {
    public FingerItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && !Progression.canUse(sp, StageRules.SUKUNA)) {
            Progression.explainRefusal(sp, StageRules.SUKUNA);
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (world.isClientSide() || !(user instanceof ServerPlayer player)) {
            return super.finishUsingItem(stack, world, user);
        }
        if (!Progression.useGrowthItem(player, StageRules.SUKUNA)) {
            return stack;
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SukunaSounds.FINGER_EAT, SoundSource.PLAYERS, 1.0f, 0.9f + world.getRandom().nextFloat() * 0.2f);
        return super.finishUsingItem(stack, world, user);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.sukuna.finger.route").withStyle(ChatFormatting.RED));
        builder.accept(Component.translatable("item.sukuna.growth.lock").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.sukuna.growth.rule").withStyle(ChatFormatting.DARK_GRAY));
    }
}
