package cn.blockforge.ryomensukuna.m2a542fea.skill.domain;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.SukunaSounds;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainClash;
import cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity;
import cn.blockforge.ryomensukuna.m2a542fea.net.SukunaNet;
import cn.blockforge.ryomensukuna.m2a542fea.progression.Progression;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** Malevolent Shrine: one per caster (player or Sukuna NPC). */
public final class DomainSkill {
    private static final Map<UUID, ShrineEntity> ACTIVE = new HashMap<UUID, ShrineEntity>();
    private static final double SHRINE_DISTANCE = 12.0;

    public static void onFinished(ShrineEntity shrine) {
        ACTIVE.entrySet().removeIf(entry -> entry.getValue() == shrine);
    }

    public static boolean active(LivingEntity p) {
        ShrineEntity s = ACTIVE.get(p.getUUID());
        return s != null && !s.isRemoved() && s.level() == p.level();
    }

    public static boolean dismiss(ServerPlayer p) {
        ShrineEntity s = ACTIVE.remove(p.getUUID());
        if (s == null || s.isRemoved()) {
            return false;
        }
        s.finishDomain();
        SukunaNet.actionBar(p, "sukuna.hint.domain_end", new Object[0]);
        return true;
    }

    public static void cast(ServerPlayer p, float charge) {
        if (castFor(p, StageRules.domainTicks(CurseManager.of(p).stage()), Progression.latestCast(p))) {
            Progression.recordDomainExpanded(p);
            SukunaNet.actionBar(p, "sukuna.hint.domain", new Object[0]);
        }
    }

    /** Raises the shrine behind the caster and opens the domain. Returns false if one is already open. */
    public static boolean castFor(LivingEntity p, int activeTicks, int castId) {
        if (DomainSkill.active(p)) {
            return false;
        }
        // The shrine model is drawn 1.75x larger, so it rises further behind the caster.
        Vec3 candidate = p.position().subtract(Vec3.directionFromRotation(0.0f, p.getYRot()).scale(SHRINE_DISTANCE));
        Vec3 anchor = candidate;
        int y = (int)p.getY() + 2;
        while ((double)y > p.getY() - 24.0) {
            BlockPos pos = BlockPos.containing(candidate.x, (double)y, candidate.z);
            if (!p.level().getBlockState(pos).getCollisionShape((BlockGetter)p.level(), pos).isEmpty()) {
                anchor = new Vec3(candidate.x, (double)(y + 1), candidate.z);
                break;
            }
            --y;
        }
        ShrineEntity s = new ShrineEntity(SukunaMod.SHRINE, p.level());
        s.setOwnerUuid(p.getStringUUID());
        s.snapTo(anchor.x, anchor.y, anchor.z, p.getYRot(), 0.0f);
        p.level().addFreshEntity((Entity)s);
        s.castId = castId;
        s.activeTicks = activeTicks;
        ACTIVE.put(p.getUUID(), s);
        DomainClash.register(p, s, anchor, s.radius, false);
        p.level().playSound(null, anchor.x, anchor.y + 3.0, anchor.z, SukunaSounds.DOMAIN_OPEN, SoundSource.PLAYERS, 4.0f, 0.55f);
        // "Domain Expansion: Malevolent Shrine" voice line, heard across the whole domain.
        p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SukunaSounds.SHRINE_VOICE, SoundSource.PLAYERS, 6.0f, 1.0f);
        return true;
    }
}
