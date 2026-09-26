package cn.blockforge.ryomensukuna.m2a542fea;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class SukunaSounds {
    public static final SoundEvent SLASH1 = SukunaSounds.of("slash1");
    public static final SoundEvent SLASH2 = SukunaSounds.of("slash2");
    public static final SoundEvent RED_SHOOT = SukunaSounds.of("red_shoot");
    public static final SoundEvent RED_BOOM = SukunaSounds.of("red_boom");
    public static final SoundEvent DOMAIN_OPEN = SukunaSounds.of("domain_open");
    public static final SoundEvent DOMAIN_CLAP = SukunaSounds.of("domain_clap");
    public static final SoundEvent DOMAIN_SLICE = SukunaSounds.of("domain_slice");
    public static final SoundEvent DOMAIN_END = SukunaSounds.of("domain_end");
    public static final SoundEvent DOMAIN_BGM = SukunaSounds.of("domain_bgm");
    public static final SoundEvent WORLD_CUT = SukunaSounds.of("world_cut");
    public static final SoundEvent MAHORAGA_SPAWN = SukunaSounds.of("mahoraga_spawn");
    public static final SoundEvent MAHORAGA_ADAPT = SukunaSounds.of("mahoraga_adapt");
    public static final SoundEvent FINGER_EAT = SukunaSounds.of("finger_eat");
    public static final SoundEvent UNLOCK = SukunaSounds.of("unlock");
    /** Domain expansion voice + Unlimited Void music (user-provided track). */
    public static final SoundEvent VOID_THEME = SukunaSounds.of("void_theme");
    /** Seamless loop cut from the music part of the theme, for the rest of the domain. */
    public static final SoundEvent VOID_LOOP = SukunaSounds.of("void_loop");
    /** Malevolent Shrine voice line (user-provided, loudness raised). */
    public static final SoundEvent SHRINE_VOICE = SukunaSounds.of("shrine_voice");
    public static final SoundEvent RCT_HEAL = SukunaSounds.of("rct_heal");
    public static final SoundEvent BLACK_FLASH = SukunaSounds.of("black_flash");

    private SukunaSounds() {
    }

    private static SoundEvent of(String path) {
        return SukunaSounds.register(path);
    }

    private static SoundEvent register(String path) {
        return (SoundEvent)Registry.register((Registry)BuiltInRegistries.SOUND_EVENT, (Identifier)SukunaMod.id(path), SoundEvent.createVariableRangeEvent((Identifier)SukunaMod.id(path)));
    }

    public static void register() {
    }
}

