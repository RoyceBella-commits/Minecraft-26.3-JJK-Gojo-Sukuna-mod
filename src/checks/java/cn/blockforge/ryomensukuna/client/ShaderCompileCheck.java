package cn.blockforge.ryomensukuna.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

/**
 * Headless GLSL check: compiles every shader of the mod with shaderc, the compiler the game itself
 * uses (Vulkan 1.2 target, uniforms bound automatically, {@code minecraft:} includes taken from the
 * game jar). A typo in a shader then fails the build instead of blanking a domain in game. No game
 * or window is started.
 */
public final class ShaderCompileCheck {
    private ShaderCompileCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args[0]);
        List<Path> shaders;
        try (Stream<Path> files = Files.list(dir)) {
            shaders = files.filter(p -> p.toString().endsWith(".fsh") || p.toString().endsWith(".vsh")).sorted().toList();
        }
        List<ByteBuffer> keep = new ArrayList<>();
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
        Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
        Shaderc.shaderc_compile_options_set_preserve_bindings(options, true);
        ShadercIncludeResolve resolve = ShadercIncludeResolve.create((user, requested, type, requesting, depth) -> {
            String name = MemoryUtil.memUTF8(requested);
            ShadercIncludeResult result = ShadercIncludeResult.calloc();
            ByteBuffer source = MemoryUtil.memUTF8(include(name), false);
            ByteBuffer label = MemoryUtil.memUTF8(name, false);
            keep.add(source);
            keep.add(label);
            result.source_name(label);
            result.content(source);
            return result.address();
        });
        ShadercIncludeResultRelease release = ShadercIncludeResultRelease.create((user, result) -> ShadercIncludeResult.create(result).free());
        Shaderc.shaderc_compile_options_set_include_callbacks(options, resolve, release, 0L);
        List<String> failures = new ArrayList<>();
        try {
            for (Path shader : shaders) {
                String text = Files.readString(shader, StandardCharsets.UTF_8);
                int kind = shader.toString().endsWith(".vsh") ? Shaderc.shaderc_vertex_shader : Shaderc.shaderc_fragment_shader;
                long result = Shaderc.shaderc_compile_into_spv(compiler, text, kind, "sukuna:core/" + shader.getFileName(), "main", options);
                if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                    failures.add(shader.getFileName() + ":\n" + Shaderc.shaderc_result_get_error_message(result));
                }
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
            resolve.free();
            release.free();
            keep.forEach(MemoryUtil::memFree);
        }
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            throw new IllegalStateException(failures.size() + " shader(s) failed to compile");
        }
        System.out.println("PASS: " + shaders.size() + " shaders compile with shaderc (Vulkan 1.2 target, as in game)");
    }

    /** {@code minecraft:name.glsl} from the game jar's shader includes. */
    private static String include(String name) {
        String path = name.contains(":") ? "assets/" + name.substring(0, name.indexOf(':')) + "/shaders/include/" + name.substring(name.indexOf(':') + 1)
            : "assets/minecraft/shaders/include/" + name;
        try (InputStream in = ShaderCompileCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("include not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
