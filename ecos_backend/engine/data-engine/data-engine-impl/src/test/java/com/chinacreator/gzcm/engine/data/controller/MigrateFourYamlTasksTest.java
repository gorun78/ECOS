package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineDefinition;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineRepository;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSaveDTO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * F02-03 / R-2 a《MigrateFourYamlTasksTest》— YAML 四端点只读化 + JSON DAG 单模型承接。
 *
 * <p><b>落点校正（相对指令口径）</b>：指令引用的 {@code EntityArchController}
 * 与 {@code migrate*} / {@code yaml*} 具名端点在当前工作树<b>不存在</b>。F02-03 真实的
 * 「YAML 停写端点面」落在 {@link PipelineTaskController}（类级
 * {@code @RequestMapping("/api/v1/engine/data/pipeline")}）的 4 个写动词端点
 * （createTask / updateTask / deleteTask / triggerRun）；它们的收敛形态 =
 * <b>409 只读桩</b>（message 首段 {@code ECOS-DATA-011}），承流迁移到
 * {@code /api/v1/pipeline/definitions}（JSON DAG 单模型，F02-03 §F02-03 收敛动作 ①③）。
 * 4 行存量 task → 4 的 node/edge 等价迁移由一次性脚本
 * {@code V168__migrate_yaml_pipeline_task.sql} 承担（DDL 侧，非本测试面）。
 *
 * <p>守三条真实不变量：
 * <ol>
 *   <li><b>反射</b>：4 个写端点方法存在、非 private、带 {@code @PostMapping/@PutMapping/
 *       @DeleteMapping} 之一，且类级 {@code @RequestMapping} 落在
 *       {@code /api/v1/engine/data/pipeline} 前缀（D.2 承流路径同源）；</li>
 *   <li><b>行为·正向</b>：{@code PipelineServiceImpl.createDefinition}（F02-03 §D.2
 *       {@code savePipelineDefinition} 守卫面）能对合法 2 节点 DAG 落库，
 *       {@code getDefinition(id)} 回读同 id；</li>
 *   <li><b>行为·负向</b>：环 A↔B 定义 → {@code createDefinition} 抛
 *       {@code ECOS-DATA-013}（400），不落库。</li>
 * </ol>
 *
 * <p>W 行：详细设计-02 §F02-03（R-2 a）/ W44。
 *
 * @author ecos-p0-data-engine
 * @date 2026-09-30
 */
@DisplayName("F02-03/R-2a：YAML 四写端点只读化 + DAG 单模型承接）")
class MigrateFourYamlTasksTest {

    /** F02-03：僵尸写端点必须挂 409 只读桩（ECOS-DATA-011）。 */
    static final String ECOS_DATA_011 = "ECOS-DATA-011";

    /** 类级路径前缀（VersionPrefixRewriteFilter 双路径 canonical 前缀）。 */
    private static final String CLASS_MAPPING_PREFIX = "/api/v1/engine/data/pipeline";

    // ─── A. 反射：四个 YAML 停写写端点存活且带写动词注解 ──────────

    @Test
    @DisplayName("四个写端点方法存在 + 非 private + 带写动词映射注解")
    void fourWriteEndpointsExistWithWriteMapping() throws Exception {
        String[][] endpoints = {
                // {methodName, hasOneOfPost/Put/Delete}
                {"createTask", "POST"},   // @PostMapping("/tasks")
                {"updateTask", "PUT"},    // @PutMapping("/tasks/{id}")
                {"deleteTask", "DELETE"}, // @DeleteMapping("/tasks/{id}")
                {"triggerRun", "POST"},   // @PostMapping("/tasks/{id}/run")
        };
        for (String[] e : endpoints) {
            Method m = findControllerMethod(e[0]);
            assertNotNull(m, "写端点方法缺失: " + e[0]);
            assertFalseSkip(m);
            assertTrue(hasWriteMapping(m),
                    "写端点 " + e[0] + " 必须带 @PostMapping/@PutMapping/@DeleteMapping 之一（409 只读桩）");
        }
    }

