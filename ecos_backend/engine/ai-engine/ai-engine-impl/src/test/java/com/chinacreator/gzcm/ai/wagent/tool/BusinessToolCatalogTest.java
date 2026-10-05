package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.tool.BusinessToolCatalog;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContract;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-17 业务工具目录离线单测：12 个种子工具齐备；
 * 每个工具 endpoint_kind=rest 且 endpoint 落在既有 {@code /api/v1/} 前缀；
 * 无通用 CRUD / 裸 SQL 工具（名称无 query_db/raw_sql，类别 ∈ 四值白名单）。
 */
class BusinessToolCatalogTest {

    private static final Set<ToolCategory> KNOWN = Set.of(
            ToolCategory.QUERY, ToolCategory.ANALYZE, ToolCategory.COMMIT, ToolCategory.AGGREGATE);

    /** F10-17：种子基线 12 个工具（append-only，禁删减）。 */
    @Test
    void initialTwelveToolsRegistered() {
        assertEquals(12, BusinessToolCatalog.INITIAL_TWELVE.size(), "基线必须恰 12 个种子工具");
        assertEquals(12, BusinessToolCatalog.toolNames().size(), "12 工具名无重复");
    }

    /** F10-17：每个工具 endpoint_kind=rest 且 endpoint_url 以 /api/v1/ 起头（既有引擎前缀）。 */
    @Test
    void everyToolMapsToEngineEndpoint() {
        for (ToolContract c : BusinessToolCatalog.INITIAL_TWELVE) {
            assertEquals("rest", c.endpointKind(), c.name() + " 的 endpointKind 必须 = rest");
            assertNotNull(c.endpointUrl(), c.name() + " 必须有 endpointUrl");
            assertTrue(c.endpointUrl().startsWith("/api/v1/"),
                    c.name() + " 的 endpointUrl 必须落既有 /api/v1/ 前缀：" + c.endpointUrl());
        }
    }

    /**
     * F10-17：不暴露通用 CRUD / 裸 SQL 工具——名称不含 query_db / raw_sql，
     * 且 category 必在四值白名单（QUERY/ANALYZE/COMMIT/AGGREGATE）。
     */
    @Test
    void noGenericCrudOrRawSqlToolExposed() {
        for (ToolContract c : BusinessToolCatalog.INITIAL_TWELVE) {
            String name = c.name().toLowerCase();
            assertFalse(name.contains("query_db"), name + " 涉嫌裸 DB 查询工具，禁暴露");
            assertFalse(name.contains("raw_sql"), name + " 涉嫌裸 SQL 工具，禁暴露");
            assertTrue(KNOWN.contains(c.category()),
                    c.name() + " 的 category " + c.category() + " 不在四值白名单");
        }
    }
}
