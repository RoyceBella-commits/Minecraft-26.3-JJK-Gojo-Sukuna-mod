package cn.blockforge.ryomensukuna.m2a542fea.client;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
/** One shared key set for both routes; every binding can be changed (mouse side buttons too). */
public final class SukunaKeys {
    public static KeyMapping WHEEL, CAST, QUICK, DASH, LEAP, HUD;
    /** Optional direct key per technique (index = skill net id); all unbound by default. */
    public static final KeyMapping[] SKILLS = new KeyMapping[cn.blockforge.ryomensukuna.m2a542fea.skill.Skill.values().length];
    public static boolean down(Minecraft client, KeyMapping binding) { return binding.isDown(); }
    public static void init() {
        var category = KeyMapping.Category.register(SukunaMod.id("controls"));
        WHEEL=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.wheel",InputConstants.Type.KEYBOARD,InputConstants.KEY_LALT,category));
        CAST=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.cast",InputConstants.Type.KEYBOARD,InputConstants.KEY_G,category));
        QUICK=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.quick",InputConstants.Type.KEYBOARD,InputConstants.KEY_B,category));
        DASH=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.dash",InputConstants.Type.KEYBOARD,InputConstants.KEY_C,category));
        LEAP=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.leap",InputConstants.Type.KEYBOARD,InputConstants.KEY_Z,category));
        HUD=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.hud",InputConstants.Type.KEYBOARD,InputConstants.KEY_J,category));
        var skills = KeyMapping.Category.register(SukunaMod.id("skills"));
        for (var skill : cn.blockforge.ryomensukuna.m2a542fea.skill.Skill.values()) {
            SKILLS[skill.netId]=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sukuna.skill."+skill.name().toLowerCase(java.util.Locale.ROOT),
                InputConstants.Type.KEYBOARD,InputConstants.UNKNOWN.getValue(),skills));
        }
    }
}
