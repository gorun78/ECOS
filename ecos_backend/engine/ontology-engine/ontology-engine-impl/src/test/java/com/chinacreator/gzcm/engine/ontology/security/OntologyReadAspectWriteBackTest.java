package com.chinacreator.gzcm.engine.ontology.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

/**
 * 本体读切面的<b>写回</b>门禁（#142/B4-3d-8，差距 D-B4-34）。
 *
 * <p><b>缺陷本体</b>：{@code OntologySecurityInterceptor#secureReadResult} 对
 * {@code ApiResponse} 的两种 payload 形态走了两条不同的腿——</p>
 * <ul>
 *   <li>List 形态：过滤结果经 {@code assignData(resp, rows)} 写回 ⇒ 行级/列级/脱敏真的生效；</li>
 *   <li>Map 形态（{@code {data:[…], total, page, size, totalPages}}，即<b>分页读接口的权威形态</b>，
 *       实证 {@code OntologyDataController#listData} 的 {@code GET /api/v1/ontology/data}）：
 *       RLS 过滤、CLS 裁剪、脱敏全部作用在 {@code copyMap(payload)} 出来的<b>副本</b>上，
 *       副本<b>从未写回</b> {@code resp} ⇒ 三通道对该形态结构性恒等于关闭，
 *       页面拿到的是未过滤的全量行。</li>
 * </ul>
 *
 * <p>Map 形态之所以比 List 形态更危险：它是 27 个本体 Controller 里所有分页列表的返回形状，
 * 而切面在这条腿上的失败是<b>静默</b>的（不抛错、不告警、审计照发）。</p>
 *
 * <p><b>本类的判据取向</b>：钉的是「裁决结论必须出现在对外应答里」这个端到端事实，
 * 而不是某个私有方法的返回值——私有方法返回值再正确，丢在副本里同样是渗漏。
 * 反向对照（List 形态、匿名形态）证明门禁不是「见到 Map 就红」。</p>
 *
 * <p>诚实边界一：本类用 {@link Proxy} 造 {@code HttpServletRequest}（GET）＋Mockito 造
 * {@code Authentication} 来搭切面依赖的请求/登录态，禁为本批给 ontology-impl 加
 * {@code spring-test} 依赖（构建文件属高冲突共享面）。</p>
 *
 * <p>诚实边界二：写回后 {@code total} 的口径只可能在「页内行数」与「库侧全量」之间二选一，
 * 两者都不是「RLS 过滤后的全量行数」——真解需 DAO 侧带 RLS 的 count 下推（与本体 RLS
 * 参数化下推同一依赖）。本批选择「仅在实际做过行级过滤时改写 {@code total}」：
 * 已登录且过滤＝fail-closed 少报，匿名未过滤＝保持原口径不变（零行为变更）。
 * 分页元数据退化作为 D-B4-34 子项登记，不在本批冒充解掉。</p>
 *
 * <p><b>#142/B4-3d-9 追加（差距 D-B4-35）</b>：顶层写回接通后，脱敏的<b>嵌套腿</b>仍是
 * fail-open，且 {@code maskMapInPlace} 实际上坏在两处——</p>
 * <ul>
 *   <li>递归腿：对 {@code copyMap(sub)} 的一次性副本递归自身，掩码结果随副本丢弃
 *       ⇒ 信封里的嵌套 Map／List 字段（{@code meta.phone}、{@code history[].email}）对外仍是原文；</li>
 *   <li>顶层腿：{@code it.remove()} 后紧跟 {@code map.put(key, …)} 在 LinkedHashMap 迭代中
 *       构成结构性修改 ⇒ 该键之后只要还有元素就抛 {@code ConcurrentModificationException}，
 *       被 {@code secureReadResult} 外层 {@code catch (Exception)}「不影响主流程」静默吞掉，
 *       <b>连同 D-B4-34 刚接通的写回一起跳过</b>——即"多掩一个字段"反而把行/列过滤全丢掉。</li>
 * </ul>
 * <p>本类尾段把两条腿与「同一字段不得被掩两遍」一起钉住：{@code maskPhone} 并不幂等
 * （{@code 138****5678} 再过一遍会变成 {@code 138*****5678}），所以修法的判据是
 * 「一次递归遍历到位」，不是「再多补一遍脱敏」。</p>
 */
