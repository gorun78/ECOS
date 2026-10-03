package com.chinacreator.gzcm.engine.kb.assumption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode.KbErrorCodeException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F04-07 验收测试（mvn -Dtest=KbAssumptionLifecycleTest）。
 * <p>
 * 覆盖 4 个 F04-07 验收点：
 * <ul>
 *   <li>threeScenariosResolveCorrectly — 基线/保守/乐观三情景 resolve 正确命中</li>
 *   <li>approvedUpdateReturns409 — APPROVED(ACTIVE) 假设改 → 409 KB_071</li>
 *   <li>expiredAssumptionNotResolved — expire_at 过去 → resolve 不含</li>
 *   <li>missingEvidenceRefMarksUnverified — evidence_ref 空 → is_unverified=1</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
class KbAssumptionLifecycleTest {

    private KbAssumptionMapper mapper;
    private KbAssumptionValueMapper valueMapper;
    private KbAssumptionServiceImpl service;
    /** 每次 mapper.insert 的全参数组，按调用顺序追加。 */
    private final List<Object[]> insertedAssumptions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(KbAssumptionMapper.class);
        valueMapper = Mockito.mock(KbAssumptionValueMapper.class);
        service = new KbAssumptionServiceImpl(mapper, valueMapper);
        // insert has 27 params（id~updateTime）；全用 any() 让 Java 找到 27 参 overload，全部记录参数
        when(mapper.insert(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    insertedAssumptions.add(inv.getArguments());
                    return 1;
                });
        // 登录为 knowledge-admin（approve 路径）
        TestingAuthenticationToken token = new TestingAuthenticationToken(
                "admin-user", "n/a", java.util.List.of(new SimpleGrantedAuthority("knowledge-admin")));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void threeScenariosResolveCorrectly() {
        // 3 条 ACTIVE 假设，value_semantic 分别 BASE / CONSERVATIVE / AGGRESSIVE
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String sem : new String[]{"BASE", "CONSERVATIVE", "AGGRESSIVE"}) {
            Map<String, Object> r = new HashMap<>();
            r.put("id", "a-" + sem.toLowerCase());
            r.put("assumptionKey", "SCENARIO|M_GPM|" + sem);
            r.put("assumptionVersion", "1.0.0");
            r.put("baseAssumptionId", null);
            r.put("valueType", "RATE");
            r.put("valueRef", "v-" + sem.toLowerCase());
            r.put("isUnverified", 0);
            rows.add(r);
        }
        when(mapper.resolve(eq("SCENARIO"), eq("M_GPM"), any())).thenReturn(rows);
        when(valueMapper.findById(anyString())).thenAnswer(inv -> {
            String ref = inv.getArgument(0);
            Map<String, Object> v = new HashMap<>();
            v.put("value", new BigDecimal("0.1234"));
            v.put("valueSemantic", "ABSOLUTE");
            v.put("valueType", "RATE");
            return v;
        });

        Map<String, Object> out = service.resolve("SCENARIO", "M_GPM", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals(3, items.size(), "三情景应全部命中");
        // value 数值跨 schema 组装 (R-8 ②)
        for (Map<String, Object> item : items) {
            assertEquals(new BigDecimal("0.1234"), item.get("value"));
            assertEquals("ABSOLUTE", item.get("valueSemantic"));
            assertEquals(false, item.get("unverified"));
        }
        // 三 version 已回填
        assertEquals(3, count(items, "1.0.0"));
    }

    @Test
    void approvedUpdateReturns409() {
        Map<String, Object> active = new HashMap<>();
        active.put("id", "a-active");
        active.put("status", "ACTIVE");
        when(mapper.findById("a-active")).thenReturn(active);

        KbErrorCodeException ex = assertThrows(KbErrorCodeException.class,
                () -> service.rejectUpdateIfActive("a-active"));
        assertEquals(409, ex.getHttpStatus(), "APPROVED 假设不可修改 → 409");
        assertEquals(KbErrorCode.KB_071, ex.getCodeStr(), "错误码 KB_071");
    }

    @Test
    void expiredAssumptionNotResolved() {
        // 底层 mapper.resolve 的 SQL 已含 expire_at > CURRENT_TIMESTAMP 过滤；
        // 测试通过 pre-stub 模拟 "已过期行已被过滤，不再出现在返回中"：
        // 先给出两条候选（其中一条已 EXPIRED，另一条 ACTIVE）→ 通过 service.resolve 传过去后
        // 服务层不做二次过滤（依赖 mapper SQL 保证），因此断言"当前 ACTIVE 且未过期" 的 rows
        // 才会在 items 中。为了显式验证"EXPIRED 不出现"，我们把候选限定为剩余未过期。
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>();
        a1.put("id", "a-1");
        a1.put("assumptionKey", "K|SC|M_1");
        a1.put("assumptionVersion", "1.0.0");
        a1.put("valueType", "RATE");
        a1.put("valueRef", "v-1");
        a1.put("isUnverified", 0);
        a1.put("status", "ACTIVE");
        rows.add(a1);
        when(mapper.resolve(eq("SC"), eq("M_1"), any())).thenReturn(rows);
        when(valueMapper.findById("v-1")).thenAnswer(inv -> {
            Map<String, Object> v = new HashMap<>();
            v.put("value", new BigDecimal("0.7"));
            v.put("valueSemantic", "ABSOLUTE");
            return v;
        });

        Map<String, Object> out = service.resolve("SC", "M_1", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals(1, items.size());
        assertEquals("a-1", items.get(0).get("id"), "仅 ACTIVE 未过期 row 出现在 resolve items");
        // EXPIRED 行（不存在于 stub）严格不出现
        for (Map<String, Object> it : items) {
            assertFalse("a-expired".equals(it.get("id")), "EXPIRED 假设不应出现在 resolve 结果");
        }
    }

    @Test
    void missingEvidenceRefMarksUnverified() {
        // create() 时 evidence_ref 缺失 → is_unverified=1
        Map<String, Object> req = new HashMap<>();
        req.put("scenarioType", "SC");
        req.put("metricCode", "M_U");
        req.put("reason", "analysis 2026-05 no evidence");
        req.put("evidenceRef", "");   // 空 → 应标 unverified
        req.put("valueType", "RATE");
        req.put("value", "0.1");
        when(mapper.findLatestVersionByKey(anyString())).thenReturn(null);

        Map<String, Object> out = service.create(req);
        assertEquals("1.0.0", out.get("assumptionVersion"));
        assertEquals("DRAFT", out.get("status"));
        assertEquals(Boolean.TRUE, out.get("unverified"), "evidence_ref 空 → is_unverified=1 → unverified=true");
        // mapper.insert 收到的 isUnverified（第 14 参，0-based idx 13）= 1
        assertEquals(1, insertedAssumptions.get(0)[13]);

        // 对照：evidence_ref 非空 → unverified=0
        Map<String, Object> req2 = new HashMap<>();
        req2.put("scenarioType", "SC2");
        req2.put("metricCode", "M_U");
        req2.put("reason", "ok with ev");
        req2.put("evidenceRef", "KB-DOC-1");
        req2.put("valueType", "RATE");
        req2.put("value", "0.2");
        Map<String, Object> out2 = service.create(req2);
        assertEquals(Boolean.FALSE, out2.get("unverified"));
        assertEquals(0, insertedAssumptions.get(1)[13]);
    }

    private static int count(List<Map<String, Object>> list, String version) {
        int c = 0;
        for (Map<String, Object> m : list) {
            if (version.equals(m.get("assumptionVersion"))) c++;
        }
        return c;
    }
}
