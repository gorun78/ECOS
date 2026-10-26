package com.chinacreator.gzcm.sysman.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.chinacreator.gzcm.sysman.security.clearance.service.impl.SubjectClearanceQueryServiceImpl;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

/**
 * 准入面机构腿的判据门（#142/B4-3e-82，收口台账 D-B4-99 b) 准入侧腿）。
 *
 * <p><b>本门盯的是「机构档接在哪个判据上」</b>：改前 {@code ClearanceInterceptor#getCurrentOrgId}
 * 经本模块 DAO 直读对方表（{@code is_primary='1' … LIMIT 1}）再取第一行，于是机构档的取数
 * <b>永远</b>发生（哪怕主体登记了两枚互异主机构，交回哪枚由数据库顺序决定）；改后取数走属主 api
 * 门面、判据走 {@code PrimaryOrganizationRule}，主机构不唯一 ⇒ 机构档整体跳过。两种形态在
 * 「恰好一枚主机构」的正常数据上读数完全一致，只有异常数据能分辨——所以本门的价值全押在异常档上：</p>
 * <ol>
 *   <li>唯一主机构 ⇒ 机构档被咨询且命中即止（不越级取全局档，同 3e-81 的短路纪律）；</li>
 *   <li><b>两枚互异主机构 ⇒ 机构档一次都不咨询</b>，判定回落到更低档——这就是「不替主体猜一个机构」
 *       的可执行形态，也是 {@link #ambiguousPrimaryOrgSkipsOrgTier} 存在的理由；</li>
 *   <li>同一机构重复登记 ⇒ 仍咨询那一枚（不唯一比对的是机构集合，不是行数）；</li>
 *   <li>无主机构行／机构号残缺 ⇒ 不咨询；</li>
 *   <li>门面取数抛错 ⇒ 保持既有 fail-soft：跳过机构档、按更低档继续判定，<b>不</b>把请求打成 500。</li>
 * </ol>
 *
 * <p>夹具档位做成可分辨的：机构档 {@code 4} ＞ 全局默认档 {@code 2}，请求打 L3 平面
 * （{@code /api/v1/system/}）。于是「咨询机构档」＝放行、「跳过机构档」＝403——
 * 断言只看两件事：准入结果与 DAO 调用面。</p>
 *
 * <p><b>本门不声称能分辨的</b>：「机构档无行」与「机构档断腿」在准入面上的可见结果相同（都下探全局档），
 * 二者的三态区分（无值 ≠ 降级）由 3e-81 的 {@code SubjectClearanceQueryServiceImplTest} 在服务侧钉；
 * 这里只钉「两种形态都不放宽档位、都不把请求打成 500」。降级事实是否记入
 * {@code SubjectClearanceFact.degradedTiers()} 属那一门的职责。</p>
 *
 * <p><b>桩形态统一 {@code doReturn/doThrow}</b>：{@code Integer} 未打桩时 Mockito 交回 {@code 0}，
 * 而 0 在密级口径（0–4）里是合法读数，会让「档位没命中」与「档位读了 0」不可分辨
 * （同一陷阱见 3e-81 的判据门注释）。租户腿与超管旁路一律钉成「咨询即红」：
 * 租户档插在机构档与全局档之间、超管旁路根本不走密级链，任一发生都会让上面的档位设计失效，
 * 与其静默走偏不如让夹具前提自己变红。</p>
 *
 * <p>验收：{@code mvn -o -f ecos_backend/pom.xml -pl services/sysman/impl/sysman-impl test
 * -Dtest='ClearanceInterceptorOrgResolutionTest'}</p>
 */
@Timeout(60)
class ClearanceInterceptorOrgResolutionTest {

    private static final String USER = "u-org-test";
    private static final String ORG_L3_PATH = "/api/v1/system/users";
    private static final Integer ORG_TIER_LEVEL = 4;
    private static final Integer GLOBAL_TIER_LEVEL = 2;

    /** 仅作 HandlerMethod 载体，无行为。 */
    static class DummyHandler {
        public void handle() {
        }
    }

