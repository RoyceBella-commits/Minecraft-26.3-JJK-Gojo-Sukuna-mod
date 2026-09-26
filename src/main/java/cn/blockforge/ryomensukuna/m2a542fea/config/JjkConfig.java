package cn.blockforge.ryomensukuna.m2a542fea.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server rules in config/jjk-server.json; also switchable in game with /jjk terrain on|off. */
public final class JjkConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("JJK/Config");
    private static volatile boolean terrainDestruction = true;
    private static Path path;

    private JjkConfig() {
    }

    public static boolean terrainDestruction() {
        return terrainDestruction;
    }

    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve("jjk-server.json");
        try {
            if (Files.exists(path)) {
                JsonObject o = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("terrainDestruction") && o.get("terrainDestruction").isJsonPrimitive()) {
                    terrainDestruction = o.get("terrainDestruction").getAsBoolean();
                }
            } else {
                save();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Cannot read {}; using defaults", path, e);
        }
    }

    public static void setTerrainDestruction(boolean on) {
        terrainDestruction = on;
        save();
    }

    private static void save() {
        if (path == null) return;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("terrainDestruction", terrainDestruction);
            Files.createDirectories(path.getParent());
            Files.writeString(path, o + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Cannot save {}", path, e);
        }
    }
}
