package com.chinacreator.gzcm.sysman.iam.dao.impl;

import com.chinacreator.gzcm.sysman.iam.dao.OrganizationDao;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.chinacreator.gzcm.sysman.iam.service.impl.OrganizationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-80（差距 D-B4-23 主体域腿）— 属主侧「用户机构关联行」读面门禁。
 *
 * <p>读的是运行期真正加载的那份 {@code Organization-sql.xml}（classpath 同 FQN 路径，
 * pom 已把 {@code src/main/java} 下的 {@code **\/*.xml} 收进 resources），不是抄本。
 * 判据盯的是「多重性必须可分」这一件事：旧读面 {@code getUserOrganizations} 把
 * {@code is_primary} 只用作 {@code ORDER BY}、投影里没有它 ⇒ 消费方分不出 0 枚与 2 枚主机构，
 * 只能 {@code get(0)}。新读面若把 {@code is_primary} 摘掉、或补上 {@code LIMIT 1}／
 * {@code WHERE is_primary='1'}，属主就又把判定权抢回了写侧——那正是 security 侧
 * {@code SubjectDepartmentResolutionService} 判定纪律的前提塌掉。</p>
 */
@Timeout(60)
class OrganizationAffiliationReadTest {

    private static final String SQL_RESOURCE =
            "com/chinacreator/gzcm/sysman/iam/dao/impl/Organization-sql.xml";

    private static String sqlConfig() {
        try (InputStream in = OrganizationAffiliationReadTest.class
                .getClassLoader().getResourceAsStream(SQL_RESOURCE)) {
            assertNotNull(in, "运行期读面缺失：" + SQL_RESOURCE + "（属主 DAO 与本门同源，缺文件即装配失败）");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("读取属主 SQL 配置失败: " + e, e);
        }
    }

    /** 取 {@code <select id="...">} 体（不解析 XML：判据要能被注释掉的写法照样拦不住，这里只按标记切段）。 */
    private static String selectBody(String xml, String id) {
        String open = "<select id=\"" + id + "\">";
        int start = xml.indexOf(open);
        assertTrue(start >= 0, "读面缺失：" + id);
        int end = xml.indexOf("</select>", start);
        assertTrue(end > start, id + " 的 select 段未闭合");
        return xml.substring(start + open.length(), end);
    }

    /** 投影段＝select 与 from 之间；WHERE 段＝where 之后。分段判据，禁整串 contains 蒙混。 */
    private static String projection(String bodyLower) {
        int from = bodyLower.indexOf("from");
        assertTrue(from > 0, bodyLower);
        return bodyLower.substring(bodyLower.indexOf("select"), from);
    }

    private static String whereClause(String bodyLower) {
        int where = bodyLower.indexOf("where");
        assertTrue(where > 0, bodyLower);
        return bodyLower.substring(where);
    }

    @Test
    @DisplayName("新读面把 is_primary 交付给消费方，且不排序/不 LIMIT/不在属主侧过滤")
    void affiliationReadDeliversMultiplicity() {
        String body = selectBody(sqlConfig(), "getUserOrganizationAffiliations");
        String lower = body.toLowerCase();

        assertTrue(projection(lower).contains("uo.is_primary"),
                "投影必须含 is_primary，否则消费方分不出 0 枚与 2 枚主机构:\n" + body);
        assertTrue(whereClause(lower).contains("uo.user_id = ?"), "只按主体取数:\n" + body);
        assertFalse(whereClause(lower).contains("is_primary"),
                "禁在属主侧按 is_primary 过滤——多枚主机构的歧义必须原样交回，不能被读面抹平:\n" + body);
        assertFalse(lower.contains("limit"), "禁 LIMIT——关联行被截断就是「取其一」的属主版本:\n" + body);
        assertFalse(lower.contains("order by"), "禁 ORDER BY——排序会让取第一条看起来安全:\n" + body);
        assertTrue(lower.contains("inner join td_organization"),
                "主机构指向已停写/注销机构时如实读不到，落未解析而不是拿废机构号比行:\n" + body);
        assertTrue(lower.contains("o.status != 'deleted'"), "同上——机构侧存活条件是判据的一部分:\n" + body);
    }

    @Test
    @DisplayName("旧读面 getUserOrganizations 原文保留（API 只增不改），且仍是 is_primary 不出投影的形态")
    void legacyReadStaysUntouched() {
        String body = selectBody(sqlConfig(), "getUserOrganizations");
        String lower = body.toLowerCase();

        assertTrue(lower.contains("order by uo.is_primary desc"), "既有排序形态不得被顺手改掉:\n" + body);
        assertFalse(projection(lower).contains("is_primary"),
                "本批不动旧读面投影（改它＝改既有契约面）:\n" + body);
    }

    @Test
    @DisplayName("属主 api 门面与 DAO 只增一枚读方法，返回关联行本身")
    void facadeAndDaoExposeTheRead() throws Exception {
        Method facade = IOrganizationService.class
                .getMethod("getUserOrganizationAffiliations", String.class);
        assertEquals(List.class, facade.getReturnType());
        assertTrue(facade.getGenericReturnType().getTypeName()
                        .contains(UserOrganization.class.getCanonicalName()),
                "行型必须是 TD_USER_ORGANIZATION 的实体，不得折叠成机构名或 String: "
                        + facade.getGenericReturnType().getTypeName());

        Method dao = OrganizationDao.class.getMethod("getUserOrganizationAffiliations", String.class);
        assertEquals(List.class, dao.getReturnType());

        // 实现腿必须在位（门面有声明、实现缺失会在装配期 NoSuchMethodError，这里提前判红）
        assertNotNull(OrganizationDaoImpl.class
                .getMethod("getUserOrganizationAffiliations", String.class));
        assertNotNull(OrganizationServiceImpl.class
                .getMethod("getUserOrganizationAffiliations", String.class));
    }

    @Test
    @DisplayName("判据可红性自检：同一条判据喂给变异片段必须报 LIMIT/缺投影/WHERE 混入过滤")
    void gateIsFalsifiable() {
        String limitMutant = "<select id=\"x\">SELECT uo.org_id, uo.is_primary FROM t uo"
                + " WHERE uo.user_id = ? LIMIT 1</select>";
        assertTrue(selectBody(limitMutant, "x").toLowerCase().contains("limit"),
                "判据对 LIMIT 失明＝假绿");

        String projectionMutant = "<select id=\"y\">SELECT uo.org_id FROM t uo WHERE uo.user_id = ?</select>";
        assertFalse(projection(selectBody(projectionMutant, "y").toLowerCase()).contains("uo.is_primary"),
                "判据对投影缺失失明＝假绿");

        String whereMutant = "<select id=\"z\">SELECT uo.org_id, uo.is_primary FROM t uo"
                + " WHERE uo.user_id = ? AND uo.is_primary = '1'</select>";
        assertTrue(whereClause(selectBody(whereMutant, "z").toLowerCase()).contains("is_primary"),
                "判据对属主侧过滤失明＝假绿");
    }
}
