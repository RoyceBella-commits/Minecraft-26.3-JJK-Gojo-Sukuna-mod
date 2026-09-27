package cn.blockforge.ryomensukuna.m2a542fea.item;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.item.FingerItem;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ItemLike;

public final class SukunaItems {
    public static final FingerItem FINGER = new FingerItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("finger"))).food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.6f).alwaysEdible().build()).stacksTo(16));
    public static final SixEyesTokenItem SIX_EYES_TOKEN = new SixEyesTokenItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("six_eyes_token"))).stacksTo(16));
    public static final TrainingDummyItem TRAINING_DUMMY = new TrainingDummyItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("training_dummy"))).stacksTo(16));
    public static final SpawnEggItem MAHORAGA_SPAWN_EGG = new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("mahoraga_spawn_egg"))).spawnEgg(SukunaMod.MAHORAGA));
    public static final SpawnEggItem GOJO_SPAWN_EGG = new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("gojo_spawn_egg"))).spawnEgg(SukunaMod.GOJO_NPC));
    public static final SpawnEggItem SUKUNA_SPAWN_EGG = new SpawnEggItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("sukuna_spawn_egg"))).spawnEgg(SukunaMod.SUKUNA_NPC));
    public static final NpcSealItem PRISON_REALM = new NpcSealItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("prison_realm"))).stacksTo(1), cn.blockforge.ryomensukuna.m2a542fea.entity.npc.GojoNpcEntity.class);
    public static final NpcSealItem SUKUNA_TALISMAN = new NpcSealItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("sukuna_talisman"))).stacksTo(1), cn.blockforge.ryomensukuna.m2a542fea.entity.npc.SukunaNpcEntity.class);
    public static final ForceGrowthItem GOJO_SECRET_SCROLL = new ForceGrowthItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("gojo_secret_scroll"))).stacksTo(1), cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules.GOJO);
    public static final ForceGrowthItem CURSED_WOMB_BOX = new ForceGrowthItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("cursed_womb_box"))).stacksTo(1), cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules.SUKUNA);
    public static final CreativeModeTab TAB = (CreativeModeTab)Registry.register((Registry)BuiltInRegistries.CREATIVE_MODE_TAB, (Identifier)SukunaItems.id("main"), FabricCreativeModeTab.builder().title((Component)Component.translatable((String)"itemGroup.sukuna.main")).icon(() -> new ItemStack((ItemLike)FINGER)).displayItems((ctx, entries) -> {
        entries.accept(new ItemStack((ItemLike)SIX_EYES_TOKEN));
        entries.accept(new ItemStack((ItemLike)FINGER));
        entries.accept(new ItemStack((ItemLike)TRAINING_DUMMY));
        entries.accept(new ItemStack((ItemLike)MAHORAGA_SPAWN_EGG));
        entries.accept(new ItemStack((ItemLike)GOJO_SPAWN_EGG));
        entries.accept(new ItemStack((ItemLike)SUKUNA_SPAWN_EGG));
        entries.accept(new ItemStack((ItemLike)GOJO_SECRET_SCROLL));
        entries.accept(new ItemStack((ItemLike)CURSED_WOMB_BOX));
        entries.accept(new ItemStack((ItemLike)PRISON_REALM));
        entries.accept(new ItemStack((ItemLike)SUKUNA_TALISMAN));
    }).build());

    private SukunaItems() {
    }

    public static Identifier id(String path) {
        return SukunaMod.id(path);
    }

    public static void register() {
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("finger"), (FINGER));
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("mahoraga_spawn_egg"), MAHORAGA_SPAWN_EGG);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("six_eyes_token"), SIX_EYES_TOKEN);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("training_dummy"), TRAINING_DUMMY);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("gojo_spawn_egg"), GOJO_SPAWN_EGG);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("sukuna_spawn_egg"), SUKUNA_SPAWN_EGG);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("gojo_secret_scroll"), GOJO_SECRET_SCROLL);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("cursed_womb_box"), CURSED_WOMB_BOX);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("prison_realm"), PRISON_REALM);
        Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)SukunaItems.id("sukuna_talisman"), SUKUNA_TALISMAN);
    }
}

