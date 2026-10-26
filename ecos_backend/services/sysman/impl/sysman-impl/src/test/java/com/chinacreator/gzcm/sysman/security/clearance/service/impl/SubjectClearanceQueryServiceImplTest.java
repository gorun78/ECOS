package com.chinacreator.gzcm.sysman.security.clearance.service.impl;

import com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao;
import com.chinacreator.gzcm.sysman.security.clearance.model.SubjectClearanceFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * #142/B4-3e-81（差距 D-B4-23 余项）— 属主侧五档密级链单源的判据门。
 *
 * <p>本门盯的四件事，每一件都是收口前真实存在的形态：</p>
 * <ol>
 *   <li><b>档序与短路</b>：USER&gt;ROLE&gt;ORG&gt;TENANT&gt;GLOBAL 命中即止，不越级取高
 *       （命中后仍向下取数会把手工设的低档悄悄被机构/租户高档覆盖）；</li>
 *   <li><b>无档案 ≠ 断腿</b>：某档没行就只是没行；取数抛错才进 {@code degradedTiers}。
 *       收口前两者都被同一个 {@code catch (Exception)} 静默吞掉，消费方看不出链断过；</li>
 *   <li><b>禁兜底成任何档位</b>：五档查尽交付 {@code level=null} 的未解析事实——
 *       旧实现在这里 {@code return 1}，把「没配档案」冒充成「L1 内部」，
 *       这是本批唯一的行为面改动，也是下面 {@code fiveTiersAllMissYieldsNoLevel} 存在的理由；</li>
 *   <li><b>机构档只在拿到机构号时才查</b>：机构号由调用方传入，属主侧不自行解析机构，
 *       以免造出第二套「恰好一枚主机构」判据（#142/B4-3e-82 起，准入面与策略面共用
 *       {@code PrimaryOrganizationRule} 这一份判据，本条钉住「本服务不解析机构」的边界）；</li>
 *   <li><b>USER 档取数只能走白名单条目</b>：#142/B4-3e-82 把 DAO 的 {@code (tableName, idColumn)}
 *       两个 {@code String} 参换成 {@code ClearanceRowKey} 具名白名单，本门用
 *       {@code eq(ClearanceRowKey.USER_PROFILE)} 钉住实际交付的条目——改回字符串拼装即编译不过，
 *       换成别的条目即本门变红。</li>
 * </ol>
 *
 * <p>夹具全部用 {@code doReturn/doThrow} 形态而非 {@code when(...)}：默认替身把每一档钉成
 * 「抛 {@code EmptyResultDataAccessException}」，用 {@code when(dao.x())} 再打桩会先真的调用一次
 * 替身、被上一轮的 throw 打断（本门第一版就栽在这里，报的是「EmptyResultDataAccess no row」
 * 而不是断言失败——失败形态与被测判据无关，取证时会误读成生产链断了）。</p>
 *
 * <p>验收：{@code mvn -o -f ecos_backend/pom.xml -pl services/sysman/impl/sysman-impl test
 * -Dtest='SubjectClearanceQueryServiceImplTest'}</p>
 */
@Timeout(60)
class SubjectClearanceQueryServiceImplTest {

    private static final String USER = "u-1";
    private static final String TENANT = "T1";
    private static final String EMPTY_ROW = "no row";

    private final ClearanceLookupDao dao = mock(ClearanceLookupDao.class);
    private final SubjectClearanceQueryServiceImpl service = new SubjectClearanceQueryServiceImpl(dao);

