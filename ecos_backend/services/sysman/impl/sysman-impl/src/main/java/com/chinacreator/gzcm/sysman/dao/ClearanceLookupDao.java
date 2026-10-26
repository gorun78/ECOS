package com.chinacreator.gzcm.sysman.dao;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 准入等级拦截器数据访问 DAO — 承接 ClearanceInterceptor 中的 JdbcTemplate 操作。
 * <p>铁律 §2.5：拦截器不直用 JdbcTemplate，统一经 DAO。
 * <p>注意：{@code queryForObject} 在无结果行时会抛 {@code EmptyResultDataAccessException}，
 * 由拦截器捕获后按缓存回退。此为既有 fail-soft 语义的一部分，保持 DAO 不吞异常。
 * <p><b>标识符面纪律</b>（#142/B4-3e-82，收口 D-B4-99 e)）：本类<b>不得</b>以调用方传入的字符串
 * 拼出表名/列名。原 {@code queryClearanceRows(String tableName, String idColumn, …)} 用
 * {@code String.format} 把两个 {@code String} 参嵌进 SQL——唯一生产调用点传的是字面量，
 * 所以今日不可利用，但它是一条<b>无门禁</b>的注入面：任何后来者传变量（租户号、机构号、
 * 前端可影响的配置键）即成 SQL 注入，且本模块的密级裁决正建在它上面。现改为
 * {@link ClearanceRowKey} 具名白名单：标识符只存在于枚举常量里的完整 SQL 字面量，
 * 运行期无拼接、无格式化，未登记的行表在类型系统上无法表达。同批删除另一条已死腿
 * {@code findOrgById}（唯一调用点 {@code ClearanceInterceptor#getCurrentOrgId} 已改走
 * 属主 api 门面 {@code getUserOrganizationAffiliations} + {@code PrimaryOrganizationRule}
 * 单源判据；判据本体与死码治理见 #142/B4-3e-70 惯例）。
 * <p><b>角色档不再自带 SQL</b>（#142/B4-3e-83，收口 D-B4-99 d)）：{@code highestRoleClearanceLevel}
 * 原是一条 {@code COALESCE(MAX(rsp.clearance_level), -1)} 聚合腿，与
 * {@link SecurityProfileQueryDao#findHighestRoleForUser} 的行读、以及安全引擎侧的逐字副本
 * 构成三处实现面——同一主体的「最高角色档」可以有三个答案（空密级行在两个面读数相反）。
 * 现聚合语句删除，本方法委派属主行读单源，只保留 {@code -1}＝「无配置」的读取值归一。
 * <p>门禁：{@code ClearanceLookupDaoSqlFaceRatchetTest} 把本类的 SQL 字面量集合钉成多重集，
 * 并硬钉零枚 {@code String.format}／{@code %s}，新增任何一条语句必须先过对白名单；
 * {@code HighestRoleProfileSingleSourceRatchetTest} 另钉「全仓只有一枚最高档语句」。
 */
@Repository
public class ClearanceLookupDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 最高角色档的属主行读（本类不再自带该语句，见类注释 D-B4-99 d) 段）。 */
    private final SecurityProfileQueryDao securityProfileQueryDao;

    public ClearanceLookupDao(SecurityProfileQueryDao securityProfileQueryDao) {
        this.securityProfileQueryDao = securityProfileQueryDao;
    }

    /**
     * 密级取数的<b>具名行表白名单</b>（#142/B4-3e-82）：一枚条目＝一套完整的两形态语句，
     * 标识符只能是编译期字面量。
     *
     * <p>两形态＝「带租户过滤」与「不带租户过滤」，与 {@code td_user_security_profile}
     * 的租户列可空语义一致（{@code tenant_id IS NULL} 的行对任意租户可见，故带租户腿写成
     * {@code (tenant_id = ? OR tenant_id IS NULL)}）。把语句整体而不是把标识符拆开放进枚举，
     * 是为了让「拼标识符」这件事在结构上不存在。</p>
     */
    public enum ClearanceRowKey {
        /** 用户级密级档案（{@code scope_type} 由 {@code user_id} 单键定位，不在此重复过滤）。 */
        USER_PROFILE("td_user_security_profile", "user_id",
                "SELECT clearance_level FROM td_user_security_profile WHERE user_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                "SELECT clearance_level FROM td_user_security_profile WHERE user_id = ? LIMIT 1");

        private final String table;
        private final String idColumn;
        private final String tenantScopedSql;
        private final String unscopedSql;

        ClearanceRowKey(String table, String idColumn, String tenantScopedSql, String unscopedSql) {
            this.table = table;
            this.idColumn = idColumn;
            this.tenantScopedSql = tenantScopedSql;
            this.unscopedSql = unscopedSql;
        }

        /** 表名（只用于日志与门禁对账，<b>不</b>参与语句构造）。 */
        public String table() {
            return table;
        }

        /** id 列名（同上，只用于可取证性）。 */
        public String idColumn() {
            return idColumn;
        }

        /** 带租户过滤形态的完整语句（编译期字面量）。 */
        public String tenantScopedSql() {
            return tenantScopedSql;
        }

        /** 不带租户过滤形态的完整语句（编译期字面量）。 */
        public String unscopedSql() {
            return unscopedSql;
        }
    }

    /**
     * 按白名单条目回查一档密级配置（含租户过滤形态）。
     *
     * <p>参数只交付<b>值</b>（{@code idValue}／{@code tenantId}），二者一律走占位符；
     * 标识符由 {@link ClearanceRowKey} 在编译期封闭。语义与改前逐字等价（同一形态、
     * 同一 {@code LIMIT 1}、同一 {@code tenantId != null} 判据），变的只是类型。</p>
     */
    public List<Map<String, Object>> queryClearanceRows(ClearanceRowKey key, String idValue, String tenantId) {
        String sql = tenantId != null ? key.tenantScopedSql() : key.unscopedSql();
        Object[] params = tenantId != null ? new Object[]{idValue, tenantId} : new Object[]{idValue};
        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * 查询该用户最高角色档的 clearance_level，{@code -1}＝无配置（调用方按「该档未命中」继续下探）。
     *
     * <p>语句已归一属主行读 {@link SecurityProfileQueryDao#findHighestRoleForUser}（#142/B4-3e-83）：
     * 本方法只做「无档案 ⇒ {@code -1}」的读取值归一，不再自带聚合 SQL——聚合腿
     * {@code MAX()} 忽略 NULL 而行读 {@code ORDER BY … DESC} 默认 NULLS FIRST，两条腿对
     * 空密级行给出相反读数，这正是 D-B4-99 d) 的缺陷本体。</p>
     */
    public Integer highestRoleClearanceLevel(String userId, String tenantId) {
        SecurityProfile profile = securityProfileQueryDao.findHighestRoleForUser(userId, tenantId);
        if (profile == null || profile.getClearanceLevel() == null) {
            return -1;
        }
        return profile.getClearanceLevel();
    }

    public Integer globalDefaultClearanceLevel(String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
            params = new Object[]{tenantId};
        } else {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1";
            params = new Object[]{};
        }
        return jdbcTemplate.queryForObject(sql, Integer.class, params);
    }

    public Integer orgClearanceLevel(String orgId, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' AND (tenant_id = ? OR tenant_id IS NULL) ORDER BY clearance_level DESC LIMIT 1";
            params = new Object[]{orgId, tenantId};
        } else {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' ORDER BY clearance_level DESC LIMIT 1";
            params = new Object[]{orgId};
        }
        return jdbcTemplate.queryForObject(sql, Integer.class, params);
    }

    public Integer tenantClearanceLevel(String tenantId) {
        String sql = "SELECT clearance_level FROM td_user_security_profile WHERE tenant_id = ? AND scope_type = 'TENANT' ORDER BY clearance_level DESC LIMIT 1";
        return jdbcTemplate.queryForObject(sql, Integer.class, tenantId);
    }
}
