package cn.blockforge.ryomensukuna.m2a542fea;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.AkaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MurasakiEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.TrainingDummyEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.GojoNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.JjkNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.SukunaNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.combat.BlackFlash;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaSkill;
import cn.blockforge.ryomensukuna.m2a542fea.config.JjkCommands;
import cn.blockforge.ryomensukuna.m2a542fea.config.JjkConfig;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.Infinity;
import cn.blockforge.ryomensukuna.m2a542fea.mobility.Mobility;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Growth;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import cn.blockforge.ryomensukuna.m2a542fea.entity.CurseSlashEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.RedBlastEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.SlashFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.WorldCutFxEntity;
import cn.blockforge.ryomensukuna.m2a542fea.item.SukunaItems;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.skill.ChannelCasting;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CombatSkills;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import cn.blockforge.ryomensukuna.m2a542fea.skill.TerrainCuts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public final class SukunaMod
implements ModInitializer {
    public static final String MOD_ID = "sukuna";
    private int tickCounter = 0;
    public static final EntityType<CurseSlashEntity> CURSE_SLASH = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("curse_slash"), EntityType.Builder.of(CurseSlashEntity::new, MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(64).updateInterval(2).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("curse_slash"))));
    public static final EntityType<SlashFxEntity> SLASH_FX = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("slash_fx"), EntityType.Builder.of(SlashFxEntity::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(64).updateInterval(3).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("slash_fx"))));
    public static final EntityType<RedBlastEntity> RED_BLAST = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("red_blast"), EntityType.Builder.of(RedBlastEntity::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(2).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("red_blast"))));
    public static final EntityType<WorldCutFxEntity> WORLD_CUT_FX = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("world_cut_fx"), EntityType.Builder.of(WorldCutFxEntity::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(96).updateInterval(3).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("world_cut_fx"))));
    public static final EntityType<ShrineEntity> SHRINE = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("shrine"), EntityType.Builder.of(ShrineEntity::new, MobCategory.MISC).sized(4.0f, 6.0f).clientTrackingRange(96).updateInterval(5).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("shrine"))));
    public static final EntityType<MahoragaEntity> MAHORAGA = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("mahoraga"), EntityType.Builder.of(MahoragaEntity::new, MobCategory.MONSTER).sized(1.0f, 2.6f).clientTrackingRange(64).updateInterval(3).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("mahoraga"))));
    public static final EntityType<AoEntity> AO = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("ao"), EntityType.Builder.<AoEntity>of(AoEntity::new, MobCategory.MISC).sized(0.8f, 0.8f).clientTrackingRange(96).updateInterval(2).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("ao"))));
    public static final EntityType<AkaEntity> AKA = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("aka"), EntityType.Builder.<AkaEntity>of(AkaEntity::new, MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(96).updateInterval(1).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("aka"))));
    public static final EntityType<MurasakiEntity> MURASAKI = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("murasaki"), EntityType.Builder.<MurasakiEntity>of(MurasakiEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(128).updateInterval(1).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("murasaki"))));
    public static final EntityType<VoidDomainEntity> VOID_DOMAIN = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("unlimited_void"), EntityType.Builder.<VoidDomainEntity>of(VoidDomainEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(128).updateInterval(5).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE, id("unlimited_void"))));
    public static final EntityType<TrainingDummyEntity> TRAINING_DUMMY = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("training_dummy"), EntityType.Builder.<TrainingDummyEntity>of(TrainingDummyEntity::new, MobCategory.MISC).sized(0.5f, 1.975f).eyeHeight(1.7775f).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id("training_dummy"))));
    public static final EntityType<GojoNpcEntity> GOJO_NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("gojo"), EntityType.Builder.<GojoNpcEntity>of(GojoNpcEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id("gojo"))));
    public static final EntityType<SukunaNpcEntity> SUKUNA_NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("sukuna"), EntityType.Builder.<SukunaNpcEntity>of(SukunaNpcEntity::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id("sukuna"))));
    public static final ParticleOptions CURSE_PARTICLE = ParticleTypes.CRIMSON_SPORE;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public void onInitialize() {
        JjkConfig.load();
        SukunaItems.register();
        SukunaSounds.register();
        FabricDefaultAttributeRegistry.register(MAHORAGA, (AttributeSupplier.Builder)MahoragaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TRAINING_DUMMY, ArmorStand.createAttributes());
        FabricDefaultAttributeRegistry.register(GOJO_NPC, JjkNpcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SUKUNA_NPC, JjkNpcEntity.createAttributes());
        MahoragaSkill.init();
        BlackFlash.register();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Infinity::allowDamage);
        // Anything held by Unlimited Void cannot strike with its own body.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) ->
            !(source.getEntity() != null && source.getEntity() == source.getDirectEntity() && VoidDomainEntity.stunned(source.getEntity())));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) Growth.onRespawn(newPlayer);
            else Growth.applyAttributes(newPlayer);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> JjkCommands.register(dispatcher));
        SukunaNet.registerServerReceivers();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ++this.tickCounter;
            CombatSkills.tick();
            Infinity.tickHeld();
            for (ServerLevel world : server.getAllLevels()) {
                TerrainCuts.tick(world);
            }
            server.getPlayerList().getPlayers().forEach(player -> {
                if ((this.tickCounter & 3) == 0) {
                    CurseManager.tickRegen(player);
                }
                CurseManager.tickCooldowns(player);
                ChannelCasting.tick(player);
                Growth.tick(player);
                Infinity.tick(player);
                Mobility.tick(player);
            });
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer)entity;
                CurseManager.onDeath(player);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CurseManager.clearTransient();
            GojoSkills.clear();
        });
    }
}