    /**
     * 默认替身形态＝「五档都没档案行」。
     * <p>两层都要钉住，否则判据会假绿：</p>
     * <ol>
     *   <li><b>Mockito 对 {@code Integer} 返回值给的是 {@code 0} 不是 {@code null}</b>，而 0 在密级列
     *       口径（0–4）里是一枚合法档位读数——不显式钉死，未解析判据会被这枚 0 顶成 TENANT 命中；</li>
     *   <li><b>真实属主侧「无行」的形态是 {@code queryForObject} 抛
     *       {@code EmptyResultDataAccessException}</b>，所以「没档案」用同型异常表达，
     *       不用 {@code thenReturn(null)}（后者是「DAO 契约里没有的形态」，会把「取到 null」
     *       这种应答异常洗成正常下探）。</li>
     * </ol>
     */
    @BeforeEach
    void noProfileRowOnAnyTier() {
        doReturn(List.of()).when(dao)
                .queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any());
        doReturn(-1).when(dao).highestRoleClearanceLevel(anyString(), any());
        doThrow(emptyRow()).when(dao).orgClearanceLevel(anyString(), any());
        doThrow(emptyRow()).when(dao).tenantClearanceLevel(anyString());
        doThrow(emptyRow()).when(dao).globalDefaultClearanceLevel(any());
    }

    /** USER 档有档案行的夹具（其它档保持「没行」的默认替身形态）。 */
    private void userRow(Object level) {
        doReturn(level == null ? List.of(Map.of()) : List.of(Map.of("clearance_level", level)))
                .when(dao).queryClearanceRows(
                        eq(ClearanceLookupDao.ClearanceRowKey.USER_PROFILE), eq(USER), any());
    }

    private static EmptyResultDataAccessException emptyRow() {
        return new EmptyResultDataAccessException(EMPTY_ROW, 1);
    }

    @Test
    @DisplayName("USER 档命中即止：低档存在时一次都不向下取数")
    void userTierWinsAndShortCircuits() {
        userRow(Integer.valueOf(4));

        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, "ORG-1");

        assertEquals(4, fact.level().intValue(), String.valueOf(fact.level()));
        assertEquals(SubjectClearanceFact.TIER_USER, fact.tier());
        assertTrue(fact.isResolved());
        assertFalse(fact.isDegraded(), "链上没断过腿就不该带降级标记: " + fact.degradedTiers());
        verify(dao, never()).highestRoleClearanceLevel(anyString(), any());
        verify(dao, never()).orgClearanceLevel(anyString(), any());
    }

    @Test
    @DisplayName("USER 无档案行 ⇒ 下探 ROLE 档并按其读数命中")
    void roleTierIsReachedOnlyWhenUserTierMisses() {
        doReturn(2).when(dao).highestRoleClearanceLevel(USER, TENANT);

        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, null);

        assertEquals(2, fact.level().intValue());
        assertEquals(SubjectClearanceFact.TIER_ROLE, fact.tier());
        assertTrue(fact.isResolved());
        assertEquals(List.of(), fact.degradedTiers());
    }

    @Test
    @DisplayName("ROLE 档的 -1 是「无配置」读数，不是断腿也不参与档位比对")
    void negativeRoleReadingIsAMissNotADegradedTier() {
        doReturn(1).when(dao).globalDefaultClearanceLevel(TENANT);

        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, null);

        assertEquals(SubjectClearanceFact.TIER_GLOBAL, fact.tier());
        assertEquals(List.of(), fact.degradedTiers(), "-1＝该档无配置，禁记成断腿");
    }

    @Test
    @DisplayName("机构号在场才查 ORG 档；机构未解析时该档整体跳过（不记降级、不猜机构）")
    void orgTierIsSkippedWhenSubjectHasNoResolvedOrganization() {
        SubjectClearanceFact withOrg = service.resolveSubjectClearance(USER, TENANT, "  ");

        verify(dao, never()).orgClearanceLevel(anyString(), any());
        assertEquals(List.of(), withOrg.degradedTiers(), "没机构号就不是「查不到」，禁记成断腿");

        service.resolveSubjectClearance(USER, TENANT, "ORG-9");
        verify(dao).orgClearanceLevel("ORG-9", TENANT);
    }

    @Test
    @DisplayName("EmptyResult＝该档无档案行，链继续向下且不记降级")
    void emptyResultIsAnOrdinaryMissOnScopedTier() {
        doThrow(emptyRow()).when(dao).orgClearanceLevel("ORG-9", TENANT);
        doReturn(3).when(dao).tenantClearanceLevel(TENANT);

        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, "ORG-9");

        assertEquals(3, fact.level().intValue());
        assertEquals(SubjectClearanceFact.TIER_TENANT, fact.tier());
        assertEquals(List.of(), fact.degradedTiers(), "无行不是断腿——把它记成降级会让策略面白白转 503");
    }

    @Test
    @DisplayName("取数抛错＝该档断腿，如实记进 degradedTiers 交消费方裁决")
    void fetchFailureIsRecordedAsDegradedTier() {
        doThrow(new RuntimeException("datasource down")).when(dao)
                .queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any());
        doReturn(2).when(dao).highestRoleClearanceLevel(USER, TENANT);

        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, null);

        assertEquals(2, fact.level().intValue());
        assertEquals(SubjectClearanceFact.TIER_ROLE, fact.tier());
        assertEquals(List.of(SubjectClearanceFact.TIER_USER), fact.degradedTiers(),
                "命中了下探档也必须交付「USER 档断过」这一事实，否则消费方无法判信不信这个读数");
        assertTrue(fact.isDegraded());
    }

    @Test
    @DisplayName("列值形态不可解析＝断腿，禁猜一个数字或当没这行")
    void unparsableColumnValueIsDegradedNotGuessed() {
        userRow("机密");

        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, null);

        assertFalse(fact.isResolved(), "非数字读数不能算命中");
        assertEquals(List.of(SubjectClearanceFact.TIER_USER), fact.degradedTiers());
    }

    @Test
    @DisplayName("五档全无可读档案 ⇒ 未解析事实：level=null（禁照抄准入面的兜底 L1）")
    void fiveTiersAllMissYieldsNoLevel() {
        SubjectClearanceFact fact = service.resolveSubjectClearance(USER, TENANT, null);

        assertNull(fact.level(),
                "「没配档案」必须如实交未解析；返回 1 就是冒充 L1 内部（收口前的 return 1 形态）");
        assertFalse(fact.isResolved());
        assertEquals(SubjectClearanceFact.TIER_NONE, fact.tier(), "tier 不得为 null，消费方要能直读「哪档都没中」");
        assertEquals(List.of(), fact.degradedTiers(), "没行不等于断腿，两者形态必须可分辨");
    }

    @Test
    @DisplayName("租户号缺失时跳过 TENANT 档：不给属主一次注定无行的取数")
    void blankTenantSkipsTenantTier() {
        service.resolveSubjectClearance(USER, null, null);

        verify(dao, never()).tenantClearanceLevel(anyString());
    }

    @Test
    @DisplayName("无主体：不取数、不伪造，交付 noSubject 形态")
    void blankSubjectYieldsNoFact() {
        SubjectClearanceFact fact = service.resolveSubjectClearance("  ", TENANT, "ORG-1");

        assertFalse(fact.isResolved());
        assertNull(fact.level());
        verify(dao, never()).queryClearanceRows(
                any(ClearanceLookupDao.ClearanceRowKey.class), anyString(), any());
    }
}
