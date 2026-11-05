package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IAnalyticsService;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code /api/v1/task} 族 wire 键面门（DI-2 #147CV）。
 *
 * <p>旧实现全部用 {@code Map.of(...)} 拼装（键序不确定）；改 record 后本题按**键集相等 + 键序**钉住：
 * 列表 4 键、任务 9 键、状态 6 键、详情 2 键、提交/执行/批量回执与类型目录各键。保留的 7 处
 * {@code Map<String,Object>} 是属主契约（查询条件入参、统计/健康自由结果）与自由 JSON
 * {@code params}，不以「改写属主签名」冒充清退。</p>
 *
 * <p>gateway 测试面无 Mockito ⇒ 用 {@link Proxy} 假面（只按方法名返回夹具，其余 null）。</p>
 */
class TaskControllerWireShapeTest {

    private static final List<String> TASK_KEYS = List.of(
            "taskId", "taskName", "taskType", "category", "description", "priority",
            "createTime", "createdBy", "parameters");

    private static final List<String> STATUS_KEYS = List.of(
            "taskId", "status", "statusMessage", "progress", "startedAt", "completedAt");

    private TaskController controller;

    @BeforeEach
    void setUp() {
        ITaskManagementService taskService = fakeTaskService();
        IAnalyticsService analytics = (IAnalyticsService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IAnalyticsService.class},
                (proxy, method, args) -> null);
        controller = new TaskController();
        ReflectionTestUtils.setField(controller, "taskManagementService", taskService);
        ReflectionTestUtils.setField(controller, "analyticsService", analytics);
    }

    private static ITaskManagementService fakeTaskService() {
        return (ITaskManagementService) Proxy.newProxyInstance(
                TaskControllerWireShapeTest.class.getClassLoader(),
                new Class<?>[]{ITaskManagementService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "queryTasks" -> List.of(task());
                    case "getTaskDescription" -> task();
                    case "getTaskStatus" -> status();
                    case "submitTask" -> "t9";
                    case "executeTask" -> null;
                    default -> null;
                });
    }

    private static TaskDescription task() {
        TaskDescription t = new TaskDescription();
        t.setTaskId("t1");
        t.setTaskName("夜间同步");
        t.setTaskType("ETL");
        t.setDescription("同步");
        t.setPriority(2);
        t.setCreateTime(new Date(0));
        t.setCreatedBy("u1");
        t.setParameters(Map.of("k", "v"));
        return t;
    }

    private static TaskStatus status() {
        TaskStatus s = new TaskStatus();
        s.setTaskId("t1");
        s.setStatus(TaskStatus.Status.SUCCEEDED);
        s.setStatusMessage("ok");
        s.setProgress(100);
        s.setStartTime(new Date(0));
        s.setEndTime(new Date(1000));
        return s;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode data(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("列表：4 键（items/total/offset/limit），任务条目 9 键且 category 由类型派生")
    void listShape() throws Exception {
        JsonNode data = data(controller.listTasks(0, 20, null, null, null).getData());

        assertEquals(List.of("items", "total", "offset", "limit"), keys(data));
        assertEquals(TASK_KEYS, keys(data.path("items").get(0)));
        assertEquals("pipeline", data.path("items").get(0).path("category").asText());
        assertEquals(new Date(0).toString(), data.path("items").get(0).path("createTime").asText());
    }

    @Test
    @DisplayName("详情：2 键（task/status），状态条目 6 键且 status 取枚举名")
    void detailShape() throws Exception {
        JsonNode data = data(controller.getTask("t1").getData());

        assertEquals(List.of("task", "status"), keys(data));
        assertEquals(TASK_KEYS, keys(data.path("task")));
        assertEquals(STATUS_KEYS, keys(data.path("status")));
        assertEquals("SUCCEEDED", data.path("status").path("status").asText());
    }

    @Test
    @DisplayName("提交/执行/操作回执键集：2/2/1 键，异步执行如实标注")
    void mutationReceiptShapes() throws Exception {
        assertEquals(List.of("taskId", "status"),
                keys(data(controller.submitTask(new TaskController.SubmitTaskRequest(
                        "n", "ETL", "d", Map.of(), null)).getData())));
        assertEquals(List.of("taskId", "result"),
                keys(data(controller.executeTask("t9").getData())));
        assertEquals("ASYNC_EXECUTING",
                data(controller.executeTask("t9").getData()).path("result").asText());
        assertEquals(List.of("message"),
                keys(data(controller.cancelTask("t9").getData())));
    }

    @Test
    @DisplayName("批量操作：2 键（successCount/errors），未知动作计入 errors 不改计数")
    void batchShape() throws Exception {
        ApiResponse<TaskController.BatchResultVO> resp = controller.batchOperation(
                new TaskController.BatchOperationRequest(List.of("t1"), "ghost"));
        JsonNode data = data(resp.getData());

        assertEquals(List.of("successCount", "errors"), keys(data));
        assertEquals(0, data.path("successCount").asInt());
        assertEquals("Unknown action: ghost", data.path("errors").get(0).asText());
    }

    @Test
    @DisplayName("类型目录：1 键 + 分类条目 3 键，四类与类型清单不漂")
    void typesShape() throws Exception {
        JsonNode data = data(controller.taskTypes().getData());

        assertEquals(List.of("categories"), keys(data));
        assertEquals(List.of("key", "label", "types"), keys(data.path("categories").get(0)));
        assertEquals(4, data.path("categories").size());
        assertEquals("pipeline", data.path("categories").get(0).path("key").asText());
        assertEquals(5, data.path("categories").get(0).path("types").size());
    }
}
