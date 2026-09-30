package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-03 / W44 / C33 架构守卫（源码扫描，无 DB）：JSON DAG 单模型收敛。
 *
 * <p>核心不变式（详细设计-02 §F02-03，R-2 a）：
 * <ul>
 *   <li>{@code PipelineExecutionEngine}（YAML 执行器）必须携带
 *       {@code @Deprecated} —— 唯一承流模型已收敛至 JSON DAG
 *       （{@code /api/v1/pipeline/definitions/{id}/execute} → POA 侧 runtime-task）。</li>
 *   <li>{@code PipelineTaskController} 类级 {@code @Deprecated} + {@code ECOS-DATA-011} 常量
 *       仍在（409 只读桩契约），且写端点（POST /tasks、PUT /tasks/{id}、DELETE /tasks/{id}、
 *       POST /tasks/{id}/run、POST /tasks/{id}/cancel）必须统一调用
 *       {@link com.chinacreator.gzcm.engine.data.controller.PipelineTaskController#readonlyConflict}
 *       抛出 409，不得调用 {@code taskService} 的写方法。
 *   <li>{@code PipelineExecutionEngine} 内不得出现任何 HTTP/route 注解（其本身不是 controller，
 *       更不允许新增 {@code @PostMapping("/tasks/{id}/run")} 之类的 YAML 执行直连路由）。</li>
 * </ul>
 *
 * 命中标记违规 = FAIL —— 单模型守门，冗余 YAML 执行器复活即回归。
 */
@DisplayName("F02-03 / W44：Pipeline 单模型（JSON DAG）守护 — YAML 执行器/控制器只读")
class PipelineSingleModelArchTest {

    private static final Pattern DEPRECATED_ANNOTATION =
            Pattern.compile("@\\s*Deprecated");
    private static final Pattern MAPPING_ANNOTATION =
            Pattern.compile("@\\s*(Post|Put|Delete|Patch|Get)Mapping\\b");
    private static final Pattern TASK_SERVICE_WRITE_CALL =
            Pattern.compile("\\btaskService\\s*\\.\\s*(create|update|delete|trigger|cancel|run)\\w*\\s*\\(");
    private static final Pattern READONLY_CONFLICT_CALL =
            Pattern.compile("\\breadonlyConflict\\s*\\(");

    @Test
    @DisplayName("PipelineExecutionEngine（YAML 执行器）已标 @Deprecated 且不承载任何 HTTP 路由")
    void yamlEngineDeprecatedAndRouteless() throws IOException {
        Path root = moduleRoot();
        Path engine = root.resolve("src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineExecutionEngine.java");
        assertTrue(Files.isRegularFile(engine), "PipelineExecutionEngine 源码缺失: " + engine);
        String src = Files.readString(engine, StandardCharsets.UTF_8);

        assertTrue(DEPRECATED_ANNOTATION.matcher(src).find(),
                "F02-03/W44: PipelineExecutionEngine 必须标 @Deprecated（YAML 执行器已归档）");

        // 引擎自身禁止新增任何 Route 注解 —— 唯一执行入口收敛至 JSON DAG
        List<String> foundRoutes = new ArrayList<>();
        int lin = 0;
        for (String raw : src.split("\n", -1)) {
            lin++;
            String t = raw.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            if (MAPPING_ANNOTATION.matcher(raw).find()) {
                foundRoutes.add(lin + ": " + t);
            }
        }
        assertTrue(foundRoutes.isEmpty(),
                "PipelineExecutionEngine 不得含任何 @*Mapping 路由（F02-03 单模型收敛，路由归 JSON DAG /definitions/{id}/execute）：\n"
                        + String.join("\n", foundRoutes));
    }

    @Test
    @DisplayName("PipelineTaskController：5 个写端点全部走 readonlyConflict 抛 409，不真调 taskService 写方法")
    void taskControllerWritesOnlyThroughReadonlyConflict() throws IOException {
        Path root = moduleRoot();
        Path ctrl = root.resolve("src/main/java/com/chinacreator/gzcm/engine/data/controller/PipelineTaskController.java");
        assertTrue(Files.isRegularFile(ctrl), "PipelineTaskController 源码缺失: " + ctrl);
        String src = Files.readString(ctrl, StandardCharsets.UTF_8);

        // 类级 @Deprecated
        assertTrue(DEPRECATED_ANNOTATION.matcher(src).find(),
                "PipelineTaskController 必须携带 @Deprecated（F02-03 R-2 a）");
        assertTrue(src.contains("ECOS-DATA-011"),
                "PipelineTaskController 必须声明 ECOS-DATA-011 错误码常量");

        // 5 个写端点映射注解齐全（POST /tasks, PUT /tasks/{id}, DELETE /tasks/{id},
        // POST /tasks/{id}/run, POST /tasks/{id}/cancel）
        assertTrue(src.contains("@PostMapping(\"/tasks\")"), "缺 POST /tasks 桩");
        assertTrue(src.contains("@PutMapping(\"/tasks/{id}\")"), "缺 PUT /tasks/{id} 桩");
        assertTrue(src.contains("@DeleteMapping(\"/tasks/{id}\")"), "缺 DELETE /tasks/{id} 桩");
        assertTrue(src.contains("/tasks/{id}/run"), "缺 POST /tasks/{id}/run 桩");
        assertTrue(src.contains("/tasks/{id}/cancel"), "缺 POST /tasks/{id}/cancel 桩");

        // 5 处 readonlyConflict(...) 调用 = 5 个写端点全部 409 抛路径
        int conflictCalls = 0;
        var m = READONLY_CONFLICT_CALL.matcher(src);
        while (m.find()) {
            conflictCalls++;
        }
        // = 4 次在方法体内 + 1 次在 readonlyConflict 方法自身定义；至少 5 出现
        assertTrue(conflictCalls >= 5,
                "5 个写端点 + readonlyConflict 定义 ⇒ readonlyConflict( 应至少出现 5 次，实际 " + conflictCalls);

        // 严禁 taskService.create/update/delete/trigger/cancel/run 直接调用（写穿透 YAML 执行器）
        List<String> violations = new ArrayList<>();
        int lin = 0;
        for (String raw : src.split("\n", -1)) {
            lin++;
            String t = raw.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            if (TASK_SERVICE_WRITE_CALL.matcher(raw).find()) {
                violations.add(lin + ": " + t);
            }
        }
        assertTrue(violations.isEmpty(),
                "PipelineTaskController 写端点不得委托 taskService 真实写方法（F02-03 只读归档）：\n"
                        + String.join("\n", violations));
    }

    @Test
    @DisplayName("PipelineExecutionEngine 无被 PipelineTaskController 引用（休眠态，唯一调用方已 409）")
    void yamlEngineNoLongerInvokedByTaskController() throws IOException {
        Path root = moduleRoot();
        Path ctrl = root.resolve("src/main/java/com/chinacreator/gzcm/engine/data/controller/PipelineTaskController.java");
        assertTrue(Files.isRegularFile(ctrl), "PipelineTaskController 缺失");
        String src = Files.readString(ctrl, StandardCharsets.UTF_8);
        // 控制器导入区不含 PipelineExecutionEngine（IR03 代码保留但不新增引用——已归档不得复活）
        assertFalse(src.contains("PipelineExecutionEngine"),
                "PipelineTaskController 不得 import/引用 PipelineExecutionEngine（F02-03 单模型收敛 README：休眠失活）");
    }

    /** 从 user.dir 上溯仓库根，定位 data-engine-impl 模块根（与 NoDriverManagerArchTest 同口径）。 */
    private static Path moduleRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path cur = dir;
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve(".git")) || Files.isDirectory(cur.resolve(".git"))) {
                return cur.resolve("ecos_backend/engine/data-engine/data-engine-impl");
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 data-engine-impl 模块根（缺 .git）");
    }
}
