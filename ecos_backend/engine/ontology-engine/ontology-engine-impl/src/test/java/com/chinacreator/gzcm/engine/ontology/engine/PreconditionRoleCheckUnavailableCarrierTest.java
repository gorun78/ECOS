package com.chinacreator.gzcm.engine.ontology.engine;

import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ontology.ActionTypeService;
import com.chinacreator.gzcm.engine.ontology.controller.ActionTypeController;
import com.chinacreator.gzcm.engine.ontology.dao.PreconditionDao;
import com.chinacreator.gzcm.engine.ontology.dto.ActionTypeExecuteQuery;
import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.chinacreator.gzcm.engine.ontology.testscan.SourceCommentStripper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-54 —— 收口差距 <b>D-B4-84</b>：{@code PreconditionEngine.checkUserRole} 的
 * 「数据访问故障 ⇒ 角色校验通过」宽松降级腿改显式不可用承载；并同批收口其生效前置
 * <b>D-B4-89</b>（{@code op} 词表归一口径与 {@code case} 标签分裂 ⇒ 角色腿整体不可达）。
 *
 * <h3>差距形态（修法前）</h3>
 * <p>{@code checkUserRole} 的 {@code catch (Exception e)} 体是 {@code log.debug(…)} ＋
 * {@code return true}，注释自证「宽容模式：表不存在时默认通过」。⇒ {@code sys_user_role} 查询
 * 一旦抛异常（表缺失、连接抖动、权限不足），带 {@code hasRole} 前置条件的 ActionType 会
 * <b>把「查不了」当成「有角色」放行并落库执行</b>。一处违架构铁律 #3（security 不可用默认 DENY），
 * 一处违详设 §4.7.2（不可用 ≠ 无权限 ≠ 空结果）。它与 #142/B4-3e-52 收口的 <b>D-B4-82</b>
 * 同文件同形，但触发面不同：那枚欠装配，本枚欠数据面——故修法同类而分态案必须自配
 * （台账 3e-52 段已预登记这条要求）。</p>
 *
 * <h3>本门钉住的三态（与 3e-40~43／3e-52 承载族对齐，不另立第三形态）</h3>
 * <ul>
 *   <li><b>不可用</b>＝抛 {@link DataBridgeException}(503, 503, {@code ECOS-SYS-503: …})；
 *       禁折成 {@code false}（那会把「查不了」谎报成「没权限」，§4.7.2 明禁的另一侧折叠）；</li>
 *   <li><b>拒绝</b>＝返回 {@code false}：角色计数为 0、为 {@code null}、或主体 {@code userId} 缺失
 *       （后者不查库，属入参侧拒绝而非不可用）；</li>
 *   <li><b>通过</b>＝返回 {@code true}：仅当计数 &gt; 0（正向对照钉住「收紧没过头」）。</li>
 * </ul>
 *
 * <h3>能变红</h3>
 * <p>生产码退回原形态（{@code catch} 里 {@code return true} ＋ {@code case "hasRole"} 标签）后回跑
 * 本门：案 1 因「不抛而是 {@code passed=false}／或（标签修好后）{@code passed=true}」变红、案 7 四写全红、
 * 案 3 因角色腿派发不到而红、案 8 因源码面复现宽容字样与大写标签而红；案 2/4/5 负责「不许改过头」——
 * 把拒绝态也一并抛 503 会让案 2/4/5 变红，把通过态也拒绝会让案 3 变红。
 * 面级防回退由 {@code runtime/common-api} 的 {@code LenientDegradeTrueFaceRatchetTest} 持有。</p>
 */
@ExtendWith(MockitoExtension.class)
class PreconditionRoleCheckUnavailableCarrierTest {

    /** 属主侧不可用腿所在文件（静态断言面）。 */
    private static final String ENGINE_SOURCE =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ontology/engine/PreconditionEngine.java";

    /** {@code checkUserRole} 方法体区间定界（起点＝签名，终点＝下一枚私有方法签名）。 */
    private static final String ROLE_METHOD_HEAD = "private boolean checkUserRole(";
    private static final String NEXT_METHOD_HEAD = "private boolean evaluateAbac(";

    /** fail-open 形态的字面特征：自证宽容的注释文案。 */
    private static final String FAIL_OPEN_LOG = "宽容模式";

    @Mock
    private PreconditionDao dao;

    // ── 1. 不可用腿：显式承载 ───────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("角色数据面抛异常＝携带 503＋ECOS-SYS-503 的不可用载体，禁返回「校验通过」")
    void roleDataAccessFailureThrowsCarriedUnavailable() {
        stubEmptyObjectState();
        when(dao.countUserRole("u-1", "admin"))
                .thenThrow(new IllegalStateException("relation sys_user_role does not exist"));
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> engine.check(hasRoleAction(), "obj-1", Map.of("userId", "u-1")),
                "数据面故障必须抛出（不可用），不得静默放行");

