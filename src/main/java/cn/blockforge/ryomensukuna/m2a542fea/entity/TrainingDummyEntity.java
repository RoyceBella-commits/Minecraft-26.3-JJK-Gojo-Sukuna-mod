package cn.blockforge.ryomensukuna.m2a542fea.entity;

import cn.blockforge.ryomensukuna.m2a542fea.item.SukunaItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Practice target: takes every technique, never dies, sneak + use picks it back up. */
public class TrainingDummyEntity extends ArmorStand {
    public TrainingDummyEntity(EntityType<? extends TrainingDummyEntity> type, Level level) {
        super(type, level);
        this.setShowArms(true);
        this.setCustomName(Component.translatable("entity.sukuna.training_dummy"));
        this.setCustomNameVisible(true);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (this.level() instanceof ServerLevel level) {
            this.spawnAtLocation(level, new ItemStack(SukunaItems.TRAINING_DUMMY));
            this.discard();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        if (this.isInvulnerableTo(level, source)) {
            return false;
        }
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), this.getY() + 1.2, this.getZ(), Math.max(1, Math.min(12, (int)(damage / 2.0f))), 0.2, 0.3, 0.2, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.NEUTRAL, 0.6f, 1.0f);
        this.setHealth(this.getMaxHealth());
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
