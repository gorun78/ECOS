package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-09 / C155 §8.1 点名 {@code FormalBaselineReferenceTest#guardImplementedOnceNotTwice}
 * — 单一 SSOT 反锚点门禁：{@link BaselineReferenceGuard} 的
 * {@code assertFormalMayReference} / {@code assertForecastRunIsFormal} 两个方法签名
 * 在 workspace-impl 源码里**必须恰出现 1 处声明**（均在 {@code BaselineReferenceGuard.java}）。
 *
 * <p>为什么源码级：F07-09 与 F07-10 **共用同一 guard 实例的两种语义**（正锚点 / 反锚点），
 * 设计明文"禁两处各写一份"（X-34 反重制，本卷 C170 "以源码 regex 断言单源"同口径）。
 * 行为级单测（{@code BaselineReferenceGuardTest} 8 例）能抓"任一语义落地错了"，但抓不到
 * "两处各写一份导致语义 drift"——源码级 SSOT 反锚点是 1:1 的对应断言：若有人按场景域另扩一个
 * copy 的 guard 类做 F07-10 消费，本用例立即变红。不引 ArchUnit 依赖，读源文件即可离线证伪。</p>
 */
class FormalBaselineReferenceTest {

    /** 两个必须单源的方法名。 */
    private static final String[] GUARD_METHODS = {"assertFormalMayReference", "assertForecastRunIsFormal"};

    /** workspace-impl 模块根：自 CWD 向上找同时有 pom.xml 与 src/main/java 的目录（Maven CWD 不稳）。 */
    private static Path moduleRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("src/main/java"))
                    && Files.exists(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new AssertionError("无法定位 workspace-impl 模块根（src/main/java + pom.xml）");
    }

    @Test
    @DisplayName("guardImplementedOnceNotTwice：正/反锚点两个方法签名必须在 BaselineReferenceGuard 单类里各声明 1 处")
    void guardImplementedOnceNotTwice() throws IOException {
        Path root = moduleRoot().resolve("src/main/java");
        String[] declFile = {null, null};
        int[] hits = {0, 0};
        for (String name : GUARD_METHODS) {
            Set<Path> declFiles = scanDeclarations(root, name);
            assertEquals(1, declFiles.size(),
                    name + " 声明必须恰在 1 个文件里（BaselineReferenceGuard 单源落点），"
                            + "否则就是 X-34 重制复辟；当前命中: " + declFiles);
            Path only = declFiles.iterator().next();
            assertTrue(only.getFileName().toString().equals("BaselineReferenceGuard.java"),
                    "单源落点必须是 BaselineReferenceGuard.java（现命中 = " + only + "）");
            if (name.equals(GUARD_METHODS[0])) {
                declFile[0] = only.toString();
            } else {
                declFile[1] = only.toString();
            }
        }
        assertEquals(declFile[0], declFile[1],
                "正/反锚点必须落在同一个 BaselineReferenceGuard.java（同实例两方法，F07-09 与 F07-10 单源共用）；"
                        + "正 = " + declFile[0] + "；反 = " + declFile[1]);
    }

    private static Set<Path> scanDeclarations(Path root, String methodName) throws IOException {
        Set<Path> out = new HashSet<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    if (containsDeclaration(content, methodName)) {
                        out.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    /** 匹配"签名声明"：`<vis>[ static] <retType> <name>(`。调用点形态 `guard.assert...(`/`this.assert...(`
     * 前方是 {@code .}、无可见性修饰符，恒不命中；Javadoc {@code {@link #assert...}} 亦无修饰符。 */
    private static boolean containsDeclaration(String content, String methodName) {
        java.util.regex.Pattern decl = java.util.regex.Pattern.compile(
                "(public|protected|private)(\\s+static)?\\s+[\\w$<>,?]+\\s+" + methodName + "\\s*\\(");
        return decl.matcher(content).find();
    }
}
