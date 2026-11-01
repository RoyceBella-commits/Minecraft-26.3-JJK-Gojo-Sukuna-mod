package cn.blockforge.ryomensukuna.m2a542fea.api;

import cn.blockforge.ryomensukuna.m2a542fea.combat.DomainTerrain;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Stable entry points for other mods' domains (e.g. a reality marble) that reshape the land they
 * cover and want it back afterwards, sharing this mod's terrain journal:
 * <ol>
 *   <li>{@link #open} a region when the domain unfolds (enemies just outside are pulled in);</li>
 *   <li>change blocks inside it only through {@link #place}, which remembers the originals;</li>
 *   <li>{@link #close} it when the domain ends.</li>
 * </ol>
 * Blocks go back once no open region - this mod's or anyone's - covers them any more, so overlapping
 * domains restore cleanly in any order. Our superflat domains leave the inside of such a region alone.
 * Block entities and bedrock are never changed. Server thread only.
 */
public final class DomainTerrainApi {
    private DomainTerrainApi() {
    }

    /**
     * Opens a region (a sphere). {@code enemy}: who is pulled in from up to half a radius beyond the
     * edge, or null to pull nobody. Returns the region's id for {@link #close}.
     */
    public static UUID open(ServerLevel level, Vec3 center, double radius, UUID owner, Predicate<LivingEntity> enemy) {
        return DomainTerrain.openPlain(level, center, radius, owner, enemy);
    }

    /** Sets a block, journaling what was there first if a region covers it. False if it may not be changed. */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state) {
        return DomainTerrain.place(level, pos, state);
    }

    /** Closes a region: what it changed goes back as soon as no other region covers it. */
    public static void close(UUID region) {
        DomainTerrain.close(region);
    }

    public static boolean isOpen(UUID region) {
        return DomainTerrain.isOpen(region);
    }
}
