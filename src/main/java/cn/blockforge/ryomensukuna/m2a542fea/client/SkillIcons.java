package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import java.util.Locale;
import net.minecraft.resources.Identifier;

public final class SkillIcons {
    private SkillIcons() {
    }

    public static Identifier id(Skill skill) {
        return SukunaMod.id("textures/gui/skills/" + skill.name().toLowerCase(Locale.ROOT) + ".png");
    }
}