@DisplayName("本体读切面：Map 形态过滤结果必须写回对外应答（D-B4-34／嵌套脱敏腿 D-B4-35）")
class OntologyReadAspectWriteBackTest {

    private static final String INTERCEPTOR_SRC =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/security/OntologySecurityInterceptor.java";

    private static final String TABLE = "ecos_ontology_data";
    private static final String USER = "u-1";

    @BeforeEach
    void setUpContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(getRequest("GET")));
        // 登录态：principal 非 String 时 currentUserId() 走 getName()
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(USER);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // ═══════════════ 缺陷本体：Map 形态三通道 ═══════════════

    @Test
    @DisplayName("Map 形态·RLS — 非授权行不得留在对外应答的 data 里")
    void mapShapedPagePayloadCarriesFilteredRows() {
        ApiResponse<Map<String, Object>> resp = pagedPayload(
                List.of(row("t-7", "合同"), row("t-8", "发票")));
        SecurityEngineClient client = deciding("tenant_id = 't-7'", List.of("tenant_id", "name"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        List<?> data = dataOf(resp);
        assertEquals(1, data.size(), "RLS 只许留 t-7，data 却给了 " + data.size() + " 行＝写回缺失回潮");
        assertEquals("t-7", ((Map<?, ?>) data.get(0)).get("tenant_id"));
        assertEquals(1, ((Number) resp.getData().get("total")).intValue(),
                "已做过行级过滤时 total 取 fail-closed 少报口径（钉住选型，非认可分页元数据）");
    }

    @Test
    @DisplayName("Map 形态·CLS — 列裁剪结论必须出现在写回的行里")
    void mapShapedPagePayloadCarriesTrimmedColumns() {
        ApiResponse<Map<String, Object>> resp = pagedPayload(
                new ArrayList<>(List.of(row("t-7", "合同"))));
        SecurityEngineClient client = deciding("1=1", List.of("tenant_id"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        Map<?, ?> only = (Map<?, ?>) dataOf(resp).get(0);
        assertEquals(List.of("tenant_id"), List.copyOf(only.keySet()),
                "CLS 允许的列只有 tenant_id，写回的行却带 " + only.keySet());
    }

    @Test
    @DisplayName("Map 形态·脱敏 — phone 必须已掩码（脱敏同属被丢弃的一腿）")
    void mapShapedPagePayloadCarriesMaskedValues() {
        Map<String, Object> r = row("t-7", "合同");
        r.put("phone", "13812345678");
        ApiResponse<Map<String, Object>> resp = pagedPayload(new ArrayList<>(List.of(r)));
        SecurityEngineClient client = deciding("1=1", List.of("tenant_id", "name", "phone"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        Map<?, ?> only = (Map<?, ?>) dataOf(resp).get(0);
        assertEquals("138****5678", String.valueOf(only.get("phone")),
                "对外应答里的 phone 仍是原文＝脱敏随副本一起被丢弃");
    }

    @Test
    @DisplayName("Map 形态·拒绝态 — RLS 判 1=0 时 data 必须是空集且 total 同步归零")
    void mapShapedPagePayloadHonoursDenyClause() {
        ApiResponse<Map<String, Object>> resp = pagedPayload(
                List.of(row("t-7", "合同"), row("t-8", "发票")));
        SecurityEngineClient client = deciding(RlsExecutionContract.DENY_CLAUSE,
                List.of("tenant_id", "name"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertTrue(dataOf(resp).isEmpty(), "拒绝态写回了 " + dataOf(resp).size() + " 行");
        assertEquals(0, ((Number) resp.getData().get("total")).intValue(),
                "行集已空而 total 仍报原值＝口径不自洽");
    }

    // ═══════════════ 反向对照：判据不是「见到 Map 就红」 ═══════════════

    @Test
    @DisplayName("List 形态（既有正常腿）— 写回一直有效，本批不得改坏")
    void listShapedPayloadKeepsFiltering() {
        ApiResponse<List<Map<String, Object>>> resp = ApiResponse.success(
                new ArrayList<>(List.of(row("t-7", "合同"), row("t-8", "发票"))));
        SecurityEngineClient client = deciding("tenant_id = 't-7'", List.of("tenant_id", "name"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertEquals(1, resp.getData().size(), "List 形态本就以写回，回归即说明切面被改歪");
    }

    @Test
    @DisplayName("匿名读（无登录态）— 不裁决行/列，但 total 保持库侧口径不被顺手改写")
    void anonymousReadKeepsEnvelopeCountsButStillMasks() {
        SecurityContextHolder.clearContext();
        Map<String, Object> r = row("t-7", "合同");
        r.put("phone", "13812345678");
        ApiResponse<Map<String, Object>> resp = pagedPayload(new ArrayList<>(List.of(r)));
        SecurityEngineClient client = mock(SecurityEngineClient.class);

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertEquals(2, ((Number) resp.getData().get("total")).intValue(),
                "未发生行级过滤时改写 total＝把分页元数据无端改成页内行数（零安全收益的行为变更）");
        Map<?, ?> only = (Map<?, ?>) dataOf(resp).get(0);
        assertEquals("138****5678", String.valueOf(only.get("phone")),
                "脱敏与登录态无关，必须随写回生效");
    }

    @Test
    @DisplayName("空行集（真无数据）— 切面不介入也不报错（防把空集判成渗漏）")
    void emptyPagePayloadIsLeftAlone() {
        ApiResponse<Map<String, Object>> resp = ApiResponse.success(pageEnvelope(List.of(), 0));
        SecurityEngineClient client = mock(SecurityEngineClient.class);

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertTrue(dataOf(resp).isEmpty());
        assertEquals(0, ((Number) resp.getData().get("total")).intValue());
    }

    // ═══════════════ D-B4-35：脱敏的嵌套腿 ═══════════════

    @Test
    @DisplayName("Map 形态·嵌套信封（D-B4-35）— meta.phone/idcard 不得随副本被丢弃")
    void envelopeNestedMapFieldIsMasked() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("phone", "13812345678");
        meta.put("idcard", "110101199003078888");
        Map<String, Object> envelope = pageEnvelope(
                new ArrayList<>(List.of(row("t-7", "合同"))), 1);
        envelope.put("meta", meta);
        ApiResponse<Map<String, Object>> resp = ApiResponse.success(envelope);
        SecurityEngineClient client = deciding("1=1", List.of("tenant_id", "name"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        Map<?, ?> outMeta = (Map<?, ?>) resp.getData().get("meta");
        assertEquals("138****5678", String.valueOf(outMeta.get("phone")),
                "信封里嵌套 Map 的 phone 仍是原文＝脱敏递归腿作用在被丢弃的副本上");
        assertEquals("1101**********8888", String.valueOf(outMeta.get("idcard")));
    }

    @Test
    @DisplayName("Map 形态·信封内 List（D-B4-35）— history[].email 必须已掩码")
    void envelopeNestedListOfMapsIsMasked() {
        Map<String, Object> envelope = pageEnvelope(
                new ArrayList<>(List.of(row("t-7", "合同"))), 1);
        envelope.put("history", new ArrayList<>(List.of(Map.of("email", "tom@corp.com"))));
        ApiResponse<Map<String, Object>> resp = ApiResponse.success(envelope);
        SecurityEngineClient client = deciding("1=1", List.of("tenant_id", "name"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        List<?> history = (List<?>) resp.getData().get("history");
        assertEquals("t***@corp.com",
                String.valueOf(((Map<?, ?>) history.get(0)).get("email")),
                "List 元素里的 email 仍是原文＝同一缺陷的另一条腿");
    }

    @Test
    @DisplayName("Map 形态·D-B4-35 的更硬一面 — 掩一个顶层字段不得把行/列过滤一起丢掉")
    void maskedTopLevelFieldMustNotAbortTheWriteBack() {
        Map<String, Object> r7 = row("t-7", "合同");
        r7.put("phone", "13812345678");
        r7.put("contact", new LinkedHashMap<>(Map.of("email", "tom@corp.com")));
        ApiResponse<Map<String, Object>> resp = pagedPayload(
                new ArrayList<>(List.of(r7, row("t-8", "发票"))));
        SecurityEngineClient client = deciding("tenant_id = 't-7'",
                List.of("tenant_id", "name", "phone", "contact"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        List<?> data = dataOf(resp);
        assertEquals(1, data.size(),
                "行过滤结论又没了＝脱敏腿抛出的异常被外层 catch 静默吞掉，写回整段被跳过");
        Map<?, ?> only = (Map<?, ?>) data.get(0);
        assertEquals("138****5678", String.valueOf(only.get("phone")));
        assertEquals("t***@corp.com",
                String.valueOf(((Map<?, ?>) only.get("contact")).get("email")),
                "行内嵌套 email 必须已掩码");
    }

    @Test
    @DisplayName("反向对照（D-B4-35 修法边界）— 同一字段只许掩一遍：maskPhone 不幂等")
    void sameFieldMustNotBeMaskedTwice() {
        Map<String, Object> r = row("t-7", "合同");
        r.put("phone", "13812345678");
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("phone", "13812345678");
        r.put("contact", contact);
        ApiResponse<Map<String, Object>> resp = ApiResponse.success(
                pageEnvelope(new ArrayList<>(List.of(r)), 1));
        SecurityEngineClient client = deciding("1=1",
                List.of("tenant_id", "name", "phone", "contact"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        Map<?, ?> only = (Map<?, ?>) dataOf(resp).get(0);
        assertEquals("138****5678", String.valueOf(only.get("phone")),
                "被掩两遍会成 138*****5678＝修法把脱敏补成双通道，不是把一条通道修对");
        assertEquals("138****5678",
                String.valueOf(((Map<?, ?>) only.get("contact")).get("phone")),
                "行内嵌套字段本来就该掩（本例在 D-B4-35 收口前即成立＝证明判据不误报）");
    }

    // ═══════════════ D-B4-26 余项：不可用态的对外信封 ═══════════════

    @Test
    @DisplayName("Map 形态·不可用（D-B4-26 余项）— 503 信封 + data 清空，未过滤行一律不外流")
    void unavailableRlsLegProducesSecurityUnavailableEnvelope() {
        ApiResponse<Map<String, Object>> resp = pagedPayload(
                List.of(row("t-7", "合同"), row("t-8", "发票")));
        SecurityEngineClient client = mock(SecurityEngineClient.class);
        when(client.applyRlsExecution(anyString(), anyString(), any()))
                .thenReturn(RlsExecutionContract.Execution.unavailable("table=" + TABLE + "(connection refused)"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getCode(),
                "「security 进程没答」仍报成功码＝折叠没解除");
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_CODE, resp.getErrorCode(),
                "对外必须带机读码，告警规则才能按码寻址");
        Object payload = resp.getData();
        assertTrue(payload instanceof List<?> emptyList && emptyList.isEmpty(),
                "503 只改码不清 data＝客户端按 200 读成功的一腿仍在渗漏: " + payload);
        assertTrue(String.valueOf(resp.getMessage())
                        .contains(RlsExecutionContract.REASON_SERVICE_UNAVAILABLE),
                "信封文案需指名单源理由码，禁写成「无数据」: " + resp.getMessage());
    }

    @Test
    @DisplayName("List 形态·不可用（D-B4-26 余项）— 两条 payload 腿都不许留原行集")
    void unavailableRlsLegRewritesListShapedPayloadToo() {
        ApiResponse<List<Map<String, Object>>> resp = ApiResponse.success(
                new ArrayList<>(List.of(row("t-7", "合同"), row("t-8", "发票"))));
        SecurityEngineClient client = mock(SecurityEngineClient.class);
        when(client.applyRlsExecution(anyString(), anyString(), any()))
                .thenReturn(RlsExecutionContract.Execution.unavailable("table=" + TABLE + "(timeout)"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getCode());
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_CODE, resp.getErrorCode());
        assertTrue(resp.getData().isEmpty(), "不可用态返回了 " + resp.getData().size() + " 行未过滤数据");
    }

    @Test
    @DisplayName("反向对照 — 策略拒绝仍是 200＋空行（判据不是「空结果就红」，两态必须可分）")
    void policyDenyKeepsSuccessEnvelopeWithEmptyRows() {
        ApiResponse<Map<String, Object>> resp = pagedPayload(
                List.of(row("t-7", "合同"), row("t-8", "发票")));
        SecurityEngineClient client = deciding(RlsExecutionContract.DENY_CLAUSE,
                List.of("tenant_id", "name"));

        new OntologySecurityInterceptor(client).secureReadResult(resp);

        assertEquals(0, resp.getCode(), "把「你没权限」打成 503＝用运维告警掩盖授权结论");
        assertTrue(dataOf(resp).isEmpty());
    }

    @Test
    @DisplayName("源码判据（D-B4-26 余项）— 不可用码面单源，切面内禁手抄 ECOS-SEC-501；判据须能变红")
    void unavailableEnvelopeUsesSingleSourceCodeFace() throws IOException {
        String src = stripComments(Files.readString(repoFile(INTERCEPTOR_SRC), StandardCharsets.UTF_8));

        assertTrue(src.contains("RlsExecutionContract.SEC_UNAVAILABLE_HTTP"),
                "D-B4-26 余项回潮：503 码面又回到本地字面量");
        assertTrue(src.contains("markSecurityUnavailable(resp, rls.reason())"),
                "D-B4-26 余项回潮：不可用态没有落到对外信封（重新折成 200＋空行）");
        assertFalse(src.contains("\"" + RlsExecutionContract.SEC_UNAVAILABLE_CODE + "\""),
                "机读码在本类出现第二次即两套检索口径");

        String foldedShape = stripComments("""
                RlsOutcome rls = applyRlsRemote(tableName, userId, rows);
                rows = applyClsRemote(tableName, userId, rls.rows());
                dataMap.put("data", rows);
                """);
        assertFalse(foldedShape.contains("markSecurityUnavailable"),
                "判据必须能变红：折叠原形里定位不到「信封改写」这一腿＝假门禁");
        assertTrue(foldedShape.contains("rls.rows()"),
                "判据必须能变红：折叠原形里没有 rows() 直取＝定位失败");
    }

    // ═══════════════ 静态判据（防「行为用例外壳还在、写回被摘掉」） ═══════════════

    @Test
    @DisplayName("源码判据 — Map 分支必须以 assignData 收尾；判据自身须能变红")
    void mapBranchMustWriteBackAndThePredicateItselfIsNotVacuous() throws IOException {
        String src = stripComments(Files.readString(repoFile(INTERCEPTOR_SRC), StandardCharsets.UTF_8));
        String mapBranch = mapBranchOf(src);

        assertTrue(mapBranch.contains("assignData(resp, dataMap)"),
                "D-B4-34 回潮：Map 分支裁完又丢掉副本＝行/列/脱敏三通道对外应答恒失效:\n" + mapBranch);
        assertFalse(mapBranch.isBlank(), "Map 分支定位失败＝判据空转");

        String preFixShape = stripComments("""
                } else if (payload instanceof Map<?, ?>) {
                    Map<String, Object> dataMap = copyMap((Map<?, ?>) payload);
                    dataMap.put("data", rows);
                    maskMapInPlace(dataMap);
                }
                """);
        assertTrue(mapBranchOf(preFixShape).contains("copyMap((Map<?, ?>) payload)"),
                "判据对缺陷原形定位不到＝假门禁");
        assertFalse(mapBranchOf(preFixShape).contains("assignData(resp, dataMap)"),
                "判据必须能变红：收口前的形状里不允许出现写回");
    }

    @Test
    @DisplayName("源码判据（D-B4-35）— 脱敏递归腿不得作用于一次性副本；判据自身须能变红")
    void nestedMaskLegsMustNotOperateOnThrowawayCopies() throws IOException {
        String src = stripComments(Files.readString(repoFile(INTERCEPTOR_SRC), StandardCharsets.UTF_8));

        assertFalse(src.contains("maskMapInPlace(copyMap("),
                "D-B4-35 回潮：在 copyMap 出来的副本上递归脱敏＝掩码结果连同副本一起被丢弃");
        assertFalse(src.contains("it.remove()"),
                "D-B4-35 的顶层腿回潮：迭代中 remove+put 造成结构性修改＝CME 被外层 catch 吞掉＝写回整段跳过");

        String defectShape = stripComments("""
                if (value instanceof String s) {
                    String key = e.getKey();
                    it.remove();
                    map.put(key, applyMask(s, rule));
                } else if (value instanceof Map<?, ?> sub) {
                    maskMapInPlace(copyMap(sub));
                } else if (value instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            maskMapInPlace(copyMap(m));
                        }
                    }
                }
                """);
        assertTrue(defectShape.contains("maskMapInPlace(copyMap("),
                "判据必须能变红：缺陷原形里定位不到递归腿＝假门禁");
        assertTrue(defectShape.contains("it.remove()"),
                "判据必须能变红：缺陷原形里定位不到顶层腿＝假门禁");
    }

    // ═══════════════ 夹具 ═══════════════

    /**
     * Map 分支体的定位窗口：从 {@code payload instanceof Map} 起，到本方法第一个
     * {@code catch (Exception e)} 止。取「到 catch 为止」而非按括号配平，
     * 是因为该窗口一旦越界就会把 {@code applyRlsRemote} 等无关方法也纳入判据面（假红）。
     */
    private static String mapBranchOf(String src) {
        int at = src.indexOf("payload instanceof Map<?, ?>");
        if (at < 0) return "";
        int end = src.indexOf("catch (Exception e)", at);
        return end < 0 ? src.substring(at) : src.substring(at, end);
    }

    private static SecurityEngineClient deciding(String clause, List<String> visibleColumns) {
        SecurityEngineClient client = mock(SecurityEngineClient.class);
        // #142/B4-3d-10：切面消费的是带态出口，桩必须停在 Execution 上。
        // 停在折叠后的 String 上＝把四态重新压成一个字面量，正是被收口的缺陷本身。
        when(client.applyRlsExecution(anyString(), anyString(), any())).thenReturn(executionOf(clause));
        when(client.filterColumns(anyString(), anyList())).thenReturn(new ArrayList<>(visibleColumns));
        return client;
    }

    /** 片段字符串 → 执行契约读数（与生产者应答同一形状）。 */
    private static RlsExecutionContract.Execution executionOf(String clause) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(RlsExecutionContract.KEY_WHERE_CLAUSE, clause);
        return RlsExecutionContract.read(data, "table=" + TABLE)
                .rejectForUnbindableChannel("table=" + TABLE);
    }

    private static Map<String, Object> row(String tenantId, String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("objectTypeId", TABLE);
        r.put("tenant_id", tenantId);
        r.put("name", name);
        return r;
    }

    private static ApiResponse<Map<String, Object>> pagedPayload(List<Map<String, Object>> rows) {
        return ApiResponse.success(pageEnvelope(rows, 2));
    }

    private static Map<String, Object> pageEnvelope(List<Map<String, Object>> rows, int total) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", new ArrayList<>(rows));
        envelope.put("total", total);
        envelope.put("page", 1);
        envelope.put("size", 20);
        envelope.put("totalPages", 1);
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> dataOf(ApiResponse<Map<String, Object>> resp) {
        Object data = resp.getData().get("data");
        assertTrue(data instanceof List<?>, "data 键形态漂移，本用例判据失效: " + data);
        return (List<Object>) data;
    }

    /** 无 spring-test 依赖的最小 {@code HttpServletRequest}：只用得到 {@code getMethod()}。 */
    private static HttpServletRequest getRequest(String method) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                OntologyReadAspectWriteBackTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, proxyMethod, args) -> "getMethod".equals(proxyMethod.getName())
                        ? method : defaultValueFor(proxyMethod.getReturnType()));
    }

    private static Object defaultValueFor(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        return 0;
    }

    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到切面源码（判据两侧必须都在，禁跳过校验）: " + repoRelative);
    }

    private static String stripComments(String src) {
        return src.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }
}
