package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.CurseSlashRenderer;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.MahoragaRenderer;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.RedBlastRenderer;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.ShrineRenderer;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashFxRenderer;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.WorldCutFxRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class SukunaRenderers {
    private SukunaRenderers() {
    }

    public static void register() {
        EntityRendererRegistry.register(SukunaMod.CURSE_SLASH, CurseSlashRenderer::new);
        EntityRendererRegistry.register(SukunaMod.SLASH_FX, SlashFxRenderer::new);
        EntityRendererRegistry.register(SukunaMod.RED_BLAST, RedBlastRenderer::new);
        EntityRendererRegistry.register(SukunaMod.WORLD_CUT_FX, WorldCutFxRenderer::new);
        EntityRendererRegistry.register(SukunaMod.SHRINE, ShrineRenderer::new);
        EntityRendererRegistry.register(SukunaMod.MAHORAGA, MahoragaRenderer::new);
        EntityRendererRegistry.register(SukunaMod.AO, GojoRenderers.Ao::new);
        EntityRendererRegistry.register(SukunaMod.AKA, GojoRenderers.Aka::new);
        EntityRendererRegistry.register(SukunaMod.MURASAKI, GojoRenderers.Murasaki::new);
        EntityRendererRegistry.register(SukunaMod.VOID_DOMAIN, GojoRenderers.VoidShell::new);
        EntityRendererRegistry.register(SukunaMod.GOJO_NPC, ctx -> new JjkNpcRenderer<>(ctx, SukunaMod.id("textures/entity/gojo.png")));
        EntityRendererRegistry.register(SukunaMod.SUKUNA_NPC, ctx -> new JjkNpcRenderer<>(ctx, SukunaMod.id("textures/entity/sukuna.png")));
        EntityRendererRegistry.register(SukunaMod.TRAINING_DUMMY, net.minecraft.client.renderer.entity.ArmorStandRenderer::new);
    }
}