    @Test
    @DisplayName("类级 @RequestMapping 落在承流前缀 /api/v1/engine/data/pipeline")
    void classMappingUnderPipelinePrefix() {
        RequestMapping rm = PipelineTaskController.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "PipelineTaskController 必须带类级 @RequestMapping");
        String[] values = rm.value().length > 0 ? rm.value() : rm.path();
        assertNotNull(values);
        assertTrue(values.length > 0
                        && (values[0].startsWith(CLASS_MAPPING_PREFIX)
                            || values[0].equals("/api/engine/data/pipeline")),
                "类级映射须落在 YAML 停写承流前缀，实际: " + java.util.Arrays.toString(values));
    }

    @Test
    @DisplayName("写端点调用 → 409 只读桩，message 含 ECOS-DATA-011")
    void createTaskEndpointIsReadonly409() {
        PipelineTaskService taskService = Mockito.mock(PipelineTaskService.class);
        PipelineTaskController controller = new PipelineTaskController(taskService);
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> controller.createTask(emptyBody()));
        assertEquals(409, ex.getHttpStatus(), "写端点须 409 Conflict");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains(ECOS_DATA_011),
                "409 桩 message 首段须为 ECOS-DATA-011，实际: " + ex.getMessage());
        // 只读桩不得真正落任何 task
        Mockito.verifyNoInteractions(taskService);
    }

    // ─── B. 行为：DAG 单模型承接（createDefinition = savePipelineDefinition）──

    private PipelineServiceImpl svc(PipelineRepository repo, PipelineSecurityService sec) {
        return new PipelineServiceImpl(repo, sec);
    }

    private static PipelineSaveDTO.NodeSpec node(String id, String type) {
        PipelineSaveDTO.NodeSpec n = new PipelineSaveDTO.NodeSpec();
        n.setNodeId(id);
        n.setType(type);
        return n;
    }

    private static PipelineSaveDTO.EdgeSpec edge(String from, String to) {
        PipelineSaveDTO.EdgeSpec e = new PipelineSaveDTO.EdgeSpec();
        e.setFrom(from);
        e.setTo(to);
        return e;
    }

    private static PipelineSaveDTO validDto() {
        PipelineSaveDTO d = new PipelineSaveDTO();
        d.setName("migrated");
        d.setNodes(List.of(node("s", "SOURCE_JDBC"), node("t", "TRANSFORM_SQL")));
        d.setEdges(List.of(edge("s", "t")));
        return d;
    }

    @Test
    @DisplayName("正向：合法 2 节点 DAG 落库，getDefinition 回读同 id")
    void validDefinitionRoundTrips() {
        PipelineRepository repo = Mockito.mock(PipelineRepository.class);
        PipelineSecurityService sec = Mockito.mock(PipelineSecurityService.class);
        PipelineDefinition persisted = new PipelineDefinition();
        persisted.setId("def-9001");
        persisted.setName("migrated");
        persisted.setStatus("DRAFT");
        Mockito.when(repo.insertDefinition(any(PipelineDefinition.class))).thenReturn(persisted);

        PipelineDefinition created = assertDoesNotThrow(() -> svc(repo, sec).createDefinition(validDto()));
        assertEquals("def-9001", created.getId(), "合法定义应落库并回 id");

        // getDefinition 走 findDefinitionById — 桩成同一持久化对象，回读同 id
        Mockito.when(repo.findDefinitionById("def-9001")).thenReturn(persisted);
        PipelineDefinition read = svc(repo, sec).getDefinition("def-9001");
        assertEquals(created.getId(), read.getId(), "回读 id 须与创建 id 一致");
        // 落库后应推进节点持久化（saveNodesAndDependencies → insertNode）
        Mockito.verify(repo, Mockito.atLeastOnce())
                .insertNode(any(com.chinacreator.gzcm.engine.data.pipeline.PipelineNode.class));
    }

    @Test
    @DisplayName("负向：环 A↔B 定义 → ECOS-DATA-013(400)，不落库")
    void cycleDefinitionRejected() {
        PipelineRepository repo = Mockito.mock(PipelineRepository.class);
        PipelineSecurityService sec = Mockito.mock(PipelineSecurityService.class);
        PipelineSaveDTO d = new PipelineSaveDTO();
        d.setName("cycle");
        d.setNodes(List.of(node("a", "SOURCE_JDBC"), node("b", "TRANSFORM_SQL")));
        d.setEdges(List.of(edge("a", "b"), edge("b", "a")));

        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> svc(repo, sec).createDefinition(d));
        assertTrue(ex instanceof ValidationException, "环应走 ValidationException(400)");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("ECOS-DATA-013"),
                "须落 ECOS-DATA-013，实际: " + ex.getMessage());
        Mockito.verify(repo, Mockito.never()).insertDefinition(any(PipelineDefinition.class));
    }

    // ─── helpers ────────────────────────────────────────────────

    private static Method findControllerMethod(String name) {
        for (Method m : PipelineTaskController.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    private static Map<String, Object> emptyBody() {
        return new java.util.LinkedHashMap<>();
    }

    private static boolean hasWriteMapping(Method m) {
        return m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
    }

    /** 断言写端点方法非 private（对外端点必须 public）。 */
    private static void assertFalseSkip(Method m) {
        org.junit.jupiter.api.Assertions.assertFalse(Modifier.isPrivate(m.getModifiers()),
                "写端点须为 public 端点，实际 modifiers=" + m.getModifiers());
    }
}
