package cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.skill.ChannelCasting;
import cn.blockforge.ryomensukuna.m2a542fea.util.CurseFx;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class MahoragaSkill {
    /** A recalled Mahoraga (health + adaptation), restored by the next summon. */
    public static final AttachmentType<CompoundTag> STORE = AttachmentRegistry.<CompoundTag>builder()
        .persistent(CompoundTag.CODEC).copyOnDeath().buildAndRegister(SukunaMod.id("mahoraga_store"));

    private MahoragaSkill() {
    }

    public static void init() {
    }

    public static void cast(ServerPlayer player, float charge) {
        MahoragaSkill.castAt(player, charge, ChannelCasting.shadow(player));
    }

    private static List<MahoragaEntity> owned(ServerPlayer player) {
        return player.level().getEntitiesOfClass(MahoragaEntity.class, player.getBoundingBox().inflate(256.0),
            e -> e.isAlive() && player.getStringUUID().equals(e.ownerUuid()));
    }

    /** Sends an already summoned Mahoraga back into the shadows, keeping its health and adaptation. */
    public static boolean recall(ServerPlayer player) {
        List<MahoragaEntity> list = owned(player);
        if (list.isEmpty()) return false;
        MahoragaEntity m = list.get(0);
        player.setAttached(STORE, m.saveForRecall());
        for (MahoragaEntity e : list) {
            Vec3 at = e.position();
            SlashFxEntity.spawn(player.level(), at.x, at.y + 0.015, at.z, 5, 0.0f, 90.0f, 2.4f);
            CurseFx.particles(player.level(), SukunaMod.CURSE_PARTICLE, at.x, at.y + 1.2, at.z, 50, 0.6, 1.2, 0.6, 0.02);
            player.level().playSound(null, at.x, at.y, at.z, SukunaSounds.MAHORAGA_SPAWN, SoundSource.PLAYERS, 1.6f, 1.4f);
            e.discard();
        }
        SukunaNet.actionBar(player, "sukuna.hint.mahoraga_recalled");
        return true;
    }

    public static void castAt(ServerPlayer player, float charge, Vec3 anchor) {
        // Only one Mahoraga per caster: a new summon replaces the previous one.
        for (MahoragaEntity old : owned(player)) {
            old.discard();
        }
        MahoragaEntity m = new MahoragaEntity(SukunaMod.MAHORAGA, player.level());
        m.snapTo(anchor.x, anchor.y, anchor.z, player.getYRot(), 0.0f);
        m.ownerUuid(player.getStringUUID());
        CompoundTag stored = player.getAttached(STORE);
        boolean restored = stored != null;
        if (restored) {
            m.restoreFromRecall(stored);
            player.removeAttached(STORE);
        }
        m.beginEmergence();
        player.level().addFreshEntity((Entity)m);
        SlashFxEntity.spawn(player.level(), anchor.x, anchor.y + 0.015, anchor.z, 5, 0.0f, 90.0f, 2.8f);
        player.level().playSound(null, anchor.x, anchor.y, anchor.z, SukunaSounds.MAHORAGA_SPAWN, SoundSource.HOSTILE, 2.0f, 0.8f);
        SukunaNet.actionBar(player, restored ? "sukuna.hint.mahoraga_return" : "sukuna.hint.mahoraga_summon", new Object[0]);
    }
}
