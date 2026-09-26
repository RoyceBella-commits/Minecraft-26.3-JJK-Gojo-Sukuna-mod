package cn.blockforge.ryomensukuna.m2a542fea.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Instance-local display preference, independent of player progression and world saves. */
public final class HudPreferences {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sukuna/HudPreferences");
    private final Path path;
    private JsonObject values = new JsonObject();
    private boolean visible = true;
    private boolean lowFx;
    private boolean reduceShake;
    private boolean noFlash;

    public HudPreferences(Path path) {
        this.path = path;
        if (!Files.exists(path)) { save(); return; }
        try {
            values = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            var setting = values.get("hudVisible");
            if (setting != null && setting.isJsonPrimitive() && setting.getAsJsonPrimitive().isBoolean()) {
                visible = setting.getAsBoolean();
            }
            lowFx = flag("lowFx");
            reduceShake = flag("reduceShake");
            noFlash = flag("noFlash");
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Cannot read HUD preference {}; using visible=true", path, e);
            values = new JsonObject();
        }
    }

    public boolean visible() { return visible; }
    /** Fewer decorative particles; danger boundaries and warnings are kept. */
    public boolean lowFx() { return lowFx; }
    public boolean reduceShake() { return reduceShake; }
    public boolean noFlash() { return noFlash; }

    private boolean flag(String key) {
        var v = values.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean();
    }
    public void toggle() { visible = !visible; save(); }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            values.addProperty("hudVisible", visible);
            if (!values.has("lowFx")) values.addProperty("lowFx", lowFx);
            if (!values.has("reduceShake")) values.addProperty("reduceShake", reduceShake);
            if (!values.has("noFlash")) values.addProperty("noFlash", noFlash);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, values.toString() + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Cannot save HUD preference {}; retaining it for this session", path, e);
        }
    }
}