    private static final HandlerMethod HANDLER = new HandlerMethod(new DummyHandler(), findHandle());

    private static java.lang.reflect.Method findHandle() {
        try {
            return DummyHandler.class.getMethod("handle");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        // 夹具前提三件：非超管（旁路会整条跳过密级链＝本门假绿）、无租户号（租户档会插在机构档与全局档之间）、
        // 主体已登录（userId 为空时拦截器根本走不到密级链，机构腿无从咨询）。
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        UserContext context = new UserContext();
        context.setUserId(USER);
        UserContext.setCurrent(context);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static UserOrganization row(String orgId, String isPrimary) {
        UserOrganization r = new UserOrganization();
        r.setUserId(USER);
        r.setOrgId(orgId);
        r.setIsPrimary(isPrimary);
        return r;
    }

    /**
     * 五档链夹具：USER 无档案行、ROLE 无配置（{@code -1}）、ORG 档 {@code 4}、TENANT 咨询即红、全局档 {@code 2}。
     * <p>机构档只在裁出机构号时才会被咨询，因此「ORG 档是否被调用」本身就是机构腿的探针；
     * USER/ROLE 两档交回「无值」形态（空行集／-1）而不是抛异常，免得夹具自己记进降级档，
     * 把「链上没有更高档」与「高档断了」这两件事混起来（那是 3e-81 的门要盯的区分）。</p>
     */
    private ClearanceLookupDao chainFixture() {
        ClearanceLookupDao dao = mock(ClearanceLookupDao.class);
        doReturn(List.of()).when(dao).queryClearanceRows(any(ClearanceLookupDao.ClearanceRowKey.class),
                anyString(), nullable(String.class));
        doReturn(-1).when(dao).highestRoleClearanceLevel(anyString(), nullable(String.class));
        doReturn(ORG_TIER_LEVEL).when(dao).orgClearanceLevel(anyString(), nullable(String.class));
        doReturn(GLOBAL_TIER_LEVEL).when(dao).globalDefaultClearanceLevel(nullable(String.class));
        doThrow(new IllegalStateException("夹具前提失效：租户号非空会让机构档与全局档之间的档位设计失效"))
                .when(dao).tenantClearanceLevel(anyString());
        return dao;
    }

    private boolean preHandle(ClearanceLookupDao dao, IOrganizationService orgService) throws Exception {
        ClearanceInterceptor interceptor =
                new ClearanceInterceptor(dao, new SubjectClearanceQueryServiceImpl(dao), orgService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ORG_L3_PATH);
        request.setRequestURI(ORG_L3_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean admitted = interceptor.preHandle(request, response, HANDLER);
        if (!admitted) {
            assertEquals(403, response.getStatus(), "判否时必须已写入 403 状态行（不许用 500 冒充拒绝）");
        }
        return admitted;
    }

    private IOrganizationService orgServiceReturning(List<UserOrganization> rows) throws Exception {
        IOrganizationService orgService = mock(IOrganizationService.class);
        doReturn(rows).when(orgService).getUserOrganizationAffiliations(anyString());
        return orgService;
    }

    @Test
    @DisplayName("恰好一枚主机构：机构档被咨询、命中即止（L3 平面放行）")
    void uniquePrimaryOrgIsConsultedAndShortCircuits() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = orgServiceReturning(List.of(row("ORG-1", "1"), row("ORG-2", "0")));

        assertTrue(preHandle(dao, orgService), "机构档读到 4 ⇒ L3 平面应放行");
        verify(dao, times(1)).orgClearanceLevel("ORG-1", null);
        verify(dao, never()).globalDefaultClearanceLevel(nullable(String.class));
    }

    @Test
    @DisplayName("两枚互异主机构：机构档一次都不咨询，判定回落低档（改前形态会咨询并被 DB 顺序决定）")
    void ambiguousPrimaryOrgSkipsOrgTier() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = orgServiceReturning(List.of(row("ORG-A", "1"), row("ORG-B", "1")));

        assertFalse(preHandle(dao, orgService),
                "主机构不唯一时禁取任一档：放行等于把「事实不完备」谎报成「事实就是它」");
        verify(dao, never()).orgClearanceLevel(anyString(), nullable(String.class));
        verify(dao, times(1)).globalDefaultClearanceLevel(null);
    }

    @Test
    @DisplayName("同一机构重复登记为主机构：仍按恰好一枚处置，机构档照常咨询")
    void duplicatedSamePrimaryOrgIsStillUnique() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = orgServiceReturning(List.of(row("ORG-1", "1"), row("ORG-1", "1")));

        assertTrue(preHandle(dao, orgService));
        verify(dao, times(1)).orgClearanceLevel("ORG-1", null);
    }

