package com.chinacreator.gzcm.engine.cognitive2.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * schema 预检探测面的分态承载（差距 <b>D-B4-88</b> 第二枚收口件，#142/B4-3e-58）。
 *
 * <p><b>缺陷形态</b>：{@code MindAssetDao.mindTableExists} 的 {@code catch (Exception)} 顶层
 * {@code return true}，注释自称「保守：探测即失败时视为存在性未知，不上抛，由后续 SELECT 404 兜底」，
 * 与同一方法 javadoc 自述的「{@code to_regclass IS NULL → 503 ECOS-COG-501}」<b>自相矛盾</b>：
 * 探测通道故障时它把「未知」报成「表在」，随后 E1 行查询裸报错，503 前置门形同虚设。</p>
 *
 * <p><b>收口形态</b>：与同包 {@link BeliefScorer#readScoreInputs} 既有形态对齐 —— 探测故障抛
 * {@link CognitiveGuardException}（{@code ECOS-COG-501} → 503，{@code missing} 带表名供运维定位），
 * 而<b>只有真取到</b>的布尔值才走返回值。本类三条用例即「故障态／缺表态／在表态」互不可替。</p>
 */
class MindAssetDaoSchemaPreflightCarryTest {

    private static MindAssetDao daoWith(JdbcTemplate jdbcTemplate) throws Exception {
        MindAssetDao dao = new MindAssetDao();
        Field f = Class.forName("com.chinacreator.gzcm.runtime.core.database.support"
                + ".JdbcTemplateDaoSupport").getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(dao, jdbcTemplate);
        return dao;
    }

    @Test
    @DisplayName("D-B4-88②：to_regclass 探测通道故障 → 抛 ECOS-COG-501（503），禁报「表在」")
    void probeChannelFailureCarriesSchemaNotReady() throws Exception {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.queryForObject(anyString(), eq(Boolean.class), any(Object.class)))
                .thenThrow(new IllegalStateException("connection refused"));
        MindAssetDao dao = daoWith(jt);

        CognitiveGuardException thrown = assertThrows(CognitiveGuardException.class,
                dao::mindTableExists,
                "探测失败必须显式承载；return true 就是把「未知」报成「在」");
        assertEquals(CognitiveErrorCode.E501_SCHEMA_NOT_READY, thrown.errorCode,
                "机读码必须是前置门冻结码");
        assertEquals(503, thrown.httpStatus, "不可用态的状态行必须是 503");
        assertEquals(MindAssetDao.MIND_TABLE, thrown.missing,
                "missing 面须带表名，否则运维无法区分缺表与通道故障");
    }

    @Test
    @DisplayName("D-B4-88②分态：探测到「表不在」→ 如实返回 false（缺表态不走异常通道）")
    void absentTableReturnsFalseNotAnException() throws Exception {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.queryForObject(anyString(), eq(Boolean.class), any(Object.class)))
                .thenReturn(Boolean.FALSE);
        assertFalse(daoWith(jt).mindTableExists(),
                "真取到 false 就返回 false，由调用方 assertSchemaReady 落前置门");
    }

    @Test
    @DisplayName("D-B4-88②不过头：探测到「表在」→ true（合法读面不得被收口打死）")
    void presentTableReturnsTrue() throws Exception {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.queryForObject(anyString(), eq(Boolean.class), any(Object.class)))
                .thenReturn(Boolean.TRUE);
        assertTrue(daoWith(jt).mindTableExists());
    }

    @Test
    @DisplayName("D-B4-88②取证口径：queryForObject 返回 null 视为「表不在」，不当成在表")
    void nullProbeIsNotPresent() throws Exception {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.queryForObject(anyString(), eq(Boolean.class), any(Object.class)))
                .thenReturn(null);
        assertFalse(daoWith(jt).mindTableExists(),
                "Boolean.TRUE.equals(null)=false：未取到真值时不冒充「表在」");
    }
}
