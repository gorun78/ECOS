package com.chinacreator.gzcm.engine.ai.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.dao.AipPipelineDao;
import com.chinacreator.gzcm.engine.ai.dataobject.AipPipelineDO;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineUpsertRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIP Pipeline 端点 wire 保真（DI-2 #147EC）。
 *
 * <p>Pipeline 行 7 键（执行后补 {@code lastExecutedAt} 尾键）；列表摘要 6 键（无 steps）；
 * 执行回执 4 键；删除 2 键。</p>
 *
 * <p>V329 起持久化切至 {@link AipPipelineDao}；本测试以内存 {@link InMemoryAipPipelineDao}
 * 替身驱动同一套端点，键序断言不变。</p>
 */
class AipPipelineWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PIPELINE_KEYS = List.of("id", "name", "description", "steps",
            "status", "createdAt", "updatedAt");

    private AIPPipelineController controller;

    @BeforeEach
    void setUp() {
        controller = new AIPPipelineController(
                providerOf(new InMemoryAipPipelineDao()), providerOf(null), providerOf(null));
    }

    /** 内存 DAO 替身：语义与 V329 SQL 一致（is_deleted 软删、按 update_time 倒序、last_executed_at 回写）。 */
    static class InMemoryAipPipelineDao extends AipPipelineDao {

        private final Map<String, AipPipelineDO> rows = new ConcurrentHashMap<>();

        @Override
        public int insert(AipPipelineDO pipeline) {
            rows.put(pipeline.getId(), pipeline);
            return 1;
        }

        @Override
        public Optional<AipPipelineDO> findById(String tenantId, String id) {
            AipPipelineDO row = rows.get(id);
            return row == null || isDeleted(row) || !tenantId.equals(row.getTenantId())
                    ? Optional.empty() : Optional.of(row);
        }

        @Override
        public List<AipPipelineDO> findAll(String tenantId) {
            List<AipPipelineDO> result = new ArrayList<>();
            for (AipPipelineDO row : rows.values()) {
                if (!isDeleted(row) && tenantId.equals(row.getTenantId())) result.add(row);
            }
            result.sort(Comparator.comparing(AipPipelineDO::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            return result;
        }

        @Override
        public int update(AipPipelineDO pipeline) {
            if (!rows.containsKey(pipeline.getId())) return 0;
            rows.put(pipeline.getId(), pipeline);
            return 1;
        }

        @Override
        public int softDelete(String tenantId, String id, String updateBy) {
            AipPipelineDO row = rows.get(id);
            if (row == null || isDeleted(row) || !tenantId.equals(row.getTenantId())) return 0;
            row.setIsDeleted(1);
            row.setUpdateBy(updateBy);
            return 1;
        }

        @Override
        public int markExecuted(String tenantId, String id, String status, String updateBy) {
            AipPipelineDO row = rows.get(id);
            if (row == null || isDeleted(row) || !tenantId.equals(row.getTenantId())) return 0;
            row.setStatus(status);
            row.setLastExecutedAt(java.time.LocalDateTime.now());
            row.setUpdateBy(updateBy);
            return 1;
        }

        private static boolean isDeleted(AipPipelineDO row) {
            return row.getIsDeleted() != null && row.getIsDeleted() != 0;
        }
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }

    @Test
    @DisplayName("创建/更新：7 键；steps 自由结构透传；部分更新保值")
    void createAndUpdateKeepKeys() throws Exception {
        AipPipelineUpsertRequest body = new AipPipelineUpsertRequest(
                "清洗管道", null, null, List.of(Map.of("type", "cleansing")), null, null, null);

        JsonNode created = dataOf(controller.createPipeline(body));
        assertEquals(PIPELINE_KEYS, fieldNames(created));
        assertEquals("draft", created.get("status").asText());
        assertEquals("cleansing", created.get("steps").get(0).get("type").asText());

        String id = created.get("id").asText();
        JsonNode updated = dataOf(controller.updatePipeline(id,
                new AipPipelineUpsertRequest(null, null, "ready", null, null, null, null)));
        assertEquals("ready", updated.get("status").asText());
        assertEquals("清洗管道", updated.get("name").asText(), "未出现的键保持原值");
        assertEquals(PIPELINE_KEYS, fieldNames(updated));
    }

    @Test
    @DisplayName("执行：回执 4 键；执行后行补 lastExecutedAt 尾键且状态 completed")
    void executeKeepsReceiptAndTailKey() throws Exception {
        JsonNode created = dataOf(controller.createPipeline(
                new AipPipelineUpsertRequest("管道", null, null, null, null, null, null)));
        String id = created.get("id").asText();

        JsonNode result = dataOf(controller.executePipeline(id));
        assertEquals(List.of("pipelineId", "status", "message", "executedAt"), fieldNames(result));
        assertEquals("executed", result.get("status").asText());

        JsonNode after = dataOf(controller.getPipeline(id));
        assertEquals(PIPELINE_KEYS.size() + 1, fieldNames(after).size());
        assertEquals("lastExecutedAt", fieldNames(after).get(PIPELINE_KEYS.size()));
        assertEquals("completed", after.get("status").asText());
        assertTrue(after.get("lastExecutedAt").asText().contains("T"));

        JsonNode list = MAPPER.valueToTree(controller.listPipelines()).get("data");
        assertEquals(List.of("id", "name", "description", "status", "createdAt", "updatedAt"),
                fieldNames(list.get(0)));
        assertFalse(list.get(0).has("steps"), "列表摘要不含 steps");
    }

    @Test
    @DisplayName("删除：2 键回执；二次删除 404")
    void deleteKeepsKeys() throws Exception {
        JsonNode created = dataOf(controller.createPipeline(
                new AipPipelineUpsertRequest("管道", null, null, null, null, null, null)));
        String id = created.get("id").asText();

        JsonNode deleted = dataOf(controller.deletePipeline(id));
        assertEquals(List.of("deleted", "id"), fieldNames(deleted));
        assertEquals(404, controller.deletePipeline(id).getCode());
    }

    private static JsonNode dataOf(ApiResponse<?> resp) throws Exception {
        assertEquals(0, resp.getCode(), "夹具必须走成功信封: " + resp.getMessage());
        return MAPPER.valueToTree(resp).get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
