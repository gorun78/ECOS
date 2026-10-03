package com.chinacreator.gzcm.engine.kb.profile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.chinacreator.gzcm.engine.kb.profile.CuratedFactSource.CuratedFactRow;
import com.chinacreator.gzcm.engine.kb.profile.KbProfileServiceImpl.GenerationResult;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode.KbErrorCodeException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F04-06 验收测试（mvn -Dtest=KbProfileGenerateTest）。
 * <p>纯 Mockito，无 @SpringBootTest：mapper/factSource 全 mock，Service 走真实算法路径。
 * 用 thenAnswer 记录 profile.insert 的全参，直接按列断言（避免 27 参 positional verify 的脆弱性）。
 *
 * @author ECOS KB Team
 */
class KbProfileGenerateTest {

    private KbProfileMapper profileMapper;
    private KbProfileStatsMapper statsMapper;
    private CuratedFactSource factSource;
    private KbProfileServiceImpl service;
    /** 每次 profile.insert 的完整参数数组，按调用顺序追加。 */
    private final List<Object[]> insertedProfiles = new ArrayList<>();
    private int statsInsertCount = 0;

    @BeforeEach
    void setUp() {
        profileMapper = Mockito.mock(KbProfileMapper.class);
        statsMapper = Mockito.mock(KbProfileStatsMapper.class);
        factSource = Mockito.mock(CuratedFactSource.class);
        service = new KbProfileServiceImpl(profileMapper, statsMapper, factSource, null);

        // insert 全参记录（27 参，profileMapper.insert）
        when(profileMapper.insert(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    insertedProfiles.add(inv.getArguments());
                    return 1;
                });
        when(statsMapper.insert(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    statsInsertCount++;
                    return 1;
                });
        // findLatestVersionByKey 默认无历史版本 → 首个版本 1.0.0
        when(profileMapper.findLatestVersionByKey(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginKnowledgeAdmin() {
        TestingAuthenticationToken token = new TestingAuthenticationToken(
                "admin-user", "n/a", java.util.List.of(new SimpleGrantedAuthority("knowledge-admin")));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private ProfileGenerateRequest req(String metric) {
        ProfileGenerateRequest r = new ProfileGenerateRequest();
        r.setMetricCodes(Collections.singletonList(metric));
        r.setDims(new HashMap<>());
        r.setWindowFrom("2025-01");
        r.setWindowTo("2026-12");
        r.setDryRun(false);
        return r;
    }

    private static int metricIdx() { return 2; }
    private static int confidenceIdx() { return 9; }
    private static int degradeFromIdx() { return 10; }
    private static int versionIdx() { return 14; }
    private static int statusIdx() { return 15; }
    private static int approvedByIdx() { return 16; }

    @Test
    void groupedByTypeDeptStageWithHighConfidence() {
        // 40 条完整四维样本 → grain 0（项目×部门×环节）n=40 ≥ 30 → HIGH
        List<CuratedFactRow> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add(new CuratedFactRow(BigDecimal.TEN.add(BigDecimal.valueOf(i)), "P1", "接口类", "交付", "建设"));
        }
        when(factSource.fetch(anyString(), anyString(), anyString(), anyString())).thenReturn(rows);

        GenerationResult r = service.generateSync(req("M_RR"), false);

        assertEquals(1, r.generated);
        assertEquals(0, r.degraded);
        assertEquals(0, r.insufficient);
        assertEquals(0, r.errorCodes.size());
        assertEquals(1, statsInsertCount, "R-8 ②：数值统计事实应落 ecos_dw 一次");
        assertEquals(1, insertedProfiles.size());

        Object[] row = insertedProfiles.get(0);
        assertEquals("M_RR", row[metricIdx()]);
        assertEquals(KbProfileServiceImpl.CONF_HIGH, row[confidenceIdx()], "n=40 → HIGH");
        assertEquals(null, row[degradeFromIdx()], "grain 0 无上游退化源");
        assertEquals("1.0.0", row[versionIdx()], "无历史版本 → 1.0.0");
        assertEquals(KbProfileServiceImpl.STATUS_DRAFT, row[statusIdx()]);
        assertNull(row[approvedByIdx()]); // DRAFT → approvedBy 尚未落值
    }

    @Test
    void insufficientSampleDegradesAndRecordsDegradeFrom() {
        // project=null、dept=null → grain 0/1 缺维直接 n=0；ptype=APP、stage=建设 → grain 2 命中 n=12 → MEDIUM
        List<CuratedFactRow> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rows.add(new CuratedFactRow(BigDecimal.valueOf(i + 1), null, "APP", null, "建设"));
        }
        when(factSource.fetch(anyString(), anyString(), anyString(), anyString())).thenReturn(rows);

        GenerationResult r = service.generateSync(req("M_RR"), false);

        assertEquals(1, r.generated);
        assertEquals(1, r.degraded, "grain 0 → grain 2 命中，退化 1 级");
        assertEquals(0, r.insufficient);

        Object[] row = insertedProfiles.get(0);
        assertEquals(KbProfileServiceImpl.CONF_MEDIUM, row[confidenceIdx()], "n=12 → MEDIUM");
        assertEquals("项目类型×部门×环节", row[degradeFromIdx()],
                "grain idx2 命中 → degradeFrom 记录上一级（idx1）可读名");
    }

    @Test
    void republishCreatesNewVersionAndSupersedesOld() {
        loginKnowledgeAdmin();
        List<CuratedFactRow> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add(new CuratedFactRow(BigDecimal.TEN, "P1", "接口类", "交付", "建设"));
        }
        when(factSource.fetch(anyString(), anyString(), anyString(), anyString())).thenReturn(rows);

        // 第一次生成 → 1.0.0
        GenerationResult first = service.generateSync(req("M_RR"), false);
        assertEquals(1, first.generated);
        assertEquals("1.0.0", insertedProfiles.get(0)[versionIdx()]);

        // 发布第一版（DRAFT→PUBLISHED）
        Map<String, Object> draft1 = new HashMap<>();
        draft1.put("id", "id-1");
        draft1.put("profileKey", "M_RR");
        draft1.put("status", KbProfileServiceImpl.STATUS_DRAFT);
        when(profileMapper.findById("id-1")).thenReturn(draft1);
        when(profileMapper.markPublished(eq("id-1"), eq("admin-user"), any(), eq("admin-user"), any()))
                .thenReturn(1);
        service.publish("id-1", "admin-user");
        Mockito.verify(profileMapper, Mockito.times(1)).supersedePublishedByKey(eq("M_RR"), eq("admin-user"), any());

        // 第二次生成：已存在 1.0.0 → 1.0.1
        Map<String, Object> latest = new HashMap<>();
        latest.put("profileVersion", "1.0.0");
        when(profileMapper.findLatestVersionByKey(anyString())).thenReturn(latest);
        GenerationResult second = service.generateSync(req("M_RR"), false);
        assertEquals(1, second.generated);
        assertEquals("1.0.1", insertedProfiles.get(1)[versionIdx()], "版本应 patch+1 单调递增（I-6）");

        // 发布第二版时，第一版 PUBLISHED 应被置 SUPERSEDED
        Map<String, Object> draft2 = new HashMap<>();
        draft2.put("id", "id-2");
        draft2.put("profileKey", "M_RR");
        draft2.put("status", KbProfileServiceImpl.STATUS_DRAFT);
        when(profileMapper.findById("id-2")).thenReturn(draft2);
        when(profileMapper.markPublished(eq("id-2"), eq("admin-user"), any(), eq("admin-user"), any()))
                .thenReturn(1);
        service.publish("id-2", "admin-user");
        Mockito.verify(profileMapper, Mockito.times(2)).supersedePublishedByKey(eq("M_RR"), eq("admin-user"), any());
    }

    @Test
    void publishedProfileUpdateIsRejected() {
        Map<String, Object> published = new HashMap<>();
        published.put("id", "id-pub");
        published.put("profileKey", "M_RR");
        published.put("status", KbProfileServiceImpl.STATUS_PUBLISHED);
        when(profileMapper.findById("id-pub")).thenReturn(published);

        KbErrorCodeException ex = assertThrows(KbErrorCodeException.class,
                () -> service.rejectUpdateIfPublished("id-pub"));
        assertEquals(409, ex.getHttpStatus(), "I-6 已发布资产不可变 → 409");
        assertEquals(KbErrorCode.KB_061, ex.getCodeStr(), "错误码 KB_061");
    }
}
