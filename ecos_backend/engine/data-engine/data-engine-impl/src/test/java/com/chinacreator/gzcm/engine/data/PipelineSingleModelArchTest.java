package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-03（详细设计-02 §二.3，W44→C33，P0）—— 业务管道<b>单模型收敛</b>护栏（R-2 a 已批准）。
 *
 * <p>单模型不变量：承流模型 = <b>JSON DAG</b>（{@code /api/v1/pipeline/definitions}
 * / {@link com.chinacreator.gzcm.engine.data.pipeline.PipelineExecutionService}）；
 * <b>YAML task 模型只读归档</b>，其执行链
 * （HTTP 写端点 → {@code PipelineTaskServiceImpl} → {@code PipelineExecutionEngine}）
 * 在 HTTP 层必须已断开，且执行引擎不得再向 {@code yaml_content} 回写字节。
 *
 * <p>本测试为<b>纯源码文本扫描</b>（不连库、不启服务）的离线 arch 护栏，3 条哨兵：
 * <ol>
 *   <li><b>HTTP 写端点撤线</b>：{@code controller/PipelineTaskController.java} 内不得出现
 *       {@code taskService.<createTask|updateTask|deleteTask|triggerRun|cancelRun>( )} 任一调用
 *       （执行链 HTTP→service 断开），且 5 个原写端点仍显式 {@code return readonlyReject();}
 *       （撤线而非删端点，满足 API 只增不改）。</li>
 *   <li><b>执行引擎不得再挂路由</b>：任何 {@code controller/**} 文件不得引用
 *       {@code PipelineExecutionEngine}（防未来把 YAML 执行重新映射到某路由）。</li>
 *   <li><b>引擎不再回写 yaml_content</b>：{@code service/PipelineExecutionEngine.java}
 *       禁 {@code INSERT INTO ecos_pipeline_task} 与 {@code UPDATE … SET yaml_content}
 *       （YAML 内容只读）；保留对 {@code status} 列的状态回写（legacy 运行兼容）。
 *       引擎文件缺失 / 数量异常即判失败（防空文件静默绿）。</li>
 * </ol>
 *
 * <p><b>范围口径</b>：仅锁定 F02-03「YAML 只读 + JSON 承流」的单模型不变量。
 * {@code PipelineTaskServiceImpl}（service，非 controller）仍持有 engine 字段属"名义存活、
 * 无 HTTP 入口到达"的过渡态，由后续拆 engine 的独立 W 项收口，本护栏不越权扫描 service 层。
 * {@code PipelineGitService} 的 yaml_content 写属 Git 版本管理链（R-2 a 未明禁），另族治理。
 */
@DisplayName("F02-03 业务管道单模型收敛：YAML 执行链 HTTP 撤线（P0 arch，W44/C33）")
class PipelineSingleModelArchTest {

    private static final Pattern TASK_SERVICE_WRITE_CALL = Pattern.compile(
            "taskService\\s*\\.\\s*(createTask|updateTask|deleteTask|triggerRun|cancelRun)\\s*\\(");
    private static final Pattern READONLY_REJECT = Pattern.compile("return\\s+readonlyReject\\(\\)\\s*;");
    private static final Pattern ENGINE_REF = Pattern.compile("PipelineExecutionEngine");
    private static final Pattern ENGINE_INSERT_TASK =
            Pattern.compile("(?i)INSERT\\s+INTO\\s+ecos_pipeline_task");
    private static final Pattern ENGINE_UPDATE_YAML =
            Pattern.compile("(?i)UPDATE\\s+ecos_pipeline_task\\s+SET[^;]*yaml_content");
    private static final Pattern ENGINE_UPDATE_STATUS =
            Pattern.compile("(?i)UPDATE\\s+ecos_pipeline_task\\s+SET\\s+status");

    @Test
    @DisplayName("① PipelineTaskController 写端点全撤线（无 taskService 写调用 + 5× readonlyReject）")
    void yamlWriteEndpointsSeveredAtHttpLayer() throws IOException {
        String text = Files.readString(findSource("PipelineTaskController.java"), StandardCharsets.UTF_8);
        assertTrue(!TASK_SERVICE_WRITE_CALL.matcher(text).find(),
                "PipelineTaskController 不得直接调用 taskService 的写方法（createTask/updateTask/"
                        + "deleteTask/triggerRun/cancelRun）—— YAML 执行链 HTTP 层必须撤线（F02-03 R-2 a）");
        int rejects = countMatches(text, READONLY_REJECT);
        assertEquals(5, rejects,
                "PipelineTaskController 应对 5 个原写端点保留 `return readonlyReject();`"
                        + "（撤线而非删端点，API 只增不改）；实际 " + rejects);
    }

    @Test
    @DisplayName("② 任何 controller/** 不得引用 PipelineExecutionEngine（执行引擎不再挂 YAML 路由）")
    void noControllerReferencesYamlExecutionEngine() throws IOException {
        Path root = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> f.toString().contains("controller"))
                    .sorted()
                    ::iterator) {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                if (ENGINE_REF.matcher(content).find()) {
                    hits.add(p.getFileName().toString()
                            + ": controller 内引用 PipelineExecutionEngine（YAML 执行重新挂路由风险）");
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "F02-03 违规 —— controller 引用 YAML 执行引擎:\n" + String.join("\n", hits));
    }

    @Test
    @DisplayName("③ PipelineExecutionEngine 不回写 yaml_content（禁 INSERT/UPDATE yaml_content，保留 status）")
    void yamlExecutionEngineNeverWritesYamlContent() throws IOException {
        Path engine = findSource("PipelineExecutionEngine.java");
        String text = Files.readString(engine, StandardCharsets.UTF_8);
        assertTrue(!ENGINE_INSERT_TASK.matcher(text).find(),
                "PipelineExecutionEngine 不得 INSERT ecos_pipeline_task（YAML task 表退出写入，F02-03）");
        assertTrue(!ENGINE_UPDATE_YAML.matcher(text).find(),
                "PipelineExecutionEngine 不得 UPDATE … SET yaml_content（YAML 内容只读，F02-03）");
        assertTrue(ENGINE_UPDATE_STATUS.matcher(text).find(),
                "PipelineExecutionEngine 应保留对 ecos_pipeline_task 的 status 状态回写"
                        + "（legacy 运行兼容）—— 缺失即引擎执行逻辑被误改");
    }

    /** 在模块 main 根按文件名唯一查找源文件；数量 ≠1 即失败（防空文件 / 重名静默绿）。 */
    private static Path findSource(String filename) throws IOException {
        Path root = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        List<Path> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                if (p.getFileName().toString().equals(filename)) {
                    found.add(p);
                }
            }
        }
        assertEquals(1, found.size(),
                "期望在模块 main 中恰好找到 1 个 " + filename + "，实际 " + found.size());
        return found.get(0);
    }

    private static int countMatches(String text, Pattern p) {
        int n = 0;
        Matcher m = p.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }
}
