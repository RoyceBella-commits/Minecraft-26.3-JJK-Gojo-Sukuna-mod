package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Status panel (J toggles it), charge bar, mobility cooldowns, Six Eyes warnings and growth gold hearts. */
public final class SukunaHud {
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier GOLD_FULL = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier GOLD_HALF = Identifier.withDefaultNamespace("hud/heart/absorbing_half");
    private static final Identifier GOLD_FULL_BLINK = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final Identifier GOLD_HALF_BLINK = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");
    private static HudPreferences preferences;

    private SukunaHud() {
    }

    public static void init() {
        preferences = new HudPreferences(net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("sukuna-client.json"));
        HudElementRegistry.addLast(SukunaMod.id("hud"), (ctx, tracker) -> render(ctx, tracker.getGameTimeDeltaPartialTick(false)));
        HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, SukunaMod.id("growth_gold"), (ctx, tracker) -> renderGold(ctx));
    }

    public static HudPreferences preferences() {
        return preferences;
    }

    public static void togglePanel() { preferences.toggle(); }

    /** Growth gold hearts above the armor row; independent of the technique panel toggle. */
    private static void renderGold(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.hud.isHidden() || !SukunaClientState.awakened() || SukunaClientState.goldMax <= 0.0f
            || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        int xLeft = ctx.guiWidth() / 2 - 91;
        int yLineBase = ctx.guiHeight() - 39;
        float maxHealth = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int healthRows = Mth.ceil((maxHealth + absorption) / 2.0f / 10.0f);
        int healthRowHeight = Math.max(10 - (healthRows - 2), 3);
        int top = yLineBase - (healthRows - 1) * healthRowHeight - 10;
        if (player.getArmorValue() > 0) top -= 10;
        int hearts = Mth.ceil(SukunaClientState.goldMax / 2.0f);
        int rows = Mth.ceil(hearts / 10.0f);
        int rowStep = rows <= 1 ? 10 : Math.max(4, 10 - (rows - 1) * 3);
        int gold = Mth.ceil(SukunaClientState.gold);
        long since = (System.nanoTime() - SukunaClientState.goldChangedAt) / 1_000_000L;
        boolean blink = since < 400L && (since / 100L) % 2L == 0L;
        for (int i = hearts - 1; i >= 0; --i) {
            int row = i / 10;
            int x = xLeft + (i % 10) * 8;
            int y = top - row * rowStep;
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, x, y, 9, 9);
            if (i * 2 + 1 < gold) {
                ctx.blitSprite(RenderPipelines.GUI_TEXTURED, blink ? GOLD_FULL_BLINK : GOLD_FULL, x, y, 9, 9);
            } else if (i * 2 + 1 == gold) {
                ctx.blitSprite(RenderPipelines.GUI_TEXTURED, blink ? GOLD_HALF_BLINK : GOLD_HALF, x, y, 9, 9);
            }
        }
        String label = gold + "/" + (int)SukunaClientState.goldMax;
        ctx.text(mc.font, label, xLeft + 83, top - (rows - 1) * rowStep + 1, 0xFFFFD34A);
    }

    private static void render(GuiGraphicsExtractor ctx, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null || mc.level == null || mc.gui.screen() != null
            || !SukunaClientState.awakened()) {
            return;
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        renderDanger(ctx, mc, sw, sh);
        if (ChargeInput.isCharging()) {
            Skill charging = Skill.byId(ChargeInput.chargingSkill());
            if (charging != null) {
                SukunaHud.renderCharge(ctx, mc, charging, sw, sh);
            }
        }
        if (!preferences.visible() || (ChargeInput.isCharging() && sh < 220)) return;
        boolean gojo = SukunaClientState.route == StageRules.GOJO;
        int accent = gojo ? 0xFF7FD8FF : 0xFFE0303F;
        int width = Math.min(186, sw - 12);
        int height = 114;
        int x = sw - width - 6;
        int y = ChargeInput.isCharging() && sh < 320 ? 6 : Math.max(6, sh - (sw < 560 ? 150 : 100) - height + 66);
        ctx.fill(x, y, x + width, y + height, 0xDE0E0B15);
        ctx.fill(x, y, x + width, y + 1, accent);
        ctx.fill(x, y + 1, x + 2, y + height - 1, accent & 0x90FFFFFF);
        Component routeLine = Component.translatable(gojo ? "sukuna.route.gojo" : "sukuna.route.sukuna")
            .append(" · ").append(Component.translatable("sukuna.stage.name." + SukunaClientState.stage, StageRules.roman(SukunaClientState.stage)));
        ctx.text(mc.font, mc.font.plainSubstrByWidth(routeLine.getString(), width - 12), x + 8, y + 5, accent);
        Skill skill = Skill.byId(ChargeInput.isCharging() ? ChargeInput.chargingSkill() : SukunaClientState.selected);
        boolean ready = skill != null && SukunaClientState.unlocked(skill.netId);
        if (ready) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, SkillIcons.id(skill), x + 7, y + 17, 0.0f, 0.0f, 20, 20, 16, 16, 16, 16);
        }
        MutableComponent name = ready ? Component.translatable(skill.translationKey()) : Component.translatable("sukuna.hud.dormant");
        ctx.text(mc.font, mc.font.plainSubstrByWidth(name.getString(), width - 40), x + 32, y + 18, 0xFFFFE3D6);
        float cd = SukunaClientState.cooldown();
        MutableComponent status = cd > 0.05f
            ? Component.translatable("sukuna.hud.cooldown", String.format(Locale.ROOT, "%.1f", cd))
            : Component.translatable(ready ? "sukuna.hud.ready" : "sukuna.hud.dormant");
        ctx.text(mc.font, status, x + 32, y + 28, cd > 0.05f ? 0xFFE8A0A0 : 0xFFA0E8A8);
        String energy = SukunaClientState.infinite() ? "∞" : (int)SukunaClientState.energy + " / " + (int)SukunaClientState.maxEnergy;
        int energyWidth = mc.font.width(energy);
        ctx.text(mc.font, Component.translatable("sukuna.hud.energy"), x + 8, y + 40, 0xFFB8B8C8);
        ctx.text(mc.font, energy, x + width - 8 - energyWidth, y + 40, SukunaClientState.infinite() ? 0xFFFFE070 : 0xFFE9D5FF);
        float ratio = SukunaClientState.infinite() ? 1.0f : SukunaClientState.energy / Math.max(1.0f, SukunaClientState.maxEnergy);
        int bar = width - 16;
        ctx.fill(x + 8, y + 51, x + 8 + bar, y + 54, 0xFF3F2A40);
        ctx.fill(x + 8, y + 51, x + 8 + (int)(bar * Mth.clamp(ratio, 0.0f, 1.0f)), y + 54, gojo ? 0xFF5FB8FF : 0xFFC5324A);
        MutableComponent special = Component.empty();
        float burnout = SukunaClientState.burnout();
        if (gojo) {
            special.append(Component.translatable(SukunaClientState.infinityOn ? (burnout > 0.0f ? "sukuna.hud.infinity_sealed" : "sukuna.hud.infinity_on") : "sukuna.hud.infinity_off"));
        }
        if (burnout > 0.0f) {
            if (gojo) special.append("  ");
            special.append(Component.translatable("sukuna.hud.burnout", String.format(Locale.ROOT, "%.1f", burnout)));
        }
        ctx.text(mc.font, mc.font.plainSubstrByWidth(special.getString(), width - 16), x + 8, y + 58, burnout > 0.0f ? 0xFFFF8A5A : 0xFF9FE8FF);
        int stage = SukunaClientState.stage;
        Component stats = Component.translatable("sukuna.hud.stats", (int)(SukunaClientState.goldMax / 2.0f), (int)StageRules.armor(stage),
            (int)StageRules.unarmedDamage(stage), Math.round(StageRules.blackFlashChance(stage) * 100.0f));
        ctx.text(mc.font, mc.font.plainSubstrByWidth(stats.getString(), width - 16), x + 8, y + 69, 0xFFE8C8C8);
        Component taken = Component.translatable("sukuna.hud.taken", percent(StageRules.takenFraction(stage, false)), percent(StageRules.takenFraction(stage, true)));
        ctx.text(mc.font, mc.font.plainSubstrByWidth(taken.getString(), width - 16), x + 8, y + 80, 0xFFB8D8E8);
        ctx.text(mc.font, mc.font.plainSubstrByWidth(SukunaClientState.practice().getString(), width - 16), x + 8, y + 91, 0xFFC8B89A);
        mobilityIcon(ctx, mc, x + 8, y + 102, "C", SukunaClientState.dashCooldown(), SukunaClientState.stage >= 1, accent);
        mobilityIcon(ctx, mc, x + 60, y + 102, "Z", SukunaClientState.leapCooldown(), SukunaClientState.stage >= 2, accent);
    }

    private static String percent(float fraction) {
        return Math.round(fraction * 100.0f) + "%";
    }

    private static void mobilityIcon(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, String key, float cd, boolean unlocked, int accent) {
        ctx.fill(x, y, x + 10, y + 9, unlocked ? (cd > 0.05f ? 0xFF3A3040 : accent) : 0xFF303030);
        ctx.text(mc.font, key, x + 2, y + 1, 0xFF101010, false);
        String label = !unlocked ? "—" : cd > 0.05f ? String.format(Locale.ROOT, "%.1fs", cd) : "OK";
        ctx.text(mc.font, label, x + 13, y + 1, cd > 0.05f ? 0xFFB0B0B0 : 0xFFE0FFE0);
    }

    /** Six Eyes: direction and name of a dangerous wind-up nearby. */
    private static void renderDanger(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh) {
        if (SukunaClientState.dangerSkill == null || SukunaClientState.dangerFrom == null) return;
        long age = (System.nanoTime() - SukunaClientState.dangerAt) / 1_000_000L;
        if (age > 3000L) return;
        Vec3 to = SukunaClientState.dangerFrom.subtract(mc.player.position());
        double yaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        double rel = Mth.wrapDegrees(yaw - mc.player.getYRot());
        String arrow = Math.abs(rel) < 30 ? "▲" : Math.abs(rel) > 150 ? "▼" : rel > 0 ? "▶" : "◀";
        Component text = Component.translatable("sukuna.hud.danger", Component.translatable(SukunaClientState.dangerSkill.translationKey()), arrow);
        int color = (age / 250L) % 2L == 0L ? 0xFFFF5060 : 0xFFFFB0B0;
        ctx.centeredText(mc.font, text, sw / 2, sh / 2 - 40, color);
    }

    /** Whether this player's own Ao is in the world (the cast key then drags it). */
    public static boolean ownAoExists(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        String me = mc.player.getStringUUID();
        return !mc.level.getEntitiesOfClass(cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity.class,
            mc.player.getBoundingBox().inflate(64.0), e -> me.equals(e.ownerString())).isEmpty();
    }

    private static void renderCharge(GuiGraphicsExtractor ctx, Minecraft mc, Skill skill, int sw, int sh) {
        int width = Math.min(192, sw - 24);
        int x = (sw - width) / 2;
        int y = sh < 220 ? 12 : Math.min(sh / 2 + 22, sh - 104);
        if (skill.isHeal()) {
            ctx.fill(x - 5, y - 4, x + width + 5, y + 29, 0xDC101418);
            ctx.centeredText(mc.font, Component.translatable("sukuna.hud.healing"), sw / 2, y, 0xFFA0FFB0);
            float health = mc.player.getHealth() / mc.player.getMaxHealth();
            float gold = SukunaClientState.goldMax <= 0 ? 1.0f : SukunaClientState.gold / SukunaClientState.goldMax;
            ctx.fill(x, y + 13, x + width, y + 17, 0xFF1A3A20);
            ctx.fill(x, y + 13, x + (int)(width * Mth.clamp(health, 0.0f, 1.0f)), y + 17, 0xFFE04050);
            ctx.fill(x, y + 19, x + width, y + 23, 0xFF3A3010);
            ctx.fill(x, y + 19, x + (int)(width * Mth.clamp(gold, 0.0f, 1.0f)), y + 23, 0xFFFFD34A);
            return;
        }
        if (skill == Skill.AO && ownAoExists(mc)) {
            ctx.fill(x - 5, y - 4, x + width + 5, y + 22, 0xDC101418);
            ctx.centeredText(mc.font, Component.translatable("sukuna.hud.ao_drag"), sw / 2, y, 0xFF9FD0FF);
            ctx.centeredText(mc.font, Component.translatable("sukuna.hud.ao_drag_hint"), sw / 2, y + 11, 0xFFB8C8E0);
            return;
        }
        float seconds = ChargeInput.currentCharge();
        float ratio = seconds / Skill.MAX_CHARGE_SECONDS;
        boolean full = ratio >= 0.999f;
        float need = skill == Skill.MURASAKI ? Skill.MURASAKI_MIN_CHARGE : skill == Skill.VOID ? Skill.VOID_MIN_CHARGE : 0.0f;
        boolean enough = seconds >= need;
        int color = full ? 0xFFFFFFFF : enough ? 0xFFFFC9DB : 0xFF9A8AA0;
        ctx.fill(x - 5, y - 4, x + width + 5, y + 35, 0xDC101418);
        ctx.text(mc.font, Component.translatable(skill.translationKey() + ".short"), x, y, color);
        String progress = String.format(Locale.ROOT, "%.1f / %.1f s  %d%%", seconds, Skill.MAX_CHARGE_SECONDS, (int)(ratio * 100.0f));
        ctx.text(mc.font, progress, x + width - mc.font.width(progress), y, color);
        ctx.fill(x - 1, y + 12, x + width + 1, y + 22, 0xFF83526A);
        ctx.fill(x, y + 13, x + width, y + 21, 0xFF29192C);
        ctx.fill(x, y + 13, x + Math.max(2, (int)((float)width * ratio)), y + 21, color);
        if (need > 0.0f) {
            int mark = x + (int)(width * need / Skill.MAX_CHARGE_SECONDS);
            ctx.fill(mark, y + 11, mark + 1, y + 23, 0xFFFFE070);
        }
        for (int i = 1; i < 5; ++i) {
            ctx.fill(x + width * i / 5, y + 13, x + width * i / 5 + 1, y + 21, 0x80503C58);
        }
        MutableComponent state = !enough
            ? Component.translatable(skill == Skill.VOID ? "sukuna.hud.sealing" : "sukuna.hud.assembling")
            : Component.translatable(full ? "sukuna.hud.charged" : "sukuna.hud.charging", String.format(Locale.ROOT, "%.1f", seconds));
        ctx.centeredText(mc.font, state, sw / 2, y + 25, color);
    }
}
