package com.chinacreator.gzcm.engine.kb.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.kb.dto.KgSyncPreviewVO;
import com.chinacreator.gzcm.engine.kb.dto.KgSyncRollbackVO;
import com.chinacreator.gzcm.engine.kb.dto.KgSyncStatusVO;
import com.chinacreator.gzcm.engine.kb.dto.KgSyncTriggerVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * KG 同步端点 wire 保真（DI-2 #147EI）。
 *
 * <p>触发回执两腿（全量 3 键 / 单类型 4 键，{@code objectType} 条件键）；状态 2 键；
 * 预览 7 键（create/update/skip 恒 0，版本列表字符串化）；回滚 3 键。</p>
 */
class KgSyncWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("触发回执：全量腿 3 键、单类型腿 4 键（status 恒为尾键）")
    void triggerKeepsDualShape() throws Exception {
        JsonNode full = MAPPER.valueToTree(new KgSyncTriggerVO("sync-1", "sync-1", null, "started"));
        assertEquals(List.of("syncId", "jobId", "status"), fieldNames(full));
        assertFalse(full.has("objectType"));

        JsonNode typed = MAPPER.valueToTree(
                new KgSyncTriggerVO("sync-2", "sync-2", "Customer", "started"));
        assertEquals(List.of("syncId", "jobId", "objectType", "status"), fieldNames(typed));
    }

    @Test
    @DisplayName("状态：objectTypes/overallStatus 两键（objectTypes 原样透传）")
    void statusKeepsKeys() throws Exception {
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put("Customer", Map.of("lastSync", "2026-11-05"));
        JsonNode data = MAPPER.valueToTree(new KgSyncStatusVO(rows, "healthy"));
        assertEquals(List.of("objectTypes", "overallStatus"), fieldNames(data));
        assertEquals("healthy", data.get("overallStatus").asText());
    }

    @Test
    @DisplayName("预览 7 键（版本列表字符串化、计数 Number 归一）；回滚 3 键")
    void previewAndRollbackKeepKeys() throws Exception {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("create", 0);
        preview.put("update", 0);
        preview.put("skip", 0);
        preview.put("ontologyId", "ALL");
        preview.put("ontologyVersions", List.of("ont-1@3"));
        preview.put("entityCount", 12);
        preview.put("relationshipCount", 8);
        JsonNode previewNode = MAPPER.valueToTree(KgSyncPreviewVO.of(preview));
        assertEquals(List.of("create", "update", "skip", "ontologyId", "ontologyVersions",
                "entityCount", "relationshipCount"), fieldNames(previewNode));
        assertEquals("ont-1@3", previewNode.get("ontologyVersions").get(0).asText());

        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put("rolledBackNodes", 3);
        rollback.put("rolledBackEdges", 2);
        rollback.put("rolledBack", 5);
        JsonNode rollbackNode = MAPPER.valueToTree(KgSyncRollbackVO.of(rollback));
        assertEquals(List.of("rolledBackNodes", "rolledBackEdges", "rolledBack"),
                fieldNames(rollbackNode));
        assertEquals(5, rollbackNode.get("rolledBack").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
