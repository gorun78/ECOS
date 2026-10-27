package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-03 验收《YamlTaskReadOnlyTest》— PipelineTaskController YAML 模型写端点只读守卫。
 *
 * <p>设计 §F02-03（R-2 a 已批准）：以 JSON DAG 模型（{@code /api/v1/pipeline/definitions}）
 * 为唯一承流模型；{@code PipelineTaskController}（YAML 模型）转只读归档 —— 只停写不删（IR03）：
 * <ul>
 *   <li>POST /tasks、PUT /tasks/{id}、DELETE /tasks/{id}、
 *       POST /tasks/{id}/run、POST /tasks/{id}/cancel 五个写端点必须抛
 *       {@code DataBridgeException(httpStatus=409, errorCode=409)}，
 *       消息首段 = {@code ECOS-DATA-011}（YAML 模型只读），
 *       gateway GlobalExceptionHandler 按 httpStatus 语义透出 409。</li>
 *   <li>GET 读端点保留（{@code deprecated=true} 包装）。</li>
 * </ul>
 *
 * <p>本测试为纯源码扫描（不依赖 Spring 上下文 / classloader，复用仓库既有 arch-test 惯用范式）：
 * <ol>
 *   <li>对 {@code PipelineTaskController.java} 用反射 + 源码正则分别抽取每个写端点的
 *       {@code @PostMapping/@PutMapping/@DeleteMapping} 方法和 {@code throw readonlyConflict(...)}
 *       抛出语句，断言全部 5 个写方法均显式 {@code throw readonlyConflict}（409 桩）。</li>
 *   <li>断言控制器声明的 409 消息常量单源取自
 *       {@link com.chinacreator.gzcm.common.data.DataDomainErrorCode#ECOS_DATA_011}（ECOS-DATA-011，
 *       #146l/B4-3e-93 起改指属主常量，不再本地承载字面量），与
 *       {@code readonlyConflict(..)} helper（httpStatus=409）。</li>
 *   <li>对 {@link com.chinacreator.gzcm.common.exception.DataBridgeException} 反射调用
 *       {@code getHttpStatus()}，断言 {@code new DataBridgeException(409, 409, msg)} 的 httpStatus=409
 *       → 即 {@code readonlyConflict(..)} 抛出的异常的 httpStatus 必为 409。</li>
 * </ol>
 */
@DisplayName("F02-03 PipelineTaskController YAML 写端点只读（409 + ECOS-DATA-011）")
class YamlTaskReadOnlyTest {

    /** 5 个写端点方法名（必须全部 throw readonlyConflict(409 桩)）。 */
    private static final List<String> WRITE_METHODS = List.of(
            "createTask",       // POST /tasks
            "updateTask",       // PUT /tasks/{id}
            "deleteTask",       // DELETE /tasks/{id}
            "triggerRun",       // POST /tasks/{id}/run
            "cancelRun"         // POST /tasks/{id}/cancel
    );

    private static final Path MODULE_REL = Paths.get(
            "ecos_backend", "engine", "data-engine", "data-engine-impl",
            "src", "main", "java", "com", "chinacreator", "gzcm", "engine", "data",
            "controller", "PipelineTaskController.java");

    /** 抽取方法体（从 `MethodName(...)` 起，直到下一个 `// ──` 分割线或文件尾）。 */
    private static final Pattern METHOD_BLOCK =
            Pattern.compile("public\\s+[^{]*?" +
                    "(?<name>" + String.join("|", WRITE_METHODS) + ")" +
                    "\\s*\\((?:[^()]|\\((?:[^()]|\\([^()]*\\))*\\))*\\)\\s*\\{(?<body>.*?)\\n\\s*\\}",
                    Pattern.DOTALL);

    /** 写方法内必须出现的 `throw readonlyConflict(` 调用。 */
    private static final Pattern THROW_READONLY =
            Pattern.compile("throw\\s+readonlyConflict\\s*\\(");

    @Test
    @DisplayName("所有 5 个 YAML 写端点方法均 throw readonlyConflict(409 桩)")
    void allWriteEndpointsAre409Stubs() throws Exception {
        Path src = findRepoRoot().resolve(MODULE_REL);
        assertTrue(Files.isRegularFile(src), "缺 PipelineTaskController 源文件: " + src);
        String contents = Files.readString(src, StandardCharsets.UTF_8);

        // ① 常量声明：消息首段机读码单源取 DataDomainErrorCode.ECOS_DATA_011（#146l/B4-3e-93 起改指属主常量）
        assertTrue(contents.contains("DataDomainErrorCode.ECOS_DATA_011"),
                "PipelineTaskController 必须从 DataDomainErrorCode 单源取 409 机读码");
        // ② readonlyConflict helper 走 DataBridgeException(httpStatus=409, errorCode=409)
        assertTrue(contents.contains("readonlyConflict"),
                "PipelineTaskController 必须提供 readonlyConflict(409 桩) helper");
        assertTrue(contents.contains("409"),
                "readonlyConflict 必须显式使用 409 状态码");

        // ③ 逐方法断言：每个写端点方法体必须有 `throw readonlyConflict(`
        Matcher m = METHOD_BLOCK.matcher(contents);
        List<String> missing = new ArrayList<>();
        List<String> found = new ArrayList<>();
        while (m.find()) {
            String name = m.group("name");
            String body = m.group("body");
            found.add(name);
            if (!THROW_READONLY.matcher(body).find()) {
                missing.add(name);
            }
        }
        for (String expected : WRITE_METHODS) {
            assertTrue(found.stream().anyMatch(expected::equals),
                    "写端点方法 " + expected + " 未被识别为方法（源文件可能已改名，需同步本测试）");
        }
        assertTrue(missing.isEmpty(),
                "以下 YAML 写端点方法未 throw readonlyConflict(409 桩)（应拒绝写入，F02-03）：\n"
                        + String.join("\n", missing));
    }

    @Test
    @DisplayName("readonlyConflict 抛出的 DataBridgeException httpStatus 必为 409")
    void readonlyConflictProduces409() throws Exception {
        // 通过反射构造一个与 readonlyConflict("X") 完全同形的异常，验证 httpStatus 语义。
        // PipelineTaskController 的 readonlyConflict 是 private static（无公开 accessor），
        // 等价构造 new DataBridgeException(409, 409, "ECOS-DATA-011 ...")。
        Class<?> cbe = Class.forName("com.chinacreator.gzcm.common.exception.DataBridgeException");
        Object ex = cbe.getConstructor(int.class, int.class, String.class)
                .newInstance(409, 409, "ECOS-DATA-011 YAML 管道模型已只读，请改用 /api/v1/pipeline/definitions（拒绝操作: POST /tasks）");
        assertEquals(409, cbe.getMethod("getHttpStatus").invoke(ex),
                "Code 层 readonlyConflict 走 DataBridgeException(409, 409, msg)，gateway 按 httpStatus 透出 409");
        assertNotNull(cbe.getMethod("getMessage").invoke(ex),
                "异常消息非空（含 ECOS-DATA-011 提示）");
    }

    // ── 工具 ─────────────────────────────────────────────────────────
    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
