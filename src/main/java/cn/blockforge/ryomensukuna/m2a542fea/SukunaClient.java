package cn.blockforge.ryomensukuna.m2a542fea;

import cn.blockforge.ryomensukuna.m2a542fea.client.CastVisuals;
import cn.blockforge.ryomensukuna.m2a542fea.client.ChargeInput;
import cn.blockforge.ryomensukuna.m2a542fea.client.DomainPostFx;
import cn.blockforge.ryomensukuna.m2a542fea.client.SukunaClientState;
import cn.blockforge.ryomensukuna.m2a542fea.client.SukunaHud;
import cn.blockforge.ryomensukuna.m2a542fea.client.SukunaKeys;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.DomainRenderer;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SlashShader;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.SukunaRenderers;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import net.fabricmc.api.ClientModInitializer;

public final class SukunaClient
implements ClientModInitializer {
    public void onInitializeClient() {
        SlashShader.init();
        SukunaRenderers.register();
        SukunaKeys.init();
        cn.blockforge.ryomensukuna.m2a542fea.client.SukunaClientNetworking.registerClientReceivers();
        SukunaClientState.init();
        SukunaHud.init();
        ChargeInput.init();
        DomainPostFx.init();
        CastVisuals.init();
        cn.blockforge.ryomensukuna.m2a542fea.client.SixEyesClient.init();
        DomainRenderer.init();
    }
}

