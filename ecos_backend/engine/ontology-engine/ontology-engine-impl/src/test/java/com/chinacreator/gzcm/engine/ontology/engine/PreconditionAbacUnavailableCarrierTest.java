package com.chinacreator.gzcm.engine.ontology.engine;

import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ontology.ActionTypeService;
import com.chinacreator.gzcm.engine.ontology.controller.ActionTypeController;
import com.chinacreator.gzcm.engine.ontology.dao.PreconditionDao;
import com.chinacreator.gzcm.engine.ontology.dto.ActionTypeExecuteQuery;
import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.chinacreator.gzcm.engine.ontology.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-52 —— 收口差距 <b>D-B4-82</b>：{@code PreconditionEngine} 的 ABAC 未装配腿禁 fail-open。
 *
 * <h3>差距形态（修法前）</h3>
 * <p>{@code evaluateAbac()} 首行是 {@code if (abacPermissionChecker == null) { … return true; }}，
 * 字段注释与类 javadoc 都写着「未配置时 ABAC 操作符<b>宽容通过</b>」。⇒ 一旦 security 侧的
 * {@code IAbacPermissionChecker} 没装配（本域走 {@code Optional} 注入，缺 Bean 即 {@code null}），
 * 带 {@code abac} 前置条件的 ActionType 会<b>整条动作直接放行并落库执行</b>。这同时违两条成文红线：
 * 架构铁律 #3「security 不可用时默认 DENY」，与详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」——
 * 它比折叠更糟，是把「判不了」读成「判过了」。</p>
 *
 * <h3>本门钉住的口径（与在册同族修法对齐，不另立第三形态）</h3>
 * <ul>
 *   <li><b>不可用＝显式不可用</b>：抛 {@link DataBridgeException}(httpStatus=503, errorCode=503,
 *       消息首段＝{@code SystemErrorCode.ECOS_SYS_503})，即 #142/B4-3e-40 起 runtime-core 出口、
 *       3e-41 workspace、3e-42 kb、3e-43 data-engine 四批确立的「503 载体」族；</li>
 *   <li><b>端点显式承载</b>：{@code ActionTypeController.execute} 的 {@code catch (Exception)} 兜底
 *       会把承载型异常折成 {@code internalError(500)}，故本批在同一条 try 上先加
 *       {@code catch (DataBridgeException e) { throw e; }} 让 gateway {@code GlobalExceptionHandler}
 *       按携带状态渲染；</li>
 *   <li><b>策略拒绝腿形态不变</b>：检查器在场且返回 {@code DENY} 时仍是 {@code passed=false}，
 *       与「不可用」分属两条通道——正向对照（{@code PERMIT} ⇒ {@code passed=true}）钉住这条 false
 *       确实来自裁决而非判据失灵。</li>
 * </ul>
 *
 * <h3>能变红</h3>
 * <p>生产码未改前回跑本门：第 1、5 案必红（缺席腿返回 {@code passed=true} 而非抛出；
 * 源码面仍是 {@code return true}＋「allowing by default」字样），第 4 案必红
 * （控制器把承载折成 {@code internalError}）。第 2、3 案与判据两侧文件缺失时的
 * {@code IllegalStateException} 一起构成「判据两侧必须都在」的防跳过断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class PreconditionAbacUnavailableCarrierTest {

    /** 属主侧不可用腿所在文件（静态断言面）。 */
    private static final String ENGINE_SOURCE =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ontology/engine/PreconditionEngine.java";

    /** 承流端点所在文件（显式承载腿静态断言面）。 */
    private static final String CONTROLLER_SOURCE =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ontology/controller/ActionTypeController.java";

    /** {@code evaluateAbac} 方法体区间定界（起点＝签名，终点＝下一枚私有方法签名）。 */
    private static final String ABAC_METHOD_HEAD = "private boolean evaluateAbac(";
    private static final String NEXT_METHOD_HEAD = "private Map<String, Object> resolveAbacSection(";

    /** fail-open 形态的两个字面特征：宽容返回值与自证日志文案。 */
    private static final String FAIL_OPEN_RETURN = "return true";
    private static final String FAIL_OPEN_LOG = "allowing by default";

    @Mock
    private PreconditionDao dao;

    // ── 1. 缺席腿：显式不可用 ───────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("ABAC 检查器未装配＝抛携带 503＋ECOS-SYS-503 的不可用载体，禁返回「通过」")
    void absentCheckerThrowsCarriedUnavailable() {
        stubEmptyObjectState();
        PreconditionEngine engine = new PreconditionEngine(dao, Optional.empty());

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> engine.check(abacAction(), "obj-1", Map.of("userId", "u-1")),
                "检查器缺席必须抛出（不可用），不得静默放行");

        assertEquals(503, thrown.getHttpStatus(), "不可用腿状态行必须＝503，禁落 500 兜底");
        assertEquals(503, thrown.getErrorCode(), "携带码必须与状态行同号");
        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().startsWith(SystemErrorCode.ECOS_SYS_503 + ":"),
                "消息首段必须是机读码 ECOS-SYS-503，实际：" + thrown.getMessage());
        // 状态行单源：携带码经 resolveHttpStatus 推出的状态行必须与抛出值同号（禁第二个状态字面量）
        assertEquals(thrown.getHttpStatus(),
                DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                "承载状态行必须由 resolveHttpStatus 单源推出");
    }

    // ── 2/3. 在场腿：裁决与不可用分态 ───────────────────────

    @Test
    @Timeout(120)
    @DisplayName("检查器在场且 DENY＝前置条件不满足（passed=false），不是不可用")
    void assembledCheckerDenyIsVerdictNotUnavailability() {
        stubEmptyObjectState();
        PreconditionEngine engine = new PreconditionEngine(dao,
                Optional.of(context -> IAbacPermissionChecker.Decision.DENY));

        Map<String, Object> result = engine.check(abacAction(), "obj-1", Map.of("userId", "u-1"));

        assertEquals(false, result.get("passed"),
                "DENY 必须让前置条件不过： " + result);
        List<?> checks = (List<?>) result.get("checks");
        assertEquals(1, checks.size(), "DENY 腿应留下 1 条判定记录，实际：" + checks);
        Map<?, ?> record = (Map<?, ?>) checks.get(0);
        assertEquals("status", record.get("field"), "判定记录须保留被检字段名");
        assertEquals(false, record.get("passed"), "该条判定本身须记为不过");
    }

    @Test
    @Timeout(120)
    @DisplayName("正向对照：检查器在场且 PERMIT＝真通过（证明上一案的 false 来自裁决而非判据失灵）")
    void assembledCheckerPermitReallyPasses() {
        stubEmptyObjectState();
        PreconditionEngine engine = new PreconditionEngine(dao,
                Optional.of(context -> IAbacPermissionChecker.Decision.PERMIT));

        Map<String, Object> result = engine.check(abacAction(), "obj-1", Map.of("userId", "u-1"));

        assertEquals(true, result.get("passed"),
                "PERMIT 必须放行，否则本门第 2 案的红字无鉴别力： " + result);
    }

    // ── 4. 端点显式承载 ────────────────────────────────────

    @Test
    @Timeout(120)
    @DisplayName("execute 端点必须把承载型不可用原样上抛，禁在 catch (Exception) 里折成 internalError")
    void controllerRethrowsCarriedUnavailableInsteadOfCollapsingTo500() {
        ActionTypeService service = mock(ActionTypeService.class);
        when(service.executeAction(eq("act-1"), any())).thenThrow(
                new DataBridgeException(503, 503,
                        SystemErrorCode.ECOS_SYS_503 + ": ABAC 检查器未装配，前置条件无法判定"));
        ActionTypeController controller = new ActionTypeController(service);

        ActionTypeExecuteQuery query = new ActionTypeExecuteQuery();
        query.setObjectId("obj-1");
        query.setContext(Map.of("userId", "u-1"));

        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> controller.execute("act-1", query),
                "端点必须放行承载型异常给属主 handler，不得折叠成 ApiResponse.internalError");
        assertEquals(503, thrown.getHttpStatus(), "端点承载后状态行仍是 503");
    }

    // ── 5. 源码面：fail-open 形态不得复现 ───────────────────

    @Test
    @Timeout(120)
    @DisplayName("主源码面复核：evaluateAbac 方法体内不得再出现 fail-open 返回值，端点须有显式承载腿")
    void mainSourceNoLongerCarriesTheFailOpenShape() throws IOException {
        String engine = SourceCommentStripper.strip(readRepoSource(ENGINE_SOURCE));
        String body = between(engine, ABAC_METHOD_HEAD, NEXT_METHOD_HEAD, ENGINE_SOURCE);

        assertTrue(body.contains("throw new DataBridgeException"),
                "不可用腿必须抛承载型异常（503 族），方法体实读：" + body);
        assertTrue(engine.contains("SystemErrorCode.ECOS_SYS_503"),
                "机读码必须取单源常量，禁另写字面量");
        assertFalse(body.contains(FAIL_OPEN_RETURN),
                "evaluateAbac 内禁出现宽容通过返回值（D-B4-82 复现即红）");
        assertFalse(engine.contains(FAIL_OPEN_LOG),
                "自证 fail-open 的日志文案必须随修法一起消失");

        String controller = SourceCommentStripper.strip(readRepoSource(CONTROLLER_SOURCE));
        assertTrue(controller.contains("catch (DataBridgeException"),
                "execute 端点必须保留显式承载腿（缺它＝折叠回 500）");
    }

    // ── helpers ────────────────────────────────────────────

    /** 对象状态查询两表都返回空集：让求值只依赖 context 与裁决，不绑库。 */
    private void stubEmptyObjectState() {
        when(dao.findObjectStateFromOntologyObject(anyString())).thenReturn(List.of());
        when(dao.findObjectStateFromOntology(anyString())).thenReturn(List.of());
    }

    /** 只带一枚 {@code abac} 前置条件的 ActionType（value 为四元组 Map）。 */
    private static ActionType abacAction() {
        ActionType action = new ActionType();
        action.setId("act-1");
        action.setName("abac-gated-action");
        action.setPreconditions("[{\"field\":\"status\",\"op\":\"abac\",\"value\":{"
                + "\"subject\":{\"role\":\"admin\"},"
                + "\"resource\":{\"type\":\"ontology_object\",\"id\":\"obj-1\"},"
                + "\"action\":{\"name\":\"read\"},"
                + "\"environment\":{\"channel\":\"page\"}}}]");
        return action;
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

    /** 与同域门禁 {@code OntologyReadAspectWriteBackTest} 一致的仓根定位（找不到即抛）。 */
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
