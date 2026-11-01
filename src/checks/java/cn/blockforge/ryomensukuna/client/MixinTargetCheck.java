package cn.blockforge.ryomensukuna.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Headless check that every method and field our mixins hook into exists in this Minecraft version
 * (read straight from the class files, nothing is loaded or run). A renamed target would otherwise
 * only show up as a crash when the game starts. Keep this list in step with the mixins.
 */
public final class MixinTargetCheck {
    private MixinTargetCheck() {
    }

    /** owner class, member name, descriptor (null: any overload; "F:" prefix for a field). */
    private static final String[][] TARGETS = {
        {"net.minecraft.world.entity.LivingEntity", "getArmorValue", null},
        {"net.minecraft.world.entity.LivingEntity", "hurtServer", null},
        {"net.minecraft.world.entity.player.Player", "actuallyHurt", null},
        {"net.minecraft.world.entity.player.Player", "setHealth", "(F)V"},
        {"net.minecraft.world.level.Level", "setBlock", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},
        {"net.minecraft.world.level.Level", "destroyBlock", "(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"},
        {"net.minecraft.world.level.ServerExplosion", "calculateExplodedPositions", null},
        {"net.minecraft.world.level.ServerExplosion", "F:level", "Lnet/minecraft/server/level/ServerLevel;"},
        {"net.minecraft.client.Camera", "update", null},
        {"net.minecraft.client.Camera", "setRotation", "(FF)V"},
        {"net.minecraft.client.Camera", "F:entity", null},
        {"net.minecraft.client.Camera", "F:xRot", null},
        {"net.minecraft.client.Camera", "F:yRot", null},
        {"net.minecraft.client.model.player.PlayerModel", "setupAnim", "(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"},
        {"net.minecraft.client.Minecraft", "shouldEntityAppearGlowing", null},
        {"net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer", "submitHandsWithItems", null},
        {"net.minecraft.client.renderer.chunk.ChunkSectionsToRender", "renderGroup", null},
        {"net.minecraft.client.renderer.chunk.ChunkSectionsToRender", "renderOit", null},
    };

    public static void main(String[] args) throws IOException {
        List<String> missing = new ArrayList<>();
        for (String[] t : TARGETS) {
            Set<String> members = members(t[0], missing);
            if (members == null) continue;
            String want = t[1] + (t[2] == null ? "" : t[2]);
            boolean found = t[2] == null
                ? members.stream().anyMatch(m -> m.equals(t[1]) || m.startsWith(t[1] + "("))
                : members.contains(want);
            if (!found) missing.add(t[0] + " " + want);
        }
        if (!missing.isEmpty()) {
            missing.forEach(m -> System.err.println("missing mixin target: " + m));
            throw new IllegalStateException(missing.size() + " mixin target(s) not found");
        }
        System.out.println("PASS: " + TARGETS.length + " mixin targets exist in this Minecraft version");
    }

    /** "name(desc)" for methods, "F:name" + desc for fields, up the superclass chain; null (and noted) if the class is missing. */
    private static Set<String> members(String className, List<String> missing) throws IOException {
        Set<String> out = new HashSet<>();
        String current = className.replace('.', '/');
        boolean first = true;
        while (current != null && current.startsWith("net/minecraft/")) {
            String next = read(current, out);
            if (next == null && first) {
                missing.add(className + " (class)");
                return null;
            }
            first = false;
            current = "-".equals(next) ? null : next;
        }
        return out;
    }

    /** Adds one class's members; returns its superclass, "-" for none, or null if the class is missing. */
    private static String read(String internalName, Set<String> out) throws IOException {
        try (InputStream in = MixinTargetCheck.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            ClassReader reader = new ClassReader(in);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    out.add(name + desc);
                    return null;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                    out.add("F:" + name + desc);
                    out.add("F:" + name);
                    return null;
                }
            }, ClassReader.SKIP_CODE);
            String sup = reader.getSuperName();
            return sup == null ? "-" : sup;
        }
    }
}
