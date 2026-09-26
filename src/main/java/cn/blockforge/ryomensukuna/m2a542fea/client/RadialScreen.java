package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.Unpooled;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Technique wheel: opened while the wheel key is held, the hovered sector is chosen on release.
 * Clicking a sector also chooses; right click or Esc closes. Locked techniques show their stage.
 */
public class RadialScreen
extends Screen {
    private final Skill[] wheel;
    private final double step;
    private int hover = -1;
    private boolean released;

    public RadialScreen() {
        super(Component.translatable("sukuna.wheel.title"));
        this.wheel = SukunaClientState.wheel();
        this.step = Math.PI * 2 / (double)this.wheel.length;
    }

    public boolean isPauseScreen() {
        return false;
    }

    private double cx() {
        return (double)this.width / 2.0;
    }

    private double cy() {
        return (double)this.height / 2.0;
    }

    private double radius() {
        return Math.min(110.0, (double)Math.min(this.width, this.height) * 0.42);
    }

    private int sector(double x, double y) {
        double dx = x - this.cx();
        double dy = y - this.cy();
        double distance = Math.hypot(dx, dy);
        if (distance < this.radius() * 0.25 || distance > this.radius() * 1.4) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + this.step / 2.0;
        return (int)Math.floor((angle + Math.PI * 2) % (Math.PI * 2) / this.step);
    }

    private boolean gojo() {
        return SukunaClientState.route == StageRules.GOJO;
    }

    @Override
    public void tick() {
        super.tick();
        // Polling fallback: some platforms do not deliver the release while the screen has focus.
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(SukunaKeys.WHEEL);
        if (key.getType() == InputConstants.Type.KEYBOARD && key.getValue() > 0 && !InputConstants.isKeyDown(key.getValue())) {
            this.confirm();
        }
    }

    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x70050510);
        this.hover = this.sector(mouseX, mouseY);
        double r = this.radius();
        int accent = this.gojo() ? 0xFF7FD8FF : 0xFFFF4A5A;
        for (int i = 0; i < this.wheel.length; ++i) {
            Skill skill = this.wheel[i];
            boolean unlocked = SukunaClientState.unlocked(skill.netId);
            boolean selected = SukunaClientState.selected == skill.netId && unlocked;
            int color = i == this.hover ? (unlocked ? 0xE0708CFF : 0xE0505050) : (selected ? 0xE03A2A55 : 0xDD151522);
            if (!this.gojo() && i == this.hover && unlocked) color = 0xE0A0303F;
            this.arc(ctx, i, r * 0.43, r, color);
            this.arc(ctx, i, r - 2.0, r, selected ? 0xFFFFE0A0 : (i == this.hover ? accent : 0xFF4A4A60));
            double angle = -Math.PI / 2 + (double)i * this.step;
            int x = (int)(this.cx() + Math.cos(angle) * r * 0.73);
            int y = (int)(this.cy() + Math.sin(angle) * r * 0.73);
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SkillIcons.id(skill), x - 10, y - 16, 0.0f, 0.0f, 20, 20, 16, 16, 16, 16);
            if (!unlocked) {
                ctx.fill(x - 10, y - 16, x + 10, y + 4, 0xA0101016);
            }
            ctx.centeredText(this.font, Component.translatable(skill.translationKey() + ".short"), x, y + 7, unlocked ? 0xFFFFE6D8 : 0xFF8D8D8D);
            if (!unlocked) {
                ctx.centeredText(this.font, Component.translatable("sukuna.wheel.need_stage", StageRules.roman(skill.unlockStage)), x, y + 18, 0xFF9D8D90);
            }
        }
        Skill current = Skill.byId(SukunaClientState.selected);
        Skill shown = this.hover >= 0 ? this.wheel[this.hover] : current;
        int cx = (int)this.cx();
        int cy = (int)this.cy();
        if (shown != null) {
            ctx.centeredText(this.font, Component.translatable(shown.translationKey() + ".short"), cx, cy - 16, 0xFFFFE0CF);
            String cost = SukunaClientState.infinite() ? "∞" : String.valueOf((int)(shown.baseCost * (this.gojo() ? 0.8f : 1.0f)));
            ctx.centeredText(this.font, Component.translatable("sukuna.wheel.cost", cost), cx, cy - 4, 0xFFEA6E87 & (this.gojo() ? 0xFF7FD8FF : 0xFFFFFFFF));
            ctx.centeredText(this.font, Component.translatable("sukuna.wheel.cooldown", String.format(Locale.ROOT, "%.1f", shown.cooldownSeconds)), cx, cy + 8, 0xFFB8B8C8);
            int boxW = Math.min(300, this.width - 20);
            int top = (int)(cy + r + 14);
            if (top + 40 > this.height) top = 6;
            var lines = this.font.split(Component.translatable(shown.translationKey() + ".desc"), boxW - 12);
            int boxH = 10 + lines.size() * 10 + (SukunaClientState.unlocked(shown.netId) ? 0 : 10);
            ctx.fill(cx - boxW / 2, top, cx + boxW / 2, top + boxH, 0xC0101018);
            int ly = top + 5;
            for (var line : lines) {
                ctx.text(this.font, line, cx - boxW / 2 + 6, ly, 0xFFDDDDE6);
                ly += 10;
            }
            if (!SukunaClientState.unlocked(shown.netId)) {
                ctx.text(this.font, Component.translatable("sukuna.wheel.unlock_hint", StageRules.roman(shown.unlockStage)), cx - boxW / 2 + 6, ly, 0xFFFF9090);
            }
        }
        ctx.centeredText(this.font, Component.translatable("sukuna.wheel.hint"), cx, 8, 0xFF9090A0);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void arc(GuiGraphicsExtractor ctx, int sector, double inner, double outer, int color) {
        java.util.List<org.joml.Vector2f> points = new java.util.ArrayList<>();
        double start = -Math.PI / 2 + sector * this.step - this.step / 2 + 0.018;
        double span = this.step - 0.036;
        for (int i = 0; i < 18; i++) {
            double a = start + span * i / 18, b = start + span * (i + 1) / 18;
            for (double[] point : new double[][]{{a, inner}, {b, inner}, {b, outer}, {a, outer}})
                points.add(new org.joml.Vector2f((float)(cx() + Math.cos(point[0]) * point[1]), (float)(cy() + Math.sin(point[0]) * point[1])).mulPosition(ctx.pose()));
        }
        ctx.guiRenderState.addGuiElement(new ArcElement(java.util.List.copyOf(points), color,
            new net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, width, height)));
    }

    private record ArcElement(java.util.List<org.joml.Vector2f> points, int color, net.minecraft.client.gui.navigation.ScreenRectangle bounds)
        implements net.minecraft.client.renderer.state.gui.GuiElementRenderState {
        public void buildVertices(VertexConsumer buffer) { for (var p : points) buffer.addVertex(p.x, p.y, 0).setColor(color); }
        public com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline() { return net.minecraft.client.renderer.RenderPipelines.GUI; }
        public net.minecraft.client.gui.render.TextureSetup textureSetup() { return net.minecraft.client.gui.render.TextureSetup.noTexture(); }
        public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() { return null; }
    }

    private void choose(int index) {
        if (index < 0 || index >= this.wheel.length) return;
        Skill skill = this.wheel[index];
        if (!SukunaClientState.unlocked(skill.netId)) {
            SukunaClientState.onFeedback(skill.netId, SukunaNet.FAIL_LOCKED);
            return;
        }
        ChargeInput.select(skill);
    }

    private void confirm() {
        if (this.released) return;
        this.released = true;
        this.choose(this.hover);
        this.onClose();
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (SukunaKeys.WHEEL.matches(event)) {
            this.confirm();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (SukunaKeys.WHEEL.matchesMouse(event)) {
            this.confirm();
            return true;
        }
        return super.mouseReleased(event);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.released = true;
            this.onClose();
            return true;
        }
        int index = this.sector(event.x(), event.y());
        if (button == InputConstants.MOUSE_BUTTON_LEFT && index >= 0) {
            this.released = true;
            this.choose(index);
            this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
