package cn.blockforge.ryomensukuna.gametest;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.SukunaNpcEntity;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.GojoSkills;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.domain.DomainSkill;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dev-only screenshots of both domains and their clash from fixed camera positions, for comparing
 * visual changes. Run with {@code ./gradlew runClientGameTest}; images land in the run directory.
 */
public final class DomainShots implements FabricClientGameTest {
    private int ground;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            // Keep chunks and entities loaded far enough to view a 64-block domain from outside.
            server.runOnServer(s -> {
                s.getPlayerList().setViewDistance(16);
                s.getPlayerList().setSimulationDistance(12);
            });
            this.ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
            context.getInput().pressKey(options -> options.keyToggleGui);
            // The test client defaults to 5 chunks; a 64-block domain seen from outside needs more.
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(16);
                mc.options.broadcastOptions();
            });
            this.fly(server);
            // A stage V Gojo has unlimited cursed energy, so the Void is not cut short.
            server.runOnServer(s -> Progression.forceAdvance(player(s), StageRules.GOJO, StageRules.MAX_STAGE));

            String only = System.getProperty("sukuna.only", "");
            if (only.isEmpty() || only.equals("void")) this.voidShots(context, server);
            if (only.isEmpty() || only.equals("shrine")) this.shrineShots(context, server);
            if (only.isEmpty() || only.equals("clash")) this.clashShots(context, server);
            if (only.isEmpty() || only.equals("wheel")) this.wheelShots(context, server);
        }
    }

    private void voidShots(ClientGameTestContext context, TestServerContext server) {
            // Unlimited Void, cast by the player at the origin facing south (+z).
            this.tp(server, 0, 1, 0, 0, 0);
            context.waitTicks(40);
            context.takeScreenshot("void_0_before");
            server.runOnServer(s -> GojoSkills.expandVoid(player(s), -1, 4000));
            context.waitTicks(14);
            context.takeScreenshot("void_1_opening");
            context.waitTicks(40);
            context.takeScreenshot("void_2_inside_front");
            this.tp(server, 0, 1, 0, 180, -15);
            context.waitTicks(6);
            context.takeScreenshot("void_3_inside_blackhole");
            this.tp(server, 0, 1, 0, 90, -65);
            context.waitTicks(6);
            context.takeScreenshot("void_4_inside_up");
            this.tp(server, 0, 30, -110, 0, 10);
            context.waitTicks(100);
            context.takeScreenshot("void_5_outside");
            server.runOnServer(DomainShots::clearDomains);
    }

    private void shrineShots(ClientGameTestContext context, TestServerContext server) {
            // Malevolent Shrine, cast by a still Sukuna NPC at the origin facing south; the camera stands beside it.
            this.tp(server, 2, 1, 0, 0, 0);
            context.waitTicks(10);
            server.runOnServer(s -> DomainSkill.castFor(this.sukuna(s, 0.5, 0.0f), 4000, -1));
            context.waitTicks(60);
            context.takeScreenshot("shrine_1_assembling");
            context.waitTicks(90);
            context.takeScreenshot("shrine_2_inside_front");
            this.tp(server, 2, 1, 0, 180, -5);
            context.waitTicks(6);
            context.takeScreenshot("shrine_3_inside_shrine");
            this.tp(server, 2, 1, 0, 0, 35);
            context.waitTicks(6);
            context.takeScreenshot("shrine_4_mirror");
            this.tp(server, 0, 30, -110, 0, 10);
            context.waitTicks(100);
            context.takeScreenshot("shrine_5_outside");
            server.runOnServer(DomainShots::clearDomains);
    }

    private void clashShots(ClientGameTestContext context, TestServerContext server) {
            // Clash: the player's Void at the origin, a still Sukuna NPC's shrine 40 blocks east.
            this.tp(server, 0, 1, 0, 0, 0);
            context.waitTicks(10);
            server.runOnServer(s -> {
                GojoSkills.expandVoid(player(s), -1, 4000);
                DomainSkill.castFor(this.sukuna(s, 40.5, 90.0f), 4000, -1);
            });
            context.waitTicks(160);
            this.tp(server, 20, 6, -40, 0, 0);
            context.waitTicks(10);
            context.takeScreenshot("clash_1_seam_from_south");
            this.tp(server, 8, 2, 0, 270, -10);
            context.waitTicks(6);
            context.takeScreenshot("clash_2_void_side_facing_shrine");
            this.tp(server, 32, 2, 0, 90, -10);
            context.waitTicks(6);
            context.takeScreenshot("clash_3_shrine_side_facing_void");
            this.tp(server, 20, 40, -120, 0, 15);
            context.waitTicks(100);
            context.takeScreenshot("clash_4_outside");
            server.runOnServer(DomainShots::clearDomains);
    }

    /** Sukuna (an NPC) bearing Mahoraga's wheel over his head while his Mahoraga stands. */
    private void wheelShots(ClientGameTestContext context, TestServerContext server) {
        this.tp(server, 0, 1, -6, 0, 5);
        context.waitTicks(10);
        server.runOnServer(s -> {
            SukunaNpcEntity npc = this.sukuna(s, 0.5, 180.0f);
            cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.MahoragaSkill.summonFor(npc, new net.minecraft.world.phys.Vec3(4.5, this.ground, 3.5), null);
        });
        context.waitTicks(40);
        context.takeScreenshot("wheel_1_over_sukuna");
        this.tp(server, 3, 1, -5, 20, -10);
        context.waitTicks(10);
        context.takeScreenshot("wheel_2_with_mahoraga");
        server.runOnServer(DomainShots::clearDomains);
    }

    /** A motionless Sukuna NPC standing at (x, ground, 0.5) facing {@code yaw}. */
    private SukunaNpcEntity sukuna(MinecraftServer s, double x, float yaw) {
        ServerLevel level = s.overworld();
        SukunaNpcEntity npc = new SukunaNpcEntity(SukunaMod.SUKUNA_NPC, level);
        npc.setNoAi(true);
        npc.snapTo(x, this.ground, 0.5, yaw, 0.0f);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        level.addFreshEntity(npc);
        return npc;
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private void fly(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
    }

    /** Teleports the player (and so the camera) to a spot above the ground, looking yaw / pitch. */
    private void tp(TestServerContext server, double x, double up, double z, float yaw, float pitch) {
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x + 0.5, this.ground + up, z + 0.5, yaw, pitch));
        this.fly(server);
    }

    private static void clearDomains(MinecraftServer s) {
        for (ServerLevel level : s.getAllLevels()) {
            List<Entity> doomed = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof VoidDomainEntity || e instanceof ShrineEntity || e instanceof SukunaNpcEntity
                    || e instanceof cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity) doomed.add(e);
            }
            doomed.forEach(Entity::discard);
        }
    }
}
