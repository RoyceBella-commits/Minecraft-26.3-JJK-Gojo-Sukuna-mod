package cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.Adaptation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MahoragaAdaptation {
    public static final String WEB = "minecraft:cobweb";
    public static final String SOUL_GROUND = "minecraft:soul_ground";
    private static final UUID GROWTH = UUID.fromString("9ebd1287-0dc4-46cd-8888-342fe4421c19");
    private static final UUID VITALITY_HP = UUID.fromString("821765e5-024c-463d-865e-bd2d56d27cb8");
    private final MahoragaEntity mob;
    private final Adaptation state = new Adaptation();
    private final Map<String, Long> hits = new HashMap<String, Long>();
    private Vec3 previousPosition;
    private int soulWalkTicks;
    private BlockPos safePosition;
    private String safeDimension = "";
    private long rescueAfter;

    public MahoragaAdaptation(MahoragaEntity mob) {
        this.mob = mob;
    }

    public boolean has(Adaptation.Evolution evolution) {
        return this.state.has(evolution);
    }

    public boolean immune(String id, boolean effect) {
        Adaptation.Entry entry = this.state.get(id, effect);
        return entry != null && entry.immune();
    }

    public static String damageId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:" + source.getMsgId());
    }

    private static Adaptation.Evolution classify(String id, DamageSource source) {
        return switch (id) {
            case "minecraft:mob_attack", "minecraft:player_attack", "minecraft:mob_attack_no_aggro" -> Adaptation.Evolution.VITALITY;
            case "minecraft:on_fire", "minecraft:in_fire", "minecraft:hot_floor" -> Adaptation.Evolution.HEAT;
            case "minecraft:drown" -> Adaptation.Evolution.WATER;
            case "minecraft:lava" -> Adaptation.Evolution.LAVA;
            case "minecraft:freeze" -> Adaptation.Evolution.FROST;
            case "minecraft:starve" -> Adaptation.Evolution.FOOD;
            case "minecraft:out_of_world" -> Adaptation.Evolution.VOID;
            case "minecraft:fall", "minecraft:fly_into_wall", "minecraft:stalagmite" -> Adaptation.Evolution.FLIGHT;
            case "minecraft:wither", "minecraft:magic", "minecraft:indirect_magic" -> Adaptation.Evolution.REGEN;
            default -> source.is(DamageTypeTags.IS_EXPLOSION) ? Adaptation.Evolution.VITALITY : (source.is(DamageTypeTags.IS_PROJECTILE) ? Adaptation.Evolution.PROJECTILE : Adaptation.Evolution.NONE);
        };
    }

    private Adaptation.Entry expose(String id, boolean effect, Adaptation.Evolution evolution, boolean accelerate) {
        String key = (effect ? "effect:" : "damage:") + id;
        long now = this.mob.level().getGameTime();
        Adaptation.Entry entry = this.state.get(id, effect);
        if (entry == null) {
            entry = this.state.start(id, effect, evolution);
            this.hits.put(key, now);
            this.mob.adaptationMessage((Component)Component.literal((String)"\u6cd5\u8f6e\u5f00\u59cb\u89e3\u6790 \u00b7 ").append(this.name(entry)));
            return entry;
        }
        // Adaptation is purely time-based once analysis has started (one stage every 3 s);
        // repeated hits no longer speed it up.
        this.hits.put(key, now);
        return entry;
    }

    public void damageExposure(DamageSource source) {
        this.expose(MahoragaAdaptation.damageId(source), false, MahoragaAdaptation.classify(MahoragaAdaptation.damageId(source), source), true);
    }

    public void environmentExposure(DamageSource source) {
        Long previous = this.hits.get("damage:" + MahoragaAdaptation.damageId(source));
        if (previous == null || this.mob.level().getGameTime() - previous >= 10L) {
            this.damageExposure(source);
        }
    }

    public float scale(DamageSource source, float amount) {
        Adaptation.Entry entry = this.state.get(MahoragaAdaptation.damageId(source), false);
        return entry == null ? amount : amount * entry.multiplier();
    }

    public boolean protectedFrom(DamageSource source) {
        return this.has(Adaptation.Evolution.VITALITY) && this.mob.level().getGameTime() < this.state.protectedUntil && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public boolean effect(MobEffectInstance effect, boolean accelerate) {
        String id;
        if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
            return false;
        }
        Adaptation.Evolution evolution = switch (id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString()) {
            case "minecraft:darkness", "minecraft:blindness" -> Adaptation.Evolution.NIGHT;
            case "minecraft:hunger" -> Adaptation.Evolution.FOOD;
            case "minecraft:poison", "minecraft:wither" -> Adaptation.Evolution.REGEN;
            default -> Adaptation.Evolution.NONE;
        };
        return this.expose(id, true, evolution, accelerate).immune();
    }

    public void touchWeb() {
        this.expose(WEB, true, Adaptation.Evolution.NONE, false);
    }

    private Component name(Adaptation.Entry entry) {
        MobEffect effect;
        if (entry.id.equals(WEB)) {
            return Component.literal((String)"\u8718\u86db\u7f51\u675f\u7f1a");
        }
        if (entry.id.equals(SOUL_GROUND)) {
            return Component.literal((String)"\u7075\u9b42\u5730\u9762");
        }
        if (entry.effect && (effect = (MobEffect)BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(entry.id))) != null) {
            return effect.getDisplayName();
        }
        String label = switch (entry.id) {
            case "minecraft:mob_attack", "minecraft:mob_attack_no_aggro" -> "\u751f\u7269\u8fd1\u6218";
            case "minecraft:player_attack" -> "\u73a9\u5bb6\u8fd1\u6218";
            case "minecraft:player_explosion", "minecraft:explosion" -> "\u7206\u70b8";
            case "minecraft:arrow" -> "\u7bad\u77e2";
            case "minecraft:trident" -> "\u4e09\u53c9\u621f";
            case "minecraft:in_fire", "minecraft:on_fire" -> "\u706b\u7130";
            case "minecraft:lava" -> "\u5ca9\u6d46";
            case "minecraft:hot_floor" -> "\u707c\u70ed\u5730\u9762";
            case "minecraft:fall" -> "\u6454\u843d";
            case "minecraft:drown" -> "\u6eba\u6c34";
            case "minecraft:freeze" -> "\u51bb\u7ed3";
            case "minecraft:magic", "minecraft:indirect_magic" -> "\u9b54\u6cd5";
            case "minecraft:wither" -> "\u51cb\u96f6";
            case "minecraft:out_of_world" -> "\u865a\u7a7a";
            default -> entry.id;
        };
        return Component.literal((String)label);
    }

    private void complete(Adaptation.Entry entry) {
        this.mob.turnAdaptationWheel();
        this.mob.playSound(SukunaSounds.MAHORAGA_ADAPT, 0.8f, 1.0f);
        MutableComponent message = entry.evolved() ? Component.literal((String)("\u8fdb\u5316\u5b8c\u6210 \u00b7 " + entry.evolution.title)) : Component.literal((String)(entry.immune() ? "\u5b8c\u5168\u9002\u5e94 \u00b7 " : "\u9002\u5e94\u9636\u6bb5 " + entry.stage() + "/5 \u00b7 ")).append(this.name(entry));
        this.mob.adaptationMessage((Component)message);
        this.updateAttributes();
    }

    private void attribute(net.minecraft.core.Holder<Attribute> type, UUID legacyId, double amount) {
        Identifier id = Identifier.fromNamespaceAndPath("sukuna", "adaptation/" + legacyId);
        AttributeInstance instance = this.mob.getAttribute(type);
        if (instance == null) {
            return;
        }
        AttributeModifier old = instance.getModifier(id);
        if (old != null && old.amount() == amount) {
            return;
        }
        if (old != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void updateAttributes() {
        this.attribute(Attributes.ATTACK_DAMAGE, GROWTH, this.state.attack());
        this.attribute(Attributes.MAX_HEALTH, VITALITY_HP, this.has(Adaptation.Evolution.VITALITY) ? 20.0 : 0.0);
    }

    public void tick() {
        for (Adaptation.Entry entry : this.state.tick()) {
            this.complete(entry);
        }
        long now = this.mob.level().getGameTime();
        if (this.has(Adaptation.Evolution.HEAT) || this.has(Adaptation.Evolution.LAVA)) {
            this.mob.clearFire();
        }
        if (this.has(Adaptation.Evolution.FROST)) {
            this.mob.setTicksFrozen(0);
        }
        if (this.has(Adaptation.Evolution.WATER)) {
            this.mob.setAirSupply(this.mob.getMaxAirSupply());
        }
        this.environment();
        this.deflectProjectiles();
        if (this.has(Adaptation.Evolution.VOID) && this.mob.getY() < (double)(this.mob.level().getMinY() - 8) && now >= this.rescueAfter) {
            this.rescue();
        }
        if (this.mob.tickCount % 20 == 0) {
            for (MobEffectInstance effect : new ArrayList<>(this.mob.getActiveEffects())) {
                if (!this.effect(effect, false)) continue;
                this.mob.removeEffect(effect.getEffect());
            }
            if (this.mob.onGround() && this.safe(this.mob.blockPosition())) {
                this.safePosition = this.mob.blockPosition().immutable();
                this.safeDimension = this.mob.level().dimension().identifier().toString();
            }
            this.updateAttributes();
            if (this.has(Adaptation.Evolution.VITALITY) && (double)this.mob.getHealth() <= (double)this.mob.getMaxHealth() * 0.3 && now >= this.state.protectionReady) {
                this.state.protectionReady = now + 1200L;
                this.state.protectedUntil = now + 60L;
                this.mob.heal(8.0f);
                this.mob.adaptationMessage((Component)Component.literal((String)"\u4e0d\u5c48\u4e4b\u8eaf \u00b7 \u7d27\u6025\u6062\u590d\u4e0e 3 \u79d2\u4fdd\u62a4"));
            }
            if (this.state.healing() > 0.0f) {
                this.mob.heal(this.state.healing() * (this.has(Adaptation.Evolution.REGEN) && this.mob.getHealth() < this.mob.getMaxHealth() / 2.0f ? 1.5f : 1.0f));
            }
        }
    }

    private void environment() {
        Vec3 position = this.mob.position();
        BlockPos groundPos = BlockPos.containing((double)this.mob.getX(), (double)(this.mob.getY() - 0.2), (double)this.mob.getZ());
        BlockState ground = this.mob.level().getBlockState(groundPos);
        if ((ground.is(Blocks.SOUL_SAND) || ground.is(Blocks.SOUL_SOIL)) && this.mob.onGround()) {
            Adaptation.Entry entry = this.expose(SOUL_GROUND, true, Adaptation.Evolution.SOUL, false);
            if (this.previousPosition != null && position.subtract(this.previousPosition).horizontalDistanceSqr() > 1.0E-8 && !this.mob.isPassenger()) {
                ++this.soulWalkTicks;
            }
        }
        this.previousPosition = position;
        if (this.mob.isInLava()) {
            this.environmentExposure(this.mob.damageSources().lava());
        }
        if (this.mob.getDeltaMovement().y <= 0.0) {
            BlockPos below = BlockPos.containing((double)this.mob.getX(), (double)(this.mob.getY() - 0.05), (double)this.mob.getZ());
            BlockState block = this.mob.level().getBlockState(below);
            FluidState fluid = block.getFluidState();
            boolean support = this.has(Adaptation.Evolution.WATER) && fluid.is(FluidTags.WATER) || this.has(Adaptation.Evolution.LAVA) && fluid.is(FluidTags.LAVA);
            double surface = (float)below.getY() + (support ? fluid.getHeight((BlockGetter)this.mob.level(), below) : 1.0f);
            if ((support || this.has(Adaptation.Evolution.FROST) && block.is(Blocks.POWDER_SNOW)) && this.mob.getY() >= surface - 0.15) {
                this.mob.setPos(this.mob.getX(), surface, this.mob.getZ());
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().x, 0.0, this.mob.getDeltaMovement().z);
                this.mob.setOnGround(true);
                this.mob.fallDistance = 0.0f;
            }
        }
        if (this.mob.isInPowderSnow && this.has(Adaptation.Evolution.FROST)) {
            this.mob.push(0.0, 0.12, 0.0);
        }
    }

    private void deflectProjectiles() {
        Object object;
        if (!this.has(Adaptation.Evolution.PROJECTILE) || !((object = this.mob.level()) instanceof ServerLevel)) {
            return;
        }
        ServerLevel world = (ServerLevel)object;
        for (Projectile shot : world.getEntitiesOfClass(Projectile.class, this.mob.getBoundingBox().inflate(2.2), e -> e.getOwner() != this.mob && !e.isRemoved())) {
            String id = shot instanceof ThrownTrident ? "trident" : (shot instanceof AbstractArrow ? "arrow" : (shot instanceof WitherSkull ? "wither_skull" : (shot instanceof LargeFireball ? "fireball" : (shot instanceof ThrowableProjectile ? "thrown" : "mob_projectile"))));
            if (!this.immune("minecraft:" + id, false)) continue;
            Vec3 normal = shot.position().subtract(this.mob.position().add(0.0, (double)this.mob.getBbHeight() * 0.5, 0.0));
            if (normal.lengthSqr() < 0.001) {
                normal = this.mob.getViewVector(1.0f);
            }
            normal = normal.normalize();
            double inward = shot.getDeltaMovement().dot(normal);
            if (inward >= 0.0) continue;
            shot.setDeltaMovement(shot.getDeltaMovement().subtract(normal.scale(2.0 * inward)));
            shot.setOwner((Entity)this.mob);
            shot.needsSync = true;
        }
    }

    private static boolean hazard(BlockState block) {
        return block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE) || block.is(Blocks.CACTUS) || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE) || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.WITHER_ROSE);
    }

    private boolean safe(BlockPos position) {
        Level world = this.mob.level();
        AABB box = this.mob.getDimensions(this.mob.getPose()).makeBoundingBox(Vec3.atBottomCenterOf((Vec3i)position));
        if (position.getY() <= world.getMinY() || box.maxY >= (double)world.getMaxY() || !world.getWorldBorder().isWithinBounds(box)) {
            return false;
        }
        for (BlockPos at : BlockPos.betweenClosed((BlockPos)BlockPos.containing((double)box.minX, (double)(box.minY - 1.0), (double)box.minZ), (BlockPos)BlockPos.containing((double)box.maxX, (double)box.maxY, (double)box.maxZ))) {
            if (!world.hasChunkAt(at)) {
                return false;
            }
            BlockState block = world.getBlockState(at);
            if (!MahoragaAdaptation.hazard(block) && block.getFluidState().isEmpty()) continue;
            return false;
        }
        BlockState floor = world.getBlockState(position.below());
        return floor.isFaceSturdy((BlockGetter)world, position.below(), Direction.UP) && world.noCollision((Entity)this.mob, box);
    }

    private void rescue() {
        this.rescueAfter = this.mob.level().getGameTime() + 20L;
        BlockPos target = this.safePosition != null && this.safeDimension.equals(this.mob.level().dimension().identifier().toString()) && this.safe(this.safePosition) ? this.safePosition : null;
        BlockPos origin = this.mob.adaptationReturnOrigin();
        if (target == null) {
            block0: for (int radius = 0; radius <= 8; ++radius) {
                for (int dx = -radius; dx <= radius; ++dx) {
                    for (int dz = -radius; dz <= radius; ++dz) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        for (int dy = -3; dy <= 3; ++dy) {
                            BlockPos candidate = origin.offset(dx, dy, dz);
                            if (!this.safe(candidate)) continue;
                            target = candidate;
                            break block0;
                        }
                    }
                }
            }
        }
        if (target != null) {
            this.mob.getNavigation().stop();
            this.mob.snapTo(Vec3.atBottomCenterOf((Vec3i)target));
            this.mob.setDeltaMovement(Vec3.ZERO);
            this.mob.fallDistance = 0.0f;
            this.mob.clearFire();
            this.mob.adaptationMessage((Component)Component.literal((String)"\u865a\u7a7a\u5f52\u8fd8 \u00b7 \u5df2\u8fd4\u56de\u5b89\u5168\u843d\u70b9"));
        } else {
            this.mob.setDeltaMovement(0.0, 0.3, 0.0);
            this.mob.fallDistance = 0.0f;
        }
    }

    public void write(CompoundTag root) {
        CompoundTag nbt = new CompoundTag();
        ListTag entries = new ListTag();
        for (Adaptation.Entry entry : this.state.entries.values()) {
            CompoundTag value = new CompoundTag();
            value.putString("Id", entry.id);
            value.putBoolean("Effect", entry.effect);
            value.putString("Evolution", entry.evolution.name());
            value.putInt("Ticks", entry.ticks);
            entries.add(value);
        }
        nbt.put("Entries", (Tag)entries);
        nbt.putInt("AdaptScale", Adaptation.STAGE_TICKS);
        nbt.putLong("ProtectionReady", this.state.protectionReady);
        nbt.putLong("ProtectedUntil", this.state.protectedUntil);
        nbt.putInt("SoulWalkTicks", this.soulWalkTicks % 20);
        if (this.safePosition != null) {
            nbt.putLong("SafePosition", this.safePosition.asLong());
            nbt.putString("SafeDimension", this.safeDimension);
        }
        root.put("MahoragaAdaptation", (Tag)nbt);
    }

    public void read(CompoundTag root) {
        this.state.entries.clear();
        this.hits.clear();
        this.previousPosition = null;
        this.safePosition = null;
        CompoundTag nbt = root.getCompoundOrEmpty("MahoragaAdaptation");
        int scale = nbt.getIntOr("AdaptScale", Adaptation.LEGACY_STAGE_TICKS);
        for (Tag element : nbt.getListOrEmpty("Entries")) {
            Adaptation.Evolution evolution;
            CompoundTag value = (CompoundTag)element;
            String id = value.getStringOr("Id", "");
            if (Identifier.tryParse((String)id) == null) continue;
            try {
                evolution = Adaptation.Evolution.valueOf(value.getStringOr("Evolution", ""));
            }
            catch (IllegalArgumentException ignored) {
                evolution = Adaptation.Evolution.NONE;
            }
            Adaptation.Entry entry = this.state.start(id, value.getBooleanOr("Effect", false), evolution);
            long stored = value.getIntOr("Ticks", 0);
            if (scale != Adaptation.STAGE_TICKS) stored = stored * Adaptation.STAGE_TICKS / Math.max(1, scale);
            entry.ticks = (int)Math.max(0L, Math.min((long)entry.maximum(), stored));
            this.hits.put((entry.effect ? "effect:" : "damage:") + id, this.mob.level().getGameTime());
        }
        this.state.protectionReady = nbt.getLongOr("ProtectionReady", 0L);
        this.state.protectedUntil = nbt.getLongOr("ProtectedUntil", 0L);
        this.soulWalkTicks = Math.max(0, nbt.getIntOr("SoulWalkTicks", 0)) % 20;
        if (nbt.contains("SafePosition")) {
            this.safePosition = BlockPos.of((long)nbt.getLongOr("SafePosition", 0L));
            this.safeDimension = nbt.getStringOr("SafeDimension", "");
        }
        this.updateAttributes();
    }
}

