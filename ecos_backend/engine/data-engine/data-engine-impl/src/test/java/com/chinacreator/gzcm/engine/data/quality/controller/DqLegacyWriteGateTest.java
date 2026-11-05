package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.engine.data.quality.service.DqService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * DQ legacy 写端点停用面（DI-2 #147DK）。
 *
 * <p>#147DK 把 10 个停用端点与 {@code legacyWriteBlocked} 统一改 {@code ApiResponse<Void>}
 * （请求体从未被读取，故一并删除入参）。本门钉两件事：</p>
 * <ol>
 *   <li>十腿全部 409 {@code ECOS-DATA-011}，提示指向治理端点；</li>
 *   <li>错误信封<b>不出现 {@code data} 键</b>（旧 Map 版 data 亦为 null + NON_NULL），
 *       即强类型化未改动 wire。</li>
 * </ol>
 */
class DqLegacyWriteGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DqController controller = new DqController(mock(DqService.class));

    private List<ApiResponse<Void>> blockedWrites() {
        List<ApiResponse<Void>> responses = new ArrayList<>();
        responses.add(controller.createRule());
        responses.add(controller.updateRule(1L));
        responses.add(controller.deleteRule(2L));
        responses.add(controller.createIssue());
        responses.add(controller.updateIssue(3L));
        responses.add(controller.resolveIssue(4L));
        responses.add(controller.deleteIssue(5L));
        responses.add(controller.runCheck());
        responses.add(controller.createLegacy("rules"));
        responses.add(controller.deleteLegacy("issues", 6L));
        return responses;
    }

    @Test
    @DisplayName("十腿停用端点：409 + ECOS-DATA-011 + 治理端点提示")
    void allLegacyWritesAreBlockedWithOwnerCode() {
        List<ApiResponse<Void>> responses = blockedWrites();
        assertEquals(10, responses.size(), "停用写端点面 = 10 腿（含旧版统一路径）");
        for (ApiResponse<Void> resp : responses) {
            assertEquals(409, resp.getCode());
            assertEquals(DataDomainErrorCode.ECOS_DATA_011, resp.getErrorCode());
            assertEquals("该端点已停用，请使用 /api/v1/dq/** 治理端点", resp.getMessage());
            assertNull(resp.getData(), "停用腿不带载荷");
        }
    }

    @Test
    @DisplayName("停用回执 wire：无 data 键（强类型化前后信封同形）")
    void blockedEnvelopeHasNoDataKey() throws Exception {
        JsonNode json = MAPPER.valueToTree(blockedWrites().get(0));
        Set<String> keys = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("code", "errorCode", "message", "timestamp", "success"), keys);
        assertFalse(json.has("data"));
    }
}
