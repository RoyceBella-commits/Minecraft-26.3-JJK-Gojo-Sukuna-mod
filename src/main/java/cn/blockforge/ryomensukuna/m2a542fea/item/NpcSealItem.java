package cn.blockforge.ryomensukuna.m2a542fea.item;

import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Admin / single-player tool: right-click instantly kills a spawn-egg Gojo or Sukuna (the one in
 * the crosshair within 64 blocks, otherwise the nearest one). Nobody else can use it.
 */
public class NpcSealItem extends Item {
    private static final double RANGE = 64.0;
    private final Class<? extends JjkNpcEntity> target;

    public NpcSealItem(Item.Properties settings, Class<? extends JjkNpcEntity> target) {
        super(settings);
        this.target = target;
    }

    public static boolean permitted(ServerPlayer p) {
        return p.level().getServer().isSingleplayer() || p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;
        }
        if (!permitted(sp)) {
            SukunaNet.actionBar(sp, "sukuna.hint.seal_denied");
            return InteractionResult.FAIL;
        }
        JjkNpcEntity victim = this.find(sl, sp);
        if (victim == null) {
            SukunaNet.actionBar(sp, "sukuna.hint.seal_none");
            return InteractionResult.FAIL;
        }
        Vec3 at = victim.getBoundingBox().getCenter();
        CurseFx.particles(sl, ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 80, 0.5, 1.0, 0.5, 0.3);
        CurseFx.particles(sl, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 1.6f);
        victim.kill(sl);
        SukunaNet.actionBar(sp, "sukuna.hint.seal_done");
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
        return InteractionResult.SUCCESS_SERVER;
    }

    private JjkNpcEntity find(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1.0f).scale(RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, end, new AABB(eye, end).inflate(1.5),
            e -> this.target.isInstance(e) && e.isAlive(), 0.5f);
        if (hit != null) return (JjkNpcEntity)hit.getEntity();
        List<? extends JjkNpcEntity> near = level.getEntitiesOfClass(this.target, p.getBoundingBox().inflate(RANGE), JjkNpcEntity::isAlive);
        return near.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.sukuna.seal.tip").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.sukuna.seal.permission").withStyle(ChatFormatting.DARK_GRAY));
    }
}
