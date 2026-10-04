package com.chinacreator.gzcm.engine.ontology.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F03-06 W89/C71 (M0 P0, O-25)：本体侧审计事件防线——业务包禁 {@code java.lang.reflect}
 * 反射调 {@code KafkaTemplate.send(...)} 的 bypass pattern（架构铁律 §2.5-4 / 后端规范 §九）。
 *
 * <p>O-25 根因：{@code SecurityEngineClient.audit}(旧) 用
 * {@code kafkaTemplateBean.getClass().getMethod("send", String, Object).invoke(...)}
 * 反射绕过 {@code runtime-event} 横切底座；现有 {@link com.chinacreator.gzcm.engine.ontology.ArchitectureTest}
 * 类型引用面看不出反射调用路径（{@code Object} typed bean 只作为 Object 被引用）。
 *
 * <p>本护栏 = 文件级（source text）walk ontology-engine-impl main 源，禁两条 pattern：
 * <ol>
 *   <li>{@code getClass().getMethod("send", String.class, Object.class)}（O-25 具体形态）</li>
 *   <li>{@code KafkaTemplate} 附近 400 字符 window 内出现 {@code getMethod}（防御性：反射
 *       相关方法名调用）</li>
 * </ol>
 *
 * <p>任何新增反射 send 调用（包括 "冷巧" 改名 wrapper 但保留 getMethod 形态）都会
 * 红。纯 IO 无 Spring / 无 ArchUnit 类收集器（走 source 文本比 ClassFile index 更严）。
 */
class ReflectiveKafkaBypassArchTest {

    @Test
    @DisplayName("src 走查：SecurityEngineClient 洁净（无 java.lang.reflect.Method import，无 getMethod(\"send\") 反射）")
    void securityEngineClientClean() throws Exception {
        Path me = mainSrcOf("security/SecurityEngineClient.java");
        String s = mustRead(me);
        assertFalse(s.contains("import java.lang.reflect.Method"),
                "O-25 已禁：不应再有 java.lang.reflect.Method import: " + me);
        assertFalse(s.contains("getClass().getMethod(\"send\""),
                "O-25 已禁：不应再有 getClass().getMethod(\"send\", …) 反射 send: " + me);
    }

    @Test
    @DisplayName("src 走查：ontology-engine-impl main 全包无 KafkaTemplate + getMethod 反射 send 组合")
    void mainPackageHasNoReflectiveKafkaSend() throws Exception {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root), "main 源根未找到: " + root.toAbsolutePath());
        try (var stream = Files.walk(root)) {
            List<Path> java = stream.filter(p -> p.toString().endsWith(".java")).toList();
            assertTrue(java.size() > 0);
            for (Path p : java) {
                String s = Files.readString(p);
                if (s.contains("getClass().getMethod(\"send\"")) {
                    fail("O-25 已禁：源码残留反射 send 调用: " + p);
                }
                scanKafkaTemplateReflectiveWindow(s, p);
            }
        }
    }

    private static void scanKafkaTemplateReflectiveWindow(String src, Path file) {
        int idx = src.indexOf("KafkaTemplate");
        while (idx >= 0) {
            int end = Math.min(src.length(), idx + 400);
            String window = src.substring(idx, end);
            if (window.indexOf("getMethod") >= 0) {
                fail("O-25 已禁：KafkaTemplate 附近 400 字符内出现 getMethod 反射: " + file);
            }
            idx = src.indexOf("KafkaTemplate", idx + 1);
        }
    }

    private static Path mainSrcOf(String subpath) {
        Path p = Path.of("src", "main", "java", "com", "chinacreator", "gzcm", "engine",
                "ontology").resolve(subpath);
        if (!Files.isRegularFile(p)) {
            Path resolved = Path.of(subpath);
            return Files.isRegularFile(resolved) ? resolved : p;
        }
        return p;
    }

    private static String mustRead(Path p) throws Exception {
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("source file missing: " + p.toAbsolutePath());
        }
        return Files.readString(p);
    }
}
