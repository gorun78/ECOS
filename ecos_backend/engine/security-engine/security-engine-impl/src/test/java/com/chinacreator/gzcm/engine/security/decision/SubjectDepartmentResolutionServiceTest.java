package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.sysman.iam.entity.OrgPermission;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-80（差距 D-B4-23 主体域腿）— 主体机构真值解析单源的判定门。
 *
 * <p>本门验的是「判定纪律」，不是夹具是否凑齐：属主侧只交付关联行多重性，
 * 唯一／歧义／残缺／不可用四种形态的<b>分别</b>必须在这里成立。尤其禁两类伪绿：
 * ① 把「多枚主机构」当「取第一条」判成有值（那正是 {@code PIPImpl#orgs.get(0)} 的洞）；
 * ② 把「属主取数失败」判成 {@code null}（那是把部署缺陷谎报成用户没机构，§4.7.2 三态塌陷）。</p>
 */
@Timeout(60)
class SubjectDepartmentResolutionServiceTest {

    private static UserOrganization row(String orgId, String isPrimary) {
        UserOrganization u = new UserOrganization();
        u.setUserId("u1");
        u.setOrgId(orgId);
        u.setIsPrimary(isPrimary);
        return u;
    }

    /**
     * 属主夹具基座：只交付关联行读面，其余属主方法一律显式拒绝。
     *
     * <p>存在理由有两层。① 可读性——本门只关心 {@code getUserOrganizationAffiliations}；
     * ② <b>读面收窄的见证</b>——夹具一旦改走 {@code getUserOrganizations}（那条把
     * {@code is_primary} 只用作 {@code ORDER BY}、投影里没有它），这里立刻抛红，
     * 逼出「消费方分不出 0 枚与 2 枚主机构 ⇒ 只能取其一」的旧形态复现。</p>
     */
    private abstract static class FacadeStub implements IOrganizationService {
        @Override
        public Organization createOrganization(String orgName, String orgCode, String parentOrgId,
                                               String orgType, String description, String createdBy) {
            throw unsupported();
        }

        @Override
        public Organization getOrganization(String orgId) {
            throw unsupported();
        }

        @Override
        public Organization updateOrganization(String orgId, String orgName, String orgCode,
                                               String orgType, String description, String updatedBy) {
            throw unsupported();
        }

        @Override
        public void deleteOrganization(String orgId) {
            throw unsupported();
        }

        @Override
        public Organization getOrganizationTree(String rootOrgId) {
            throw unsupported();
        }

        @Override
        public List<Organization> getChildren(String parentOrgId) {
            throw unsupported();
        }

        @Override
        public List<Organization> listOrganizations(String orgType, String status) {
            throw unsupported();
        }

        @Override
        public void assignUserToOrg(String userId, String orgId, boolean isPrimary, String createdBy) {
            throw unsupported();
        }

        @Override
        public void removeUserFromOrg(String userId, String orgId) {
            throw unsupported();
        }

        @Override
        public List<Organization> getUserOrganizations(String userId) {
            throw new UnsupportedOperationException(
                    "本批禁走 is_primary 不出投影的读面（消费方只能取其一）");
        }

        @Override
        public void setPrimaryOrg(String userId, String orgId) {
            throw unsupported();
        }

        @Override
        public OrgPermission createOrgPermission(String orgId, String resourceId, String action,
                                                 boolean inheritFromParent, String createdBy) {
            throw unsupported();
        }

        @Override
        public List<OrgPermission> getOrgPermissions(String orgId) {
            throw unsupported();
        }

        @Override
        public void deleteOrgPermission(String permissionId) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("本门只验用户机构关联读面");
        }
    }

    /** 只交付事实的属主夹具：不排序、不去重、不 LIMIT，行集原样交回。 */
    private static IOrganizationService facade(List<UserOrganization> rows) {
        return new FacadeStub() {
            @Override
            public List<UserOrganization> getUserOrganizationAffiliations(String userId) {
                return rows;
            }
        };
    }

    @Test
    @DisplayName("恰好一枚主机构 ⇒ 返回该机构号（正向对照：事实完备时不得谎报未解析）")
    void uniquePrimaryOrgIsResolved() {
        SubjectDepartmentResolutionService svc = new SubjectDepartmentResolutionService(
                facade(List.of(row("ORG-A", "1"), row("ORG-B", "0"))));

        assertEquals("ORG-A", svc.resolvePrimaryOrgId("u1"));
    }

    @Test
    @DisplayName("零枚主机构 ⇒ null＝如实未解析，禁替主体挑一个机构")
    void noPrimaryRowIsUnresolved() {
        SubjectDepartmentResolutionService svc = new SubjectDepartmentResolutionService(
                facade(List.of(row("ORG-A", "0"), row("ORG-B", "0"))));

        assertNull(svc.resolvePrimaryOrgId("u1"));
    }

    @Test
    @DisplayName("多枚互异主机构 ⇒ null：这是取其一判据的分水岭（get(0) 在这里必然变红）")
    void ambiguousPrimaryRowsAreUnresolved() {
        SubjectDepartmentResolutionService svc = new SubjectDepartmentResolutionService(
                facade(List.of(row("ORG-A", "1"), row("ORG-B", "1"))));

        assertNull(svc.resolvePrimaryOrgId("u1"));
        assertNull(SubjectDepartmentResolutionService.uniquePrimaryOrg(
                List.of(row("ORG-A", "1"), row("ORG-B", "1"), row("ORG-C", "1"))));
    }

    @Test
    @DisplayName("主机构行机构号为空 ⇒ null（残缺事实不外推）")
    void primaryRowWithoutOrgIdIsUnresolved() {
        assertNull(SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row("  ", "1"))));
        assertNull(SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row(null, "true"))));
    }

    @Test
    @DisplayName("同一机构重复登记为主机构 ⇒ 按集合判「恰好一枚」成立（比对的是机构集合，不是行数）")
    void duplicatedSameOrgPrimaryRowsStillResolve() {
        assertEquals("ORG-A", SubjectDepartmentResolutionService.uniquePrimaryOrg(
                List.of(row("ORG-A", "1"), row("ORG-A", "1"))));
    }

    @Test
    @DisplayName("is_primary 读数归一：'1'/' 1 '/'TRUE' 认主机构，其它值（含 null、'Y'、'2'）按非主机构")
    void isPrimaryValueSpaceNormalized() {
        assertEquals("ORG-A", SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row("ORG-A", " 1 "))));
        assertEquals("ORG-A", SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row("ORG-A", "TRUE"))));
        assertNull(SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row("ORG-A", null))));
        assertNull(SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row("ORG-A", "Y"))));
        assertNull(SubjectDepartmentResolutionService.uniquePrimaryOrg(List.of(row("ORG-A", "2"))));
    }

    @Test
    @DisplayName("无主体就没有事实：userId 空 ⇒ 不取数、不抛、返回 null")
    void blankUserNeedsNoFetch() {
        SubjectDepartmentResolutionService svc = new SubjectDepartmentResolutionService(
                facade(List.of(row("ORG-A", "1"))));

        assertNull(svc.resolvePrimaryOrgId(null));
        assertNull(svc.resolvePrimaryOrgId("   "));
    }

    @Test
    @DisplayName("属主门面未装配 ⇒ 抛不可用态，禁折叠成「该用户没有主机构」")
    void absentFacadeIsUnavailableNotUnresolved() {
        SubjectDepartmentResolutionService svc =
                new SubjectDepartmentResolutionService((IOrganizationService) null);

        SubjectDepartmentResolutionService.SubjectSourceUnavailableException e =
                assertThrows(SubjectDepartmentResolutionService.SubjectSourceUnavailableException.class,
                        () -> svc.resolvePrimaryOrgId("u1"));
        assertTrue(e.getMessage().contains(SubjectDepartmentResolutionService.REASON_SOURCE_UNAVAILABLE),
                "机读理由必须可分派: " + e.getMessage());

        // 装配面 Optional 缺席（boot 调试态不带 sysman bean）同样是不可用，不是未解析
        SubjectDepartmentResolutionService viaOptional =
                new SubjectDepartmentResolutionService(Optional.empty());
        assertThrows(SubjectDepartmentResolutionService.SubjectSourceUnavailableException.class,
                () -> viaOptional.resolvePrimaryOrgId("u1"));
    }

    @Test
    @DisplayName("属主取数失败 / 返回 null 行集 ⇒ 同为不可用态；空行集是事实，不得抛")
    void fetchFailureAndNullRowsAreUnavailable() {
        SubjectDepartmentResolutionService throwing = new SubjectDepartmentResolutionService(
                new FacadeStub() {
                    @Override
                    public List<UserOrganization> getUserOrganizationAffiliations(String userId)
                            throws OrganizationException {
                        throw new OrganizationException("QUERY_FAILED", "PG 连接中断");
                    }
                });
        assertTrue(assertThrows(
                SubjectDepartmentResolutionService.SubjectSourceUnavailableException.class,
                () -> throwing.resolvePrimaryOrgId("u1")).getMessage().contains("属主取数失败"));

        SubjectDepartmentResolutionService nullRows =
                new SubjectDepartmentResolutionService(facade(null));
        assertTrue(assertThrows(
                SubjectDepartmentResolutionService.SubjectSourceUnavailableException.class,
                () -> nullRows.resolvePrimaryOrgId("u1")).getMessage().contains("null 关联行集"));

        // 真有零条关联行是「事实不完备」，不是「事实不可用」⇒ 落未解析、不抛
        assertNull(new SubjectDepartmentResolutionService(facade(new ArrayList<>()))
                .resolvePrimaryOrgId("u1"));
    }
}
