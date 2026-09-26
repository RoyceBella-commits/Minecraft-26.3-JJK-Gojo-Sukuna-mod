package cn.blockforge.ryomensukuna.m2a542fea.item;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.TrainingDummyEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Places a practice target that never dies; hits on it count as practice. */
public class TrainingDummyItem extends Item {
    public TrainingDummyItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 at = Vec3.atBottomCenterOf(pos);
        TrainingDummyEntity dummy = new TrainingDummyEntity(SukunaMod.TRAINING_DUMMY, level);
        float yaw = context.getPlayer() == null ? 0.0f : context.getPlayer().getYRot() + 180.0f;
        dummy.snapTo(at.x, at.y, at.z, yaw, 0.0f);
        if (!level.noCollision(dummy, dummy.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(dummy);
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.sukuna.training_dummy.tip").withStyle(ChatFormatting.GRAY));
    }
}