    @Test
    @DisplayName("无主机构行（只有普通隶属）：不咨询机构档，判定由全局档给出")
    void noPrimaryRowSkipsOrgTier() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = orgServiceReturning(List.of(row("ORG-1", "0"), row("ORG-2", "0")));

        assertFalse(preHandle(dao, orgService));
        verify(dao, never()).orgClearanceLevel(anyString(), nullable(String.class));
        verify(dao, times(1)).globalDefaultClearanceLevel(null);
    }

    @Test
    @DisplayName("主机构行机构号残缺：不咨询机构档（禁替主体编一个机构）")
    void blankOrgIdOnPrimaryRowSkipsOrgTier() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = orgServiceReturning(List.of(row("   ", "1")));

        assertFalse(preHandle(dao, orgService));
        verify(dao, never()).orgClearanceLevel(anyString(), nullable(String.class));
    }

    @Test
    @DisplayName("门面取数抛错＝保持既有 fail-soft：跳过机构档继续下探，不打成 500")
    void facadeFailureKeepsFailSoftAdmission() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = mock(IOrganizationService.class);
        doThrow(new RuntimeException("organization service down"))
                .when(orgService).getUserOrganizationAffiliations(anyString());

        assertFalse(preHandle(dao, orgService),
                "机构腿失败只让机构档跳过，判定仍由低档给出（全局档 2 ＜ 3 ⇒ 拒），既不放宽也不 500");
        verify(dao, never()).orgClearanceLevel(anyString(), nullable(String.class));
        verify(dao, times(1)).globalDefaultClearanceLevel(null);
    }

    @Test
    @DisplayName("机构档无档案行：下探全局档并按其读数判定（不冒充高档）")
    void orgTierWithoutProfileRowFallsThrough() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        doThrow(new EmptyResultDataAccessException("ORG 档无行", 1))
                .when(dao).orgClearanceLevel("ORG-1", null);
        IOrganizationService orgService = orgServiceReturning(List.of(row("ORG-1", "1")));

        assertFalse(preHandle(dao, orgService));
        verify(dao, times(1)).orgClearanceLevel("ORG-1", null);
        verify(dao, times(1)).globalDefaultClearanceLevel(null);
    }

    @Test
    @DisplayName("机构档断腿：仍按低档读数给出判定（既不放宽也不把请求打成 500）")
    void orgTierFetchFailureStillAdmitsLowerTier() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        doThrow(new RuntimeException("datasource down")).when(dao).orgClearanceLevel("ORG-1", null);
        IOrganizationService orgService = orgServiceReturning(List.of(row("ORG-1", "1")));

        assertFalse(preHandle(dao, orgService));
        verify(dao, times(1)).orgClearanceLevel("ORG-1", null);
        verify(dao, times(1)).globalDefaultClearanceLevel(null);
    }

    @Test
    @DisplayName("门面交回空隶属集：机构档不咨询（与「门面抛错」同一处置，但走的是判据侧的 empty 分支）")
    void emptyAffiliationsSkipOrgTier() throws Exception {
        ClearanceLookupDao dao = chainFixture();
        IOrganizationService orgService = orgServiceReturning(List.of());

        assertFalse(preHandle(dao, orgService));
        verify(dao, never()).orgClearanceLevel(anyString(), nullable(String.class));
        verify(dao, times(1)).globalDefaultClearanceLevel(null);
    }
}
