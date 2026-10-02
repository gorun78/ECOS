package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-14 / C160 向导"选已有场景"服务端过滤 — {@code ScenarioService#list(purpose)}。
 *
 * <p>设计卷 §八 验收矩阵（REQ-WS-01 行）点名
 * {@code ScenarioListFilterTest#onlyActiveIslandFreeScenariosAreListedForWizard}：
 * {@code ?purpose=forecast} 应按 R-27① 口径 {@code status=ACTIVE ∧ islands 为空} 过滤，
 * 其余/空 purpose 走全量（既有行为不变）。</p>
 *
 * <p>本过滤是<b>纯内存谓词</b>（对 {@code list()} 结果 stream 过滤），
 * 不依赖 PG。测试用桩子类覆写只读 {@code list()} 注入固定基本集，
 * 把 {@code list(purpose)} 的判据隔离出来可离线证伪 —— 无需 JdbcTemplate / MockMvc。
 * 若有人把过滤条件写成 {@code status=ACTIVE} 单条件（丢掉 islands 判据），
 * {@code bActiveWithIslands} 混入 ⇒ {@code #forecastExcludesIsolatedEvenWhenActive} 红。</p>
 */
class ScenarioListFilterTest {

    /** 覆写零参 list() 返回固定基本集；list(purpose) 内部调 list() 再过滤，路径完整穿过被测代码。 */
    private final ScenarioService svc = new ScenarioService(null, null, null) {
        @Override
        public List<ScenarioVO> list() {
            return List.of(
                    vo("sc-active-clean",   "ACTIVE",    List.of()),
                    vo("sc-active-island",  "ACTIVE",    List.of(new CompletenessVO.Island("b1", "孤立资源", "DATASET", "indeg+outdeg=0"))),
                    vo("sc-draft-clean",    "DRAFT",     List.of()),
                    vo("sc-completed",      "COMPLETED", List.of()),
                    vo("sc-active-nisland", "ACTIVE",    null));
        }
    };

    private static ScenarioVO vo(String id, String status, List<CompletenessVO.Island> islands) {
        ScenarioVO v = new ScenarioVO();
        v.setId(id);
        v.setStatus(status);
        v.setIslands(islands);
        return v;
    }

    private static java.util.Set<String> ids(List<ScenarioVO> vos) {
        return vos.stream().map(ScenarioVO::getId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Test
    @DisplayName("purpose=forecast → 只留 ACTIVE 且 islands 为空（null 视同空）")
    void onlyActiveIslandFreeScenariosAreListedForWizard() {
        java.util.Set<String> kept = ids(svc.list("forecast"));

        assertTrue(kept.contains("sc-active-clean"), "ACTIVE + 无孤岛 应保留");
        assertTrue(kept.contains("sc-active-nisland"), "ACTIVE + islands=null 应保留（null 视同空）");
        assertTrue(!kept.contains("sc-active-island"), "ACTIVE 但存在孤岛 应排除（R-27① 双条件）");
        assertTrue(!kept.contains("sc-draft-clean"), "DRAFT 即使无孤岛 也应排除");
        assertTrue(!kept.contains("sc-completed"), "COMPLETED 应排除");
        assertEquals(2, kept.size(), "基本集 5 项，forecast 只应留 2 项");
    }

    @Test
    @DisplayName("forecastExcludesIsolatedEvenWhenActive — 反例：仅 ACTIVE 不够，孤岛必排")
    void forecastExcludesIsolatedEvenWhenActive() {
        java.util.Set<String> kept = ids(svc.list("forecast"));
        assertTrue(!kept.contains("sc-active-island"),
                "若过滤漏掉 islands 判据仅有 status=ACTIVE，此 ACTIVE+孤岛 项会混入 ⇒ 本断言红");
    }

    @Test
    @DisplayName("null / 空白 / 非 forecast purpose → 走全量列表（既有行为不变）")
    void nonForecastPurposeReturnsFullList() {
        assertEquals(5, svc.list(null).size(), "purpose=null → 全量");
        assertEquals(5, svc.list("").size(), "purpose=空串 → 全量");
        assertEquals(5, svc.list("   ").size(), "purpose=空白 → 全量");
        assertEquals(5, svc.list("diagnose").size(), "purpose=非 forecast → 全量");
    }

    @Test
    @DisplayName("purpose=forecast 大小写不敏感（服务内 equalsIgnoreCase）")
    void forecastCaseInsensitive() {
        assertEquals(2, svc.list("Forecast").size());
        assertEquals(2, svc.list("FORECAST").size());
    }
}
