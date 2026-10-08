package com.chinacreator.gzcm.engine.cognitive2.scenario;

import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefReader;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefScorer;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisReader;
import com.chinacreator.gzcm.engine.cognitive2.dao.MindAssetDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.ScenarioDao;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.DomainAliasResolver;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.ScenarioCognitiveQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F05-02 schema 预检门禁（E1 数据源"从不存在建成"）：
 * {@code to_regclass} 探测必须发生在 E1 行查询<b>之前</b>；缺表 → {@code ECOS-COG-501}
 * （区别于引擎未启用，REQ-COG-01 §1.2 末行），且不触达数据查询（禁裸查报错掩蔽）。
 */
class CognitiveSchemaPreflightTest {

    private MindAssetDao mindDao;
    private CognitiveConfigQueryService cfg;
    private ScenarioCognitiveQueryService service;

    @BeforeEach
    void setUp() {
        mindDao = mock(MindAssetDao.class);
        cfg = mock(CognitiveConfigQueryService.class);
        service = new ScenarioCognitiveQueryService(mindDao, mock(ScenarioDao.class),
                mock(BeliefScorer.class), mock(HypothesisReader.class), mock(BeliefReader.class),
                new DomainAliasResolver(cfg), cfg);
    }

    @Test
    @DisplayName("mind 属主表在 E1 数据查询之前完成 to_regclass 预检；预检失败不落行查询")
    void mindTablesResolveBeforeE1() {
        when(mindDao.mindTableExists()).thenReturn(true);
        when(mindDao.detectActiveMindsForScenario("sc9")).thenReturn(List.of());
        service.detect("sc9");
        var ordered = inOrder(mindDao);
        ordered.verify(mindDao).mindTableExists();
        ordered.verify(mindDao).detectActiveMindsForScenario("sc9");

        ScenarioCognitiveQueryService svc2 = new ScenarioCognitiveQueryService(mindDao2(false),
                mock(ScenarioDao.class), mock(BeliefScorer.class), mock(HypothesisReader.class),
                mock(BeliefReader.class), new DomainAliasResolver(cfg), cfg);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> svc2.detect("sc9"));
        org.junit.jupiter.api.Assertions.assertEquals(
                com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.E501_SCHEMA_NOT_READY, e.errorCode);
        org.junit.jupiter.api.Assertions.assertEquals(503, e.httpStatus);
    }

    private MindAssetDao mindDao2(boolean exists) {
        MindAssetDao d = mock(MindAssetDao.class);
        when(d.mindTableExists()).thenReturn(exists);
        return d;
    }
}