        assertEquals(503, thrown.getHttpStatus(), "不可用腿状态行必须＝503，禁落 500 兜底");
        assertEquals(503, thrown.getErrorCode(), "携带码必须与状态行同号");
        assertTrue(thrown.getMessage().startsWith(SystemErrorCode.ECOS_SYS_503 + ":"),
                "消息首段必须是机读码 ECOS-SYS-503，实际：" + thrown.getMessage());
        assertEquals(thrown.getHttpStatus(),
                DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                "承载状态行必须由 resolveHttpStatus 单源推出");
    }

    // ── 2/3/4. 分态：拒绝与通过不可串道 ─────────────────────

    @Test
    @Timeout(120)
    @DisplayName("角色计数为 0＝前置条件不过（passed=false），不是不可用——禁把「没权限」折成 503")
    void absentRoleIsVerdictNotUnavailability() {
        stubEmptyObjectState();
        when(dao.countUserRole("u-1", "admin")).thenReturn(0);
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        Map<String, Object> result = engine.check(hasRoleAction(), "obj-1", Map.of("userId", "u-1"));

        assertEquals(false, result.get("passed"), "计数 0 必须判不过：" + result);
        Map<?, ?> record = firstCheck(result);
        assertEquals("status", record.get("field"), "判定记录须保留被检字段名");
        assertEquals("admin", record.get("expected"), "判定记录须保留期望角色，供审计回读");
        assertEquals(false, record.get("passed"), "该条判定本身须记为不过");
    }

    @Test
    @Timeout(120)
    @DisplayName("正向对照：角色计数为 1＝真通过（证明上一案的 false 来自裁决而非判据失灵）")
    void grantedRoleReallyPasses() {
        stubEmptyObjectState();
        when(dao.countUserRole("u-1", "admin")).thenReturn(1);
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        Map<String, Object> result = engine.check(hasRoleAction(), "obj-1", Map.of("userId", "u-1"));

        assertEquals(true, result.get("passed"),
                "计数 > 0 必须放行，否则本门案 2 的红字无鉴别力：" + result);
    }

    @Test
    @Timeout(120)
    @DisplayName("角色计数返回空值＝拒绝（false），不抛不可用——空值属「无匹配」而非「查不了」")
    void nullCountIsDenialNotUnavailability() {
        stubEmptyObjectState();
        when(dao.countUserRole("u-1", "admin")).thenReturn(null);
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        Map<String, Object> result = engine.check(hasRoleAction(), "obj-1", Map.of("userId", "u-1"));

        assertEquals(false, result.get("passed"), "空值计数须判不过：" + result);
    }

    // ── 5. 主体缺失：拒绝且不触库 ───────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("context 缺 userId＝拒绝（false）且不查库；主体缺失属入参侧，禁折成不可用")
    void missingSubjectIsDenialWithoutQuery() {
        stubEmptyObjectState();
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        Map<String, Object> result = engine.check(hasRoleAction(), "obj-1", Map.of());

        assertEquals(false, result.get("passed"), "无主体须判不过：" + result);
        verify(dao, never()).countUserRole(anyString(), anyString());
    }

    // ── 6. 承流面：端点已覆盖本路径 ─────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("execute 端点必须把 hasRole 承载腿原样放行到状态行，禁折成 internalError")
    void controllerRethrowsRoleCarriedUnavailable() {
        ActionTypeService service = mock(ActionTypeService.class);
        when(service.executeAction(eq("act-1"), any())).thenThrow(new DataBridgeException(503, 503,
                SystemErrorCode.ECOS_SYS_503 + ": 角色数据面不可用，hasRole 前置条件无法判定"));
        ActionTypeController controller = new ActionTypeController(service);

        ActionTypeExecuteQuery query = new ActionTypeExecuteQuery();
        query.setObjectId("obj-1");
        query.setContext(Map.of("userId", "u-1"));

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> controller.execute("act-1", query),
                "端点必须放行承载型异常给属主 handler（3e-52 的上抛腿须覆盖本路径）");
        assertEquals(503, thrown.getHttpStatus(), "端点承载后状态行仍是 503");
    }

    // ── 7. 词表归一：标签必须吃归一后的形态（D-B4-89）───────

    @Test
    @Timeout(120)
    @DisplayName("op 词表按归一后匹配＝作者写 HasRole/HASROLE/hasrole 都能派发到角色腿（禁标签漂移）")
    void opLabelMatchesNormalizedFormForAnyAuthoringCase() {
        stubEmptyObjectState();
        when(dao.countUserRole("u-1", "admin")).thenReturn(1);
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        for (String authored : List.of("hasRole", "HasRole", "HASROLE", "hasrole")) {
            Map<String, Object> result = engine.check(roleActionWithOp(authored), "obj-1",
                    Map.of("userId", "u-1"));
            assertEquals(true, result.get("passed"),
                    "op=\"" + authored + "\" 必须派发到角色腿（归一后与 case 标签同号）：" + result);
        }
    }

    // ── 8. 源码面：宽容降级腿不得复现 ───────────────────────

    @Test
    @Timeout(120)
    @DisplayName("主源码面复核：checkUserRole 体内无顶层 return true 宽容腿、不产信封，且承载腿在位")
    void mainSourceNoLongerCarriesTheLenientDegradeLeg() throws IOException {
        String engine = SourceCommentStripper.strip(readRepoSource(ENGINE_SOURCE));
        String body = between(engine, ROLE_METHOD_HEAD, NEXT_METHOD_HEAD, ENGINE_SOURCE);

        assertFalse(body.contains("return true"),
                "checkUserRole 内禁出现宽容通过返回值（D-B4-84 复现即红），方法体实读：" + body);
        assertTrue(body.contains("throw new DataBridgeException"),
                "不可用腿必须抛承载型异常（503 族），方法体实读：" + body);
        assertTrue(engine.contains("SystemErrorCode.ECOS_SYS_503"),
                "机读码必须取单源常量，禁另写字面量");
        assertFalse(engine.contains(FAIL_OPEN_LOG),
                "自证宽松降级的注释文案必须随修法一起消失");
        assertFalse(body.contains("ApiResponse"),
                "引擎层不得产出信封（折叠只允许发生在承流面），方法体实读：" + body);
        // D-B4-89：subject 已 toLowerCase，标签必须取归一形态；写回 camelCase 标签＝角色腿重新变死面
        assertTrue(engine.contains("case \"hasrole\""),
                "hasRole 操作符的 case 标签必须取归一后的小写形态（大标签恒不命中＝死面复现）");
        assertFalse(engine.contains("case \"hasRole\""),
                "归一口径下 camelCase 标签不可达，禁回写（D-B4-89 复现即红）");
    }

    // ── helpers ────────────────────────────────────────────

    /** 对象状态查询两表都返回空集：让求值只依赖 context 与角色计数，不绑库。 */
    private void stubEmptyObjectState() {
        when(dao.findObjectStateFromOntologyObject(anyString())).thenReturn(List.of());
        when(dao.findObjectStateFromOntology(anyString())).thenReturn(List.of());
    }

    /** 只带一枚 {@code hasRole} 前置条件的 ActionType。 */
    private static ActionType hasRoleAction() {
        return roleActionWithOp("hasRole");
    }

    /** 指定 op 字面写法的单条件 ActionType（用于取证词表归一口径）。 */
    private static ActionType roleActionWithOp(String op) {
        ActionType action = new ActionType();
        action.setId("act-1");
        action.setName("role-gated-action");
        action.setPreconditions("[{\"field\":\"status\",\"op\":\"" + op + "\",\"value\":\"admin\"}]");
        return action;
    }

    private static Map<?, ?> firstCheck(Map<String, Object> result) {
        List<?> checks = (List<?>) result.get("checks");
        assertEquals(1, checks.size(), "hasRole 腿应留下 1 条判定记录，实际：" + checks);
        return (Map<?, ?>) checks.get(0);
    }

    private static String readRepoSource(String repoRelative) throws IOException {
        return Files.readString(repoFile(repoRelative));
    }

    /** 取源码区间 [startHead, endHead)；定界符缺失即抛，禁「找不到就算通过」。 */
    private static String between(String code, String startHead, String endHead, String sourceName) {
        int from = code.indexOf(startHead);
        if (from < 0) {
            throw new IllegalStateException(sourceName + " 缺定界起点 [" + startHead
                    + "]＝判据面漂移，禁跳过校验");
        }
        int to = code.indexOf(endHead, from + startHead.length());
        if (to < 0) {
            throw new IllegalStateException(sourceName + " 缺定界终点 [" + endHead
                    + "]＝判据面漂移，禁跳过校验");
        }
        return code.substring(from, to);
    }

    /** 与同域门禁 {@code PreconditionAbacUnavailableCarrierTest} 一致的仓根定位（找不到即抛）。 */
    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到判据两侧源码（禁跳过校验）: " + repoRelative);
    }
}
