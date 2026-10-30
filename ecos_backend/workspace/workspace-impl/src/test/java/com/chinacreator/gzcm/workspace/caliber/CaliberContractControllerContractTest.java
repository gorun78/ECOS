package com.chinacreator.gzcm.workspace.caliber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.caliber.CaliberOwnerClient.OwnerReply;
import com.chinacreator.gzcm.workspace.exception.CaliberApproverIdentityMissingException;
import com.chinacreator.gzcm.workspace.exception.CaliberFormulaInvalidException;
import com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerFailureException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException;
import com.chinacreator.gzcm.workspace.exception.CaliberStatePreconditionException;
import com.chinacreator.gzcm.workspace.exception.CaliberWriteNotLiveException;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceDomainException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import com.chinacreator.gzcm.workspace.security.RlsFilter;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口径契约承流面验收（#146ac 附录A 第 31 条读面三枚 + #146ad 写面四枚，
 * 契约行 = 详设-12 §12.4.2 line 8248~8255）。
 *
 * <p>形态 = 窄 MockMvc（standaloneSetup + {@code WorkspaceExceptionHandler} 单源 advice，
 * 与 {@code BacktestContractTest} 同法）：七滤波器已由 {@code /api/v1/workspace/**} 既有族统一覆盖，
 * 本批不新增登记。编排面用属主替身 ⇒ 纯内存无 DB、无网络；委托客户端自身的信封/传输分态
 * 另由 {@link RecordingRest}（真实子类，不是被验对象的 mock）逐条钉住。</p>
 *
 * <p><b>判定不翻转的机读见证</b>：写面逐枚按真实形态分档钉死——create/approve 钉「属主真值腿」
 * （含出网凭证中继与审批主语来源），submit/supersede 钉「前置腿做实 + 落库腿显式 501 +
 * 逐条可复验 missing」；读面成功腿的真欠列逐枚钉「显式 null + {@code *Applicable=false} +
 * 原因单源键在位」，禁 200 伪装契约出参面齐备（X-22/X-43/X-44）。</p>
 */
@DisplayName("calibers 读写七枚（第31条）契约承流面")
class CaliberContractControllerContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 凭证载体是线程绑定的：本类既有走 MockMvc 派发（自动绑定/解绑），也有直接绑
     * {@code RequestContextHolder} 的出网中继腿。测试失败于中途时上下文不会自己清，
     * 会把上一位调用方的身份泄给下一条用例——那正是生产侧禁止的载体复用形态。
     */
    @AfterEach
    void resetCallerContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── 装配 ──

    /**
     * 读/写两面接在同一对替身上：RLS 咽喉与审计出口必须同源，
     * 禁两面各配一套安全客户端（那样 403 腿在两面可以有不同的拒绝成因）。
     */
    private static MockMvc mvc(CaliberOwnerClient owner, WorkspaceSecurityEngineClient security) {
        CaliberAddressing addressing = new CaliberAddressing(owner);
        ScenarioReadGuard guard = new ScenarioReadGuard(new AbacQueryFilter(security));
        CaliberReadService readService = new CaliberReadService(owner, guard, addressing);
        CaliberWriteService writeService = new CaliberWriteService(owner, guard, addressing, readService,
                new WorkspaceSecurityAudit(security));
        return MockMvcBuilders.standaloneSetup(new CaliberContractController(readService, writeService))
                .setControllerAdvice(new WorkspaceExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }

    private static CaliberOwnerClient ownerMock() {
        return mock(CaliberOwnerClient.class);
    }

    private static WorkspaceSecurityEngineClient rlsAllowAll() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap())).thenReturn(RlsFilter.unrestricted());
        return c;
    }

    private static WorkspaceSecurityEngineClient rlsDeny() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap()))
                .thenReturn(RlsFilter.deny("DENY", true, List.of()));
        return c;
    }

    private static WorkspaceSecurityEngineClient rlsUnavailable() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap()))
                .thenThrow(new SecurityEngineUnavailableException("rls/rows", "process down", null));
        return c;
    }

    private static MvcResult get(MockMvc mvc, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url)).andReturn();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(MvcResult r) throws Exception {
        return MAPPER.readValue(r.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(MvcResult r) throws Exception {
        return (Map<String, Object>) json(r).get("data");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> data) {
        return (List<Map<String, Object>>) data.get("items");
    }

    private static MvcResult post(MockMvc mvc, Map<String, Object> body) throws Exception {
        return postAt(mvc, "/api/v1/workspace/calibers/validate", body, null);
    }

    /** 写面出网腿 builder：{@code bearer} 为 null 即「入口不带任何凭证」——审批主语的缺失态要能测出来。 */
    private static MvcResult postAt(MockMvc mvc, String path, Map<String, Object> body, String bearer)
            throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(writeJson(body));
        }
        if (bearer != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mvc.perform(builder).andReturn();
    }

    private static String writeJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ── 契约面形态与状态行保真 ──

    @Test
    @DisplayName("契约路径逐字：/api/v1/workspace/calibers × 七枚（读三 + 写四）路径段与 operationId 全对齐")
    void contractPathsAreVerbatim() {
        org.springframework.web.bind.annotation.RequestMapping classMapping =
                CaliberContractController.class
                        .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(classMapping, "缺类级 @RequestMapping");
        assertEquals(List.of("/api/v1/workspace/calibers"), List.of(classMapping.value()),
                "契约路径段必须落在 /api/v1/workspace/calibers（类级独占前缀）");

        Map<String, Method> byName = new LinkedHashMap<>();
        for (Method m : declaredEndpoints()) {
            byName.put(m.getName(), m);
        }
        assertEquals(7, byName.size(), "第31条七枚 = 读三枚 + 写四枚，禁缺禁夹带");
        assertNotNull(byName.get("listCalibers")
                        .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class),
                "listCalibers 必须是裸 @GetMapping（类级前缀即全路径）");
        assertEquals(2, byName.get("listCalibers").getParameterCount(), "参面 = code + status，二者均可空");
        Method getCaliber = byName.get("getCaliber");
        assertEquals(List.of("/{id}"),
                List.of(getCaliber.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value()),
                "getCaliber 按契约 = 类级前缀 + /{id}");
        // @PathVariable 挂在方法参数上（Method.getAnnotation 恒 null）⇒ 判据只查参数注解面
        org.springframework.web.bind.annotation.PathVariable pathVar =
                firstAnnotation(getCaliber.getParameterAnnotations(),
                        org.springframework.web.bind.annotation.PathVariable.class);
        assertNotNull(pathVar, "getCaliber 参数面必须带 @PathVariable");
        assertEquals("id", pathVar.value(), "id 基寻址的形参名逐字 = id");
        assertNotNull(byName.get("validateCaliber")
                        .getAnnotation(org.springframework.web.bind.annotation.PostMapping.class),
                "validateCaliber 按契约 = POST /calibers/validate");

        // 写面四枚（#146ad）：路径段逐字钉契约，operationId 名同样逐字（前端/契约册按名对齐）
        assertEquals("", postPath(byName.get("createCaliber")), "createCaliber = 裸 POST 类级前缀");
        assertEquals("/{id}/submit", postPath(byName.get("submitCaliber")));
        assertEquals("/{id}/approve", postPath(byName.get("approveCaliber")));
        assertEquals("/{id}/supersede", postPath(byName.get("supersedeCaliber")));
        for (String writeFace : List.of("submitCaliber", "approveCaliber", "supersedeCaliber")) {
            org.springframework.web.bind.annotation.PathVariable writePathVar =
                    firstAnnotation(byName.get(writeFace).getParameterAnnotations(),
                            org.springframework.web.bind.annotation.PathVariable.class);
            assertNotNull(writePathVar, writeFace + " 必须按契约以路径 id 寻址");
            assertEquals("id", writePathVar.value(), writeFace + " 形参名逐字 = id");
        }
    }

    /** 取 {@code @PostMapping} 的路径段（裸映射返回空串，不做「缺省即 null」的二次约定）。 */
    private static String postPath(Method method) {
        org.springframework.web.bind.annotation.PostMapping mapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        assertNotNull(mapping, "写面端点必须带 @PostMapping: " + method.getName());
        assertTrue(mapping.value().length <= 1,
                method.getName() + " 的路径段必须唯一（同方法挂多路径＝契约路径面失控）");
        return mapping.value().length == 0 ? "" : mapping.value()[0];
    }

    private static <A extends java.lang.annotation.Annotation> A firstAnnotation(
            Annotation[][] parameterAnnotations, Class<A> annotationType) {
        for (Annotation[] row : parameterAnnotations) {
            for (Annotation annotation : row) {
                if (annotationType.isInstance(annotation)) {
                    return annotationType.cast(annotation);
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("状态行保真（D-B4-59 workspace 族「切面只能抛」）：端点只回成功信封，失败面只经领域异常")
    void failureFacesAreExceptionsNotFoldedEnvelopes() {
        for (Method m : declaredEndpoints()) {
            assertEquals(ApiResponse.class, m.getReturnType(),
                    "端点返回型 = 成功信封（本类无 ResponseEntity 出口 ⇒ 失败只能上抛，由 advice 唯一渲染）: "
                            + m.getName());
        }
        // 七枚失败载体与契约错误码列逐枚同号：403 腿另由 F07-18 咽喉承载（见 listRlsDenyIs403）
        assertFace(new CaliberNotFoundException("c1-id", CaliberOwnerClient.OWNER_WINDOW_LIMIT, false),
                404, "CALIBER_NOT_FOUND");
        assertFace(new CaliberFormulaInvalidException(List.of(), "CAL_REV"), 400, "CALIBER_DSL_INVALID");
        assertFace(new CaliberOwnerUnavailableException("list"), 503, "ONTOLOGY_UNAVAILABLE");
        assertFace(new CaliberOwnerFailureException("list", "ECOS-ONTO-050", 500), 500, "ONTOLOGY_FAILURE");
        assertFace(new CaliberStatePreconditionException("c1-id", "CAL_REV", "1", "APPROVED",
                List.of("DRAFT", "REVIEWING")), 409, "CALIBER_STATE_PRECONDITION");
        assertFace(new CaliberApproverIdentityMissingException("c1-id"), 403, "APPROVER_IDENTITY_MISSING");
        assertFace(new CaliberWriteNotLiveException("submitCaliber", "/p", List.of("x")),
                501, "STUB_CAPABILITY");
    }

    /**
     * 面计数只认真实声明的方法：JaCoCo 离线插桩会往类里注入合成方法 {@code $jacocoInit}，
     * 不过滤则「读面三枚」会数成四枚（本批首跑即因此打红，实测后按此收口）。
     */
    private static List<Method> declaredEndpoints() {
        List<Method> out = new ArrayList<>();
        for (Method m : CaliberContractController.class.getDeclaredMethods()) {
            if (!m.isSynthetic()) {
                out.add(m);
            }
        }
        return out;
    }

    private static void assertFace(WorkspaceDomainException ex, int http, String code) {
        assertEquals(http, ex.getHttpStatus(), "契约状态行: " + code);
        assertEquals(code, ex.getCode(), "机读码: " + http);
    }

    @Test
    @DisplayName("写面四枚路由在位（#146ad）：读面类不吞写面，七枚同基路径单 Controller 独占")
    void writeFaceFourEndpointsAreRoutedHere() {
        List<String> declared = new ArrayList<>();
        for (Method m : declaredEndpoints()) {
            declared.add(m.getName());
        }
        List<String> contract = new ArrayList<>(List.of("listCalibers", "getCaliber", "createCaliber",
                "submitCaliber", "approveCaliber", "supersedeCaliber", "validateCaliber"));
        declared.sort(String::compareTo);
        contract.sort(String::compareTo);
        assertEquals(contract, declared,
                "第31条七枚 = 契约 §12.4.2 方法全集（禁缺枚，也禁静默混进别的面）");
    }

    // ── listCalibers ──

    @Test
    @DisplayName("list 200 真值腿：属主主行列逐枚透传 + 窗口取证在位 + 真欠列显式缺席")
    void listReturnsOwnerTruthWithExplicitAbsentColumns() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(
                headRow("c1-id", "CAL_REV", "APPROVED"),
                headRow("c2-id", "CAL_COST", "DRAFT"))));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers");
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> body = data(r);
        List<Map<String, Object>> rows = items(body);
        assertEquals(2, rows.size());
        assertEquals("CAL_REV", rows.get(0).get("code"));
        assertEquals("万元", rows.get(0).get("unit"));
        assertEquals("CNY", rows.get(0).get("currency"));
        assertEquals("finance_manager", rows.get(0).get("ownerRole"));
        assertEquals("{\"org\":\"dept\"}", rows.get(0).get("dimensionJson"));
        assertEquals(body.get("total"), rows.size(), "total = 实回数，不拿属主窗口上限冒充");
        assertEquals("ontology-owner-rest", body.get("dataSource"), "取数来源标识在位（禁合成值冒充属主真值）");
        assertEquals(CaliberOwnerClient.OWNER_WINDOW_LIMIT, body.get("resolutionWindow"));
        assertEquals(Boolean.FALSE, body.get("windowSaturated"));

        Map<String, Object> first = rows.get(0);
        assertNull(first.get("versionChain"), "list 面版本链真欠，必须显式 null");
        assertEquals(Boolean.FALSE, first.get("versionChainApplicable"));
        assertNull(first.get("approvedBy"), "list 面批准人真欠，禁兜底填");
        assertEquals(Boolean.FALSE, first.get("approvedByApplicable"));
        assertNull(first.get("effectivePeriod"));
        assertEquals(Boolean.FALSE, first.get("effectivePeriodApplicable"));
        @SuppressWarnings("unchecked")
        Map<String, String> reasons = (Map<String, String>) body.get("notApplicableReasons");
        assertEquals(List.of("versionChain", "approvedBy", "effectivePeriod", "pagination"),
                new ArrayList<>(reasons.keySet()), "缺席原因必须逐枚具名（只报空值不分成因＝半谎）");
        assertTrue(reasons.values().stream().allMatch(v -> v != null && !v.isBlank()));
    }

    @Test
    @DisplayName("list 的 status 透传属主、code 是本面过滤腿；触顶时如实报 codeFilterComplete=false")
    void listCodeFilterReportsWindowCompleteness() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(eq("APPROVED"))).thenReturn(okList(List.of(
                headRow("c1-id", "CAL_REV", "APPROVED"))));
        MockMvc mvc = mvc(owner, rlsAllowAll());

        MvcResult hit = get(mvc, "/api/v1/workspace/calibers?code=CAL_REV&status=APPROVED");
        assertEquals(200, hit.getResponse().getStatus());
        verify(owner).listHeadRows("APPROVED");
        assertEquals(1, items(data(hit)).size());
        assertEquals(Boolean.TRUE, data(hit).get("codeFilterComplete"));

        MvcResult miss = get(mvc, "/api/v1/workspace/calibers?code=CAL_ABSENT&status=APPROVED");
        assertEquals(200, miss.getResponse().getStatus(), "过滤未命中＝合法空集，不是 404");
        assertEquals(0, items(data(miss)).size());
        assertEquals(Boolean.TRUE, data(miss).get("codeFilterComplete"),
                "未触顶 ⇒ 窗口内过滤即全集，完整性成立");

        List<Map<String, Object>> saturated = new ArrayList<>();
        for (int i = 0; i < CaliberOwnerClient.OWNER_WINDOW_LIMIT; i++) {
            saturated.add(headRow("id-" + i, "CAL_" + i, "APPROVED"));
        }
        CaliberOwnerClient owner2 = ownerMock();
        when(owner2.listHeadRows(isNull())).thenReturn(okList(saturated));
        Map<String, Object> body = data(get(mvc(owner2, rlsAllowAll()),
                "/api/v1/workspace/calibers?code=CAL_OUTSIDE"));
        assertEquals(Boolean.TRUE, body.get("windowSaturated"));
        assertEquals(Boolean.FALSE, body.get("codeFilterComplete"), "触顶 ⇒ 禁把「窗口内没有」说成「没有」");
        assertEquals(0, items(body).size());
    }

    @Test
    @DisplayName("list 403 腿：RLS 拒绝钉实体编码 caliber_definition，且属主零出网（咽喉在取数之前）")
    void listRlsDenyIs403() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        MvcResult r = get(mvc(owner, rlsDeny()), "/api/v1/workspace/calibers");
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("PERMISSION_DENIED", json(r).get("errorCode"));
        String permission = String.valueOf(data(r).get("permission"));
        assertTrue(permission.endsWith(":caliber_definition"),
                "entityCode 必须钉 caliber_definition，不随场景域默认实体漂移: " + permission);
        verify(owner, never()).listHeadRows(any());
    }

    @Test
    @DisplayName("list 503 腿：security 进程不可用 ⇒ ECOS-SEC-501（不可用≠无权限，禁折 403）")
    void listSecurityUnavailableIs503() throws Exception {
        MvcResult r = get(mvc(ownerMock(), rlsUnavailable()), "/api/v1/workspace/calibers");
        assertEquals(503, r.getResponse().getStatus());
        assertEquals("ECOS-SEC-501", json(r).get("errorCode"));
        assertEquals("security", data(r).get("engine"));
    }

    @Test
    @DisplayName("list 500 腿：属主显式回失败信封（HTTP 恒 200、语义在 code 面）⇒ 真实 500 带 ownerCode 取证")
    void listOwnerFailureEnvelopeIs500() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull()))
                .thenReturn(new OwnerReply("list", 500, "ECOS-ONTO-050", null));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers");
        assertEquals(500, r.getResponse().getStatus(), "属主信封失败码禁冒充 200");
        assertEquals("ONTOLOGY_FAILURE", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals("list", body.get("operation"));
        assertEquals(500, body.get("ownerCode"));
        assertEquals("ECOS-ONTO-050", body.get("ownerErrorCode"));
    }

    @Test
    @DisplayName("list 503 腿：属主不可得 ⇒ ONTOLOGY_UNAVAILABLE + engine=ontology，禁带 items 冒充空集")
    void listOwnerUnavailableIs503() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenThrow(new CaliberOwnerUnavailableException("list"));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers");
        assertEquals(503, r.getResponse().getStatus());
        assertEquals("ONTOLOGY_UNAVAILABLE", json(r).get("errorCode"));
        assertEquals("ontology", data(r).get("engine"));
        assertTrue(!data(r).containsKey("items") && !data(r).containsKey("total"),
                "不可得态禁携带 items/total 键冒充成功空集（X-43）");
    }

    // ── getCaliber ──

    @Test
    @DisplayName("detail 200 真值腿：版本链键面归一 + 当前 APPROVED 版本 + 量纲真实载体 + 四列显式缺席")
    void detailReturnsVersionChainAndExplicitAbsentColumns() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "APPROVED"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "APPROVED");
        head.put("createTime", "2026-01-05T08:00:00");
        head.put("versions", List.of(
                versionRow("PENDING_APPROVAL", 3, null, null),
                versionRow("APPROVED", 2, "u-approve", "2026-09-30T10:00:00"),
                versionRow("SUPERSEDED", 1, "u-approve-old", "2026-06-30T10:00:00")));
        when(owner.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));

        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id");
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> body = data(r);
        assertEquals("c1-id", body.get("id"), "id 基寻址经属主 code 基解析后仍回契约 id 键");
        assertEquals("CAL_REV", body.get("code"));
        verify(owner).detailByCode("CAL_REV");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chain = (List<Map<String, Object>>) body.get("versionChain");
        assertEquals(3, chain.size(), "版本链原样搬运属主行数，不裁剪不合成");
        assertEquals(2, chain.get(1).get("versionNo"));
        assertEquals("u-approve", chain.get(1).get("approvedBy"),
                "库侧 approved_by → 契约 approvedBy（键面归一，不做语义换算）");
        assertEquals("v2@abc123", chain.get(1).get("gitRef"));
        assertNull(chain.get(0).get("approvedBy"), "未批准版本禁兜底填批准人");
        assertEquals(2, body.get("currentVersionNo"), "当前版本 = 版本链首条 APPROVED，不按行数猜");
        assertEquals("u-approve", body.get("approvedBy"));
        assertEquals("2026-09-30T10:00:00", body.get("approvedAt"));

        @SuppressWarnings("unchecked")
        Map<String, Object> dimension = (Map<String, Object>) body.get("dimension");
        assertEquals("万元", dimension.get("unit"));
        assertEquals("CNY", dimension.get("currency"));
        assertEquals(1, dimension.get("additive"));
        assertEquals("MONTH", dimension.get("periodGranularity"));

        @SuppressWarnings("unchecked")
        Map<String, String> reasons = (Map<String, String>) body.get("notApplicableReasons");
        assertEquals(List.of("effectivePeriod", "dslRenderedText", "symbolTable", "derivedDimension"),
                new ArrayList<>(reasons.keySet()));
        assertNull(body.get("effectivePeriod"));
        assertEquals(Boolean.FALSE, body.get("effectivePeriodApplicable"));
        assertNull(body.get("dslRenderedText"));
        assertEquals(Boolean.FALSE, body.get("dslRenderedTextApplicable"));
        assertNull(body.get("symbolTable"));
        assertEquals(Boolean.FALSE, body.get("symbolTableApplicable"));
        assertNull(body.get("derivedDimension"));
        assertEquals(Boolean.FALSE, body.get("derivedDimensionApplicable"));
    }

    @Test
    @DisplayName("detail 404 腿（id 解析落空）：携 caliberId + 解析窗口 + 触顶标记，禁伪装 200 空对象")
    void detailUnknownIdIs404WithWindowEvidence() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "APPROVED"))));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/no-such-id");
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("CALIBER_NOT_FOUND", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals("no-such-id", body.get("caliberId"));
        assertEquals(CaliberOwnerClient.OWNER_WINDOW_LIMIT, body.get("resolutionWindow"));
        assertEquals(Boolean.FALSE, body.get("windowSaturated"), "未触顶 ⇒ 该 id 确实不在属主全集内");
        verify(owner, never()).detailByCode(any());
    }

    @Test
    @DisplayName("detail 404 触顶形态：窗口满 500 仍未命中 ⇒ windowSaturated=true（「没查到」≠「不存在」的机读区别）")
    void detailMissWithSaturatedWindowMarksSaturation() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < CaliberOwnerClient.OWNER_WINDOW_LIMIT; i++) {
            rows.add(headRow("row-" + i, "CAL_" + i, "APPROVED"));
        }
        when(owner.listHeadRows(isNull())).thenReturn(okList(rows));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/absent-id");
        assertEquals(404, r.getResponse().getStatus());
        assertEquals(Boolean.TRUE, data(r).get("windowSaturated"),
                "触顶 ⇒ 只知「窗口内未命中」，禁据此断言口径不存在");
    }

    @Test
    @DisplayName("detail 404 腿（属主详情面回 404）：主行见过而详情面说没有 = 并发软删，按 404 不冒充 500")
    void detailOwnerNotFoundIs404() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "APPROVED"))));
        when(owner.detailByCode("CAL_REV"))
                .thenReturn(new OwnerReply("detail", OwnerReply.CODE_NOT_FOUND, "CAL-404", null));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id");
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("CALIBER_NOT_FOUND", json(r).get("errorCode"));
        assertEquals(Boolean.FALSE, data(r).get("windowSaturated"));
    }

    @Test
    @DisplayName("detail 契约漂移腿：主行有 id 无 code ⇒ 503 不可得（禁拿 null 拼属主详情路径冒充 404）")
    void detailHeadWithoutCodeIs503() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        Map<String, Object> row = headRow("c1-id", null, "APPROVED");
        row.remove("code");
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(row)));
        MvcResult r = get(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id");
        assertEquals(503, r.getResponse().getStatus());
        assertEquals("ONTOLOGY_UNAVAILABLE", json(r).get("errorCode"));
        assertEquals("detail", data(r).get("operation"));
        verify(owner, never()).detailByCode(any());
    }

    // ── validateCaliber ──

    @Test
    @DisplayName("validate 400 腿：DSL 四关违一 ⇒ CALIBER_DSL_INVALID + 逐条违反项 + 四关闸名单源")
    void validateViolationIs400WithGateDetail() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("valid", false);
        vo.put("target", "CAL_REV");
        vo.put("violations", List.of(
                violation("DSL_V2_REFERENCE", "ECOS-ONTO-071", "引用了未登记的事实字段", "gross_amount", 1, 14),
                violation("DSL_V3_DIMENSION", "ECOS-ONTO-072", "量纲不可推导", "net_amount", 2, 3)));
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok", vo));

        MvcResult r = post(mvc(owner, rlsAllowAll()),
                Map.of("code", "CAL_REV", "formula", "gross_amount + ghost_field"));
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("CALIBER_DSL_INVALID", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals(List.of("DSL_V1_SYNTAX", "DSL_V2_REFERENCE", "DSL_V3_DIMENSION", "DSL_V4_CYCLE"),
                body.get("gates"), "四关闸名逐枚在位（前端按 gate 分组的单源词表）");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violations = (List<Map<String, Object>>) body.get("violations");
        assertEquals(2, violations.size(), "逐条违反项原样搬运，禁只报「不通过」");
        assertEquals("gross_amount", violations.get(0).get("symbol"), "契约「符号定位」列必须在位");
        assertEquals(1, violations.get(0).get("line"));
        assertEquals(14, violations.get(0).get("column"));
        assertEquals("CAL_REV", body.get("target"));
    }

    @Test
    @DisplayName("validate 信封自相矛盾腿：属主成功码 + valid=false ⇒ 按载荷报 400，禁跟着信封冒充 200")
    void validateEnvelopeSuccessWithInvalidPayloadIs400() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok",
                Map.of("valid", false, "violations", List.of(
                        violation("DSL_V1_SYNTAX", "ECOS-ONTO-070", "语法非法", "", 1, 1)))));
        MvcResult r = post(mvc(owner, rlsAllowAll()), Map.of("formula", "1 +"));
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("CALIBER_DSL_INVALID", json(r).get("errorCode"));
    }

    @Test
    @DisplayName("validate 200 腿：四关全过才回成功，且 persisted=false 机读声明「只校验不落库」")
    void validateSuccessFace() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("valid", true);
        vo.put("violations", List.of());
        vo.put("target", "CAL_REV");
        vo.put("registryMetricCount", 128);
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok", vo));

        Map<String, Object> body = data(post(mvc(owner, rlsAllowAll()),
                Map.of("code", "CAL_REV", "formula", "revenue - cost")));
        assertEquals(Boolean.TRUE, body.get("valid"));
        assertEquals(List.of(), body.get("violations"));
        assertEquals(128, body.get("registryMetricCount"), "属主 registry 计数原样透传，本面不自行统计");
        assertEquals(Boolean.FALSE, body.get("persisted"), "预检不落库必须显式声明");
        assertEquals("ontology-owner-rest", body.get("dataSource"));
    }

    @Test
    @DisplayName("validate 400 逐字段腿：formula 缺位/空白先行拦下，属主与守卫零调用（不拿空公式空跑属主）")
    void validateWithoutFormulaIs400BeforeOwner() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        MockMvc mvc = mvc(owner, rlsAllowAll());

        MvcResult blank = post(mvc, Map.of("code", "CAL_REV", "formula", "   "));
        assertEquals(400, blank.getResponse().getStatus());
        assertEquals("ACTION_FIELDS_MISSING", json(blank).get("errorCode"));
        assertEquals(List.of("formula"), data(blank).get("missingFields"), "逐字段点名 formula");

        MvcResult emptyBody = mvc.perform(MockMvcRequestBuilders.post("/api/v1/workspace/calibers/validate")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertEquals(400, emptyBody.getResponse().getStatus());
        assertEquals(List.of("formula"), data(emptyBody).get("missingFields"));
        Mockito.verifyNoInteractions(owner);
    }

    @Test
    @DisplayName("validate 白名单腿：只下发 DTO 五字段，无关键不灌进属主；属主非 400 失败码归 500")
    void validateWhitelistsRequestAndMapsOwnerFailure() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok",
                Map.of("valid", true, "violations", List.of())));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("formula", "revenue");
        request.put("code", "CAL_REV");
        request.put("status", "APPROVED");
        request.put("approvedBy", "u-x");
        assertEquals(200, post(mvc(owner, rlsAllowAll()), request).getResponse().getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(owner).validate(captor.capture());
        assertEquals(List.of("code", "formula"), new ArrayList<>(captor.getValue().keySet()),
                "下发面 = 属主 DTO 白名单，多余键丢弃（防把无关字段灌进属主）");

        CaliberOwnerClient owner2 = ownerMock();
        when(owner2.validate(anyMap())).thenReturn(new OwnerReply("validate", 500, "ECOS-ONTO-050", null));
        MvcResult fail = post(mvc(owner2, rlsAllowAll()), Map.of("formula", "revenue"));
        assertEquals(500, fail.getResponse().getStatus());
        Map<String, Object> body = data(fail);
        assertEquals("validate", body.get("operation"));
        assertEquals(500, body.get("ownerCode"));
        assertEquals("ECOS-ONTO-050", body.get("ownerErrorCode"));
    }

    @Test
    @DisplayName("validate 属主 400 信封腿：按载荷判 400（不顶成 500），缺 data 段则归不可得")
    void validateOwnerBadRequestStillMapsTo400() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 400, "ECOS-ONTO-070",
                Map.of("valid", false, "violations", List.of())));
        MvcResult r = post(mvc(owner, rlsAllowAll()), Map.of("formula", "revenue +"));
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("CALIBER_DSL_INVALID", json(r).get("errorCode"));

        CaliberOwnerClient owner2 = ownerMock();
        when(owner2.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok", null));
        MvcResult drift = post(mvc(owner2, rlsAllowAll()), Map.of("formula", "revenue"));
        assertEquals(503, drift.getResponse().getStatus(),
                "成功码却缺 data 段 = 属主契约漂移，禁按 valid=false 猜（形态不符归不可得）");
    }

    // ── 写面四枚（#146ad）──

    @Test
    @DisplayName("create 真值双腿：主行走属主兜底、DRAFT 落版本行、载荷按列面分投、非同一事务显式声明")
    void createBuildsHeadAndFirstDraftVersionLegs() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok",
                Map.of("valid", true, "violations", List.of())));
        when(owner.createHeadRow(anyMap()))
                .thenReturn(new OwnerReply("create", 0, "ok", headRow("c9-id", "CAL_NEW", "ACTIVE")));
        when(owner.addVersion(eq("CAL_NEW"), anyMap()))
                .thenReturn(new OwnerReply("createVersion", 0, "ok", versionRow("DRAFT", 1, null, null)));

        Map<String, Object> request = createRequest();
        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers", request, null);
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> body = data(r);
        assertEquals("c9-id", body.get("caliberId"));
        assertEquals("CAL_NEW", body.get("code"));
        assertEquals("ACTIVE", body.get("headStatus"), "主行状态＝属主 insertCaliber 的兜底真值，本面不顶写 DRAFT");
        assertEquals(1, body.get("versionNo"));
        assertEquals("DRAFT", body.get("versionStatus"), "契约「新口径（DRAFT）」的真实载体是版本行 status");
        assertEquals("DRAFT", body.get("status"), "对外状态字段与版本行同值，禁两套口径");
        assertEquals("versionRow", body.get("draftStatusCarrier"), "DRAFT 落点必须声明，否则前端以为主行是 DRAFT");
        assertEquals(Boolean.FALSE, body.get("atomic"), "两条属主写端点不在同一事务，禁伪装原子");
        assertEquals("季度口径调整", body.get("changeReason"), "入参原样回显（收了什么要说清）");
        assertEquals(Boolean.FALSE, body.get("changeReasonStored"), "属主十六列无变更原因列 ⇒ 未落库");
        assertEquals("dimensionJson", body.get("dimensionMappingCarrier"), "核算映射走主行 dimensionJson 载体");
        assertEquals("ontology-owner-rest", body.get("dataSource"));
        assertEquals(List.of("headStatusDraft", "changeReasonPersistence", "crossCallAtomicity"),
                new ArrayList<>(((Map<String, String>) body.get("writeNotApplicableReasons")).keySet()),
                "写面真欠原因逐枚具名，缺一即整族外推");

        InOrder order = Mockito.inOrder(owner);
        order.verify(owner).validate(anyMap());
        order.verify(owner).createHeadRow(anyMap());
        order.verify(owner).addVersion(eq("CAL_NEW"), anyMap());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headCaptor = ArgumentCaptor.forClass(Map.class);
        verify(owner).createHeadRow(headCaptor.capture());
        assertEquals(List.of("code", "name", "unit", "currency", "ownerRole", "dimensionJson"),
                new ArrayList<>(headCaptor.getValue().keySet()),
                "主行载荷 = 属主 CaliberSaveDTO 列面；公式与变更原因不许灌进主行");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> versionCaptor = ArgumentCaptor.forClass(Map.class);
        verify(owner).addVersion(eq("CAL_NEW"), versionCaptor.capture());
        assertEquals(List.of("formula", "additive", "periodGranularity", "status"),
                new ArrayList<>(versionCaptor.getValue().keySet()),
                "版本行载荷 = 属主 CaliberVersionSaveDTO 列面 + 显式 DRAFT（不靠属主缺省态猜）");
    }

    private static Map<String, Object> createRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("code", "CAL_NEW");
        request.put("name", "营业收入口径");
        request.put("formula", "revenue - cost");
        request.put("unit", "万元");
        request.put("currency", "CNY");
        request.put("ownerRole", "finance_manager");
        request.put("dimensionJson", "{\"org\":\"dept\"}");
        request.put("additive", 1);
        request.put("periodGranularity", "MONTH");
        request.put("changeReason", "季度口径调整");
        return request;
    }

    @Test
    @DisplayName("create 400 逐字段腿：code/name/formula 缺位先行拦下，属主与四关预检零调用")
    void createMissingRequiredFieldsIs400BeforeOwner() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers", Map.of(), null);
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("ACTION_FIELDS_MISSING", json(r).get("errorCode"));
        assertEquals(List.of("code", "name", "formula"), data(r).get("missingFields"),
                "三个必填逐枚点名，禁只回「参数非法」");
        Mockito.verifyNoInteractions(owner);
    }

    @Test
    @DisplayName("create DSL 违一腿：属主 400 信封（data 段缺位形态）承成契约 400，闸名取机读码、属主写腿零调用")
    void createDslRejectionIs400AndOwnerWritesUntouched() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.validate(anyMap()))
                .thenReturn(new OwnerReply("validate", 400, "ECOS-ONTO-070", null));

        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers", createRequest(), null);
        assertEquals(400, r.getResponse().getStatus(),
                "属主写入咽喉的 400 信封禁顶成 500/503（客户端公式错误 ≠ 引擎故障）");
        assertEquals("CALIBER_DSL_INVALID", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals(List.of("DSL_V1_SYNTAX", "DSL_V2_REFERENCE", "DSL_V3_DIMENSION", "DSL_V4_CYCLE"),
                body.get("gates"));
        assertEquals("DSL_V1_SYNTAX", body.get("rejectedGate"), "data 段缺位时闸名取 ECOS-ONTO-07x 机读码");
        assertEquals(Boolean.TRUE, body.get("rejectedGateApplicable"));
        assertNull(body.get("symbolLocation"));
        assertEquals(Boolean.FALSE, body.get("symbolLocationApplicable"),
                "符号定位无结构化载体 ⇒ 显式缺席，禁造一个假定位");
        assertEquals("ECOS-ONTO-070", body.get("ownerErrorCode"));
        assertEquals(List.of(), body.get("violations"));
        verify(owner, never()).createHeadRow(anyMap());
        verify(owner, never()).addVersion(any(), anyMap());
    }

    @Test
    @DisplayName("create 腿二失败＝孤儿主行：状态行如实 503 带 operation=createVersion，禁谎报已回滚、禁静默吞")
    void createSecondLegFailureSurfacesOrphanHead() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.validate(anyMap())).thenReturn(new OwnerReply("validate", 0, "ok",
                Map.of("valid", true, "violations", List.of())));
        when(owner.createHeadRow(anyMap()))
                .thenReturn(new OwnerReply("create", 0, "ok", headRow("c9-id", "CAL_NEW", "ACTIVE")));
        when(owner.addVersion(eq("CAL_NEW"), anyMap()))
                .thenThrow(new CaliberOwnerUnavailableException("createVersion"));

        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers", createRequest(), null);
        assertEquals(503, r.getResponse().getStatus());
        assertEquals("ONTOLOGY_UNAVAILABLE", json(r).get("errorCode"));
        assertEquals("createVersion", data(r).get("operation"), "缺失的是版本腿，不能报成 create 整体失败");
        verify(owner).createHeadRow(anyMap());
        assertTrue(!data(r).containsKey("caliberId"), "失败态禁携带成功面键冒充「已建好」");
    }

    @Test
    @DisplayName("approve 真值腿：入口凭证中继出网、状态与 approvedBy 取属主真值、审计只声明已发起")
    void approveReturnsOwnerTruthWithRelayedIdentity() throws Exception {
        CaliberOwnerClient owner = approvedByOwnerTruth();
        WorkspaceSecurityEngineClient security = rlsAllowAll();
        MvcResult r = postAt(mvc(owner, security), "/api/v1/workspace/calibers/c1-id/approve",
                Map.of(), "Bearer jwt-finance-user");
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> body = data(r);
        assertEquals("c1-id", body.get("caliberId"));
        assertEquals("CAL_REV", body.get("code"));
        assertEquals("1", body.get("versionNo"));
        assertEquals("APPROVED", body.get("status"), "状态行取属主应答真值，本面不自行置态");
        assertEquals("u-approve", body.get("approvedBy"), "审批人由属主从 UserContext 落库，本面不合成主语");
        assertEquals(Boolean.TRUE, body.get("auditEventAttempted"));
        assertEquals(Boolean.FALSE, body.get("auditEventDeliveryConfirmed"),
                "审计出口 fail-soft ⇒ 只可声明已发起，声明已落达即假绿");
        assertEquals("PENDING_APPROVAL", body.get("contractPreconditionStatus"));
        assertEquals(List.of("DRAFT", "REVIEWING"), body.get("observedPreconditionStatuses"),
                "属主可批状态集合与契约词表不同形 ⇒ 两面都摆出来，不做换算");
        assertEquals(List.of("approvalRoleAndOpaGate", "auditEventDelivery", "pendingApprovalState"),
                new ArrayList<>(((Map<String, String>) body.get("writeNotApplicableReasons")).keySet()));

        verify(owner).approveVersion("CAL_REV", "1");
        verify(security).audit("caliber:approve", "CAL_REV@1", "APPROVED");
    }

    private static CaliberOwnerClient approvedByOwnerTruth() {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "ACTIVE");
        head.put("versions", List.of(versionRow("DRAFT", 1, null, null)));
        when(owner.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));
        when(owner.approveVersion("CAL_REV", "1")).thenReturn(new OwnerReply("approve", 0, "ok",
                Map.of("code", "CAL_REV", "versionNo", 1, "status", "APPROVED", "approvedBy", "u-approve")));
        return owner;
    }

    @Test
    @DisplayName("approve 无凭证腿：出网前即 403 APPROVER_IDENTITY_MISSING，属主零调用（禁匿名撞属主 400）")
    void approveWithoutCallerIdentityIs403BeforeEgress() throws Exception {
        CaliberOwnerClient owner = approvedByOwnerTruth();
        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/approve", null, null);
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("APPROVER_IDENTITY_MISSING", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals("c1-id", body.get("caliberId"));
        assertEquals("Authorization 或 X-ECOS-USER", body.get("requiredCarriers"));
        assertEquals(Boolean.FALSE, body.get("ownerCallExecuted"));
        Mockito.verifyNoInteractions(owner);
    }

    @Test
    @DisplayName("approve 409 腿：点名版本行不在可批集合 ⇒ 出网前判 409，属主审批腿零调用（不拿属主 400 当状态机）")
    void approvePreconditionIs409BeforeEgress() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "ACTIVE");
        head.put("versions", List.of(versionRow("APPROVED", 2, "u-old", "2026-09-30T10:00:00")));
        when(owner.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));

        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/approve",
                Map.of("versionNo", 2), "Bearer jwt-finance-user");
        assertEquals(409, r.getResponse().getStatus());
        assertEquals("CALIBER_STATE_PRECONDITION", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals("2", body.get("versionNo"), "点名的版本号要在取证里回显");
        assertEquals("APPROVED", body.get("observedStatus"), "实际观测态要摆出来，禁只说「前置不成立」");
        assertEquals(List.of("DRAFT", "REVIEWING"), body.get("expectedStatuses"));
        assertEquals("CAL_REV", body.get("caliberCode"));
        verify(owner, never()).approveVersion(any(), any());

        // 缺省寻址（不带 versionNo）时版本链里无可批行 ⇒ 同样 409，观测态显式缺席（禁兜一个状态词）
        CaliberOwnerClient owner2 = ownerMock();
        when(owner2.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> approvedHead = headRow("c1-id", "CAL_REV", "ACTIVE");
        approvedHead.put("versions", List.of(versionRow("APPROVED", 1, "u-old", "2026-09-30T10:00:00")));
        when(owner2.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", approvedHead));
        MvcResult noApprovable = postAt(mvc(owner2, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/approve",
                Map.of(), "Bearer jwt-finance-user");
        assertEquals(409, noApprovable.getResponse().getStatus());
        assertNull(data(noApprovable).get("versionNo"),
                "缺省寻址未命中任何版本行 ⇒ 版本号无载体，禁伪造首条版本号");
        verify(owner2, never()).approveVersion(any(), any());

        // 点名一个不存在的版本号：同样是前置不成立，不许退化成「挑首条可批的」
        CaliberOwnerClient owner3 = ownerMock();
        when(owner3.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head3 = headRow("c1-id", "CAL_REV", "ACTIVE");
        head3.put("versions", List.of(versionRow("DRAFT", 1, null, null)));
        when(owner3.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head3));
        MvcResult drift = postAt(mvc(owner3, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/approve",
                Map.of("versionNo", 9), "Bearer jwt-finance-user");
        assertEquals(409, drift.getResponse().getStatus());
        assertEquals("9", data(drift).get("versionNo"), "点名的版本号要在取证里回显");
        assertNull(data(drift).get("observedStatus"), "点名版本行不存在 ⇒ 观测态无载体，禁兜一个状态词");
        verify(owner3, never()).approveVersion(any(), any());
    }

    @Test
    @DisplayName("submit 真欠腿：DRAFT 前置做实后显式 501 + missing 逐条在册，禁借道属主 approve 把送审写成批准")
    void submitIsExplicitNotLiveWithoutBorrowingApprove() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "ACTIVE");
        head.put("versions", List.of(versionRow("DRAFT", 1, null, null)));
        when(owner.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));

        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/submit", null, null);
        assertEquals(501, r.getResponse().getStatus());
        assertEquals("STUB_CAPABILITY", json(r).get("errorCode"));
        Map<String, Object> body = data(r);
        assertEquals("submitCaliber", body.get("capability"));
        assertEquals("/api/v1/workspace/calibers/c1-id/submit", body.get("path"));
        assertEquals(Boolean.TRUE, body.get("stub"));
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) body.get("missing");
        assertEquals(4, missing.size(), "真欠逐条在册，缺一即整族外推");
        assertTrue(missing.stream().allMatch(entry -> entry != null && !entry.isBlank()));
        assertTrue(missing.stream().anyMatch(entry -> entry.contains("PENDING_APPROVAL")),
                "成因必须点名无载体的目标态");
        verify(owner, never()).approveVersion(any(), any());
        verify(owner, never()).addVersion(any(), anyMap());
        verify(owner, never()).createHeadRow(anyMap());
    }

    @Test
    @DisplayName("submit 前置腿做实：版本链无 DRAFT ⇒ 409（判据先于能力缺失，禁一律外推成 501）")
    void submitPreconditionLegIsReal() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "ACTIVE");
        head.put("versions", List.of(versionRow("APPROVED", 2, "u-a", "2026-09-30T10:00:00")));
        when(owner.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));

        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/submit", null, null);
        assertEquals(409, r.getResponse().getStatus(),
                "状态前置不成立就是 409，不许和「能力未上线」混成一枚 501（两者成因与恢复路径不同）");
        assertEquals("CALIBER_STATE_PRECONDITION", json(r).get("errorCode"));
        assertEquals(List.of("DRAFT"), data(r).get("expectedStatuses"));
    }

    @Test
    @DisplayName("supersede 前置腿做实：必填逐字段 400 + 无 APPROVED 在位版本 409，均先于 501")
    void supersedePreconditionLegsAreReal() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        MvcResult fields = postAt(mvc(owner, rlsAllowAll()),
                "/api/v1/workspace/calibers/c1-id/supersede", Map.of(), null);
        assertEquals(400, fields.getResponse().getStatus());
        assertEquals(List.of("newVersionId", "changeReason"), data(fields).get("missingFields"),
                "契约「newVersionId + changeReason（必填）」逐枚点名");
        Mockito.verifyNoInteractions(owner);

        CaliberOwnerClient owner2 = ownerMock();
        when(owner2.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "ACTIVE");
        head.put("versions", List.of(versionRow("DRAFT", 1, null, null)));
        when(owner2.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));
        MvcResult state = postAt(mvc(owner2, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/supersede",
                Map.of("newVersionId", "v9", "changeReason", "口径换形"), null);
        assertEquals(409, state.getResponse().getStatus(), "无在位版本可取代 ⇒ 前置不成立，不是能力未上线");
        assertEquals(List.of("APPROVED"), data(state).get("expectedStatuses"));
    }

    @Test
    @DisplayName("supersede 真欠腿：在位版本存在但 SUPERSEDED 无数据载体 ⇒ 显式 501，禁借道 retire 冒充取代")
    void supersedeIsExplicitNotLive() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "ACTIVE"))));
        Map<String, Object> head = headRow("c1-id", "CAL_REV", "ACTIVE");
        head.put("versions", List.of(versionRow("APPROVED", 2, "u-a", "2026-09-30T10:00:00"),
                versionRow("SUPERSEDED", 1, "u-a", "2026-06-30T10:00:00")));
        when(owner.detailByCode("CAL_REV")).thenReturn(new OwnerReply("detail", 0, "ok", head));

        MvcResult r = postAt(mvc(owner, rlsAllowAll()), "/api/v1/workspace/calibers/c1-id/supersede",
                Map.of("newVersionId", "v3", "changeReason", "口径换形"), null);
        assertEquals(501, r.getResponse().getStatus());
        Map<String, Object> body = data(r);
        assertEquals("supersedeCaliber", body.get("capability"));
        assertEquals(Boolean.TRUE, body.get("stub"));
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) body.get("missing");
        assertEquals(5, missing.size(), "无状态载体/无后向列/无主行指针/白名单无该态/恢复路径，五条缺一不可");
        assertTrue(missing.stream().anyMatch(entry -> entry.contains("superseded_by")));
        verify(owner, never()).addVersion(any(), anyMap());
        verify(owner, never()).approveVersion(any(), any());
    }

    @Test
    @DisplayName("写面安全咽喉：四枚同过 caliber_definition 单源，RLS 拒绝即 403 且属主零出网")
    void writeFaceSharesReadThroat() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        MockMvc mvc = mvc(owner, rlsDeny());
        Map<String, Map<String, Object>> bodies = new LinkedHashMap<>();
        bodies.put("/api/v1/workspace/calibers", createRequest());
        bodies.put("/api/v1/workspace/calibers/c1-id/submit", null);
        bodies.put("/api/v1/workspace/calibers/c1-id/approve", null);
        bodies.put("/api/v1/workspace/calibers/c1-id/supersede",
                Map.of("newVersionId", "v3", "changeReason", "口径换形"));
        for (Map.Entry<String, Map<String, Object>> entry : bodies.entrySet()) {
            MvcResult r = postAt(mvc, entry.getKey(), entry.getValue(), "Bearer jwt-finance-user");
            assertEquals(403, r.getResponse().getStatus(), entry.getKey() + " 必须与读面同过安全咽喉");
            assertEquals("PERMISSION_DENIED", json(r).get("errorCode"));
            assertTrue(String.valueOf(data(r).get("permission")).endsWith(":caliber_definition"),
                    entry.getKey() + " 的实体编码不许随场景域默认值漂移");
        }
        Mockito.verifyNoInteractions(owner);
    }

    // ── 委托客户端自身分态（用真实子类替身，不 mock 被验对象）──

    @Test
    @DisplayName("客户端传输故障腿：RestClientException ⇒ 503 ONTOLOGY_UNAVAILABLE，operation 取实发标识")
    void ownerClientMapsTransportFailureTo503() {
        RecordingRest rest = new RecordingRest();
        rest.transportFailure = new ResourceAccessException("connect refused");
        CaliberOwnerClient client = new CaliberOwnerClient(rest);

        CaliberOwnerUnavailableException ex =
                assertThrows(CaliberOwnerUnavailableException.class, () -> client.listHeadRows(null));
        assertEquals(503, ex.getHttpStatus());
        assertEquals("ONTOLOGY_UNAVAILABLE", ex.getCode());
        assertEquals("list", ex.getDetails().get("operation"), "出网点标识必须来自实发操作，禁硬编码漂移");
        assertEquals("ontology", ex.getDetails().get("engine"));
    }

    @Test
    @DisplayName("客户端信封分态腿：HTTP 恒 200 下的 code 面（0/404）逐码上抛，缺 code 归 503 不静默")
    void ownerClientDecodesEnvelopeAndRejectsUnparseable() {
        RecordingRest rest = new RecordingRest();
        CaliberOwnerClient client = new CaliberOwnerClient(rest);

        rest.envelope = Map.of("code", 0, "errorCode", "ok", "data", List.of(Map.of("code", "CAL_REV")));
        OwnerReply ok = client.listHeadRows("APPROVED");
        assertTrue(ok.ok());
        assertEquals(List.of("CAL_REV"), ok.rows().stream().map(m -> m.get("code")).toList());

        rest.envelope = Map.of("code", 404, "errorCode", "CAL-404");
        OwnerReply missing = client.detailByCode("CAL_MISSING");
        assertTrue(missing.notFound(), "属主 404 码面必须可辨（供读面映射成真实 404）");
        assertEquals("detail", missing.operation());

        rest.envelope = Map.of("errorCode", "ok");
        assertThrows(CaliberOwnerUnavailableException.class, () -> client.detailByCode("CAL_REV"),
                "信封缺 code = 契约漂移，禁按成功继续渲染");
    }

    @Test
    @DisplayName("客户端不合成「全部状态」逗号串：status 缺省只下发 limit（属主等值匹配会被逗号串命中零行）")
    void ownerClientOmitsStatusParamWhenAbsent() {
        RecordingRest rest = new RecordingRest();
        rest.envelope = Map.of("code", 0, "errorCode", "ok", "data", List.of());
        CaliberOwnerClient client = new CaliberOwnerClient(rest);

        client.listHeadRows(null);
        assertEquals("/ontology/calibers?limit={limit}", rest.lastUrlTemplate,
                "无 status 时不得下发 status 段（逗号串伪装成「无口径」的成因腿）");
        assertEquals(List.of(CaliberOwnerClient.OWNER_WINDOW_LIMIT), List.of(rest.lastVars));

        rest.lastVars = null;
        client.listHeadRows("APPROVED");
        assertEquals("/ontology/calibers?status={status}&limit={limit}", rest.lastUrlTemplate);
        assertEquals(List.of("APPROVED", CaliberOwnerClient.OWNER_WINDOW_LIMIT), List.of(rest.lastVars));
        assertEquals(HttpMethod.GET, rest.lastMethod);
    }

    @Test
    @DisplayName("客户端详情/预检出网腿：路径模板逐字 + POST 体 = 入参面（业务侧只传相对 URI，禁字符串拼接）")
    void ownerClientUsesPathTemplatesNotConcatenation() {
        RecordingRest rest = new RecordingRest();
        rest.envelope = Map.of("code", 0, "errorCode", "ok", "data", Map.of("valid", true));
        CaliberOwnerClient client = new CaliberOwnerClient(rest);

        client.detailByCode("CAL_REV");
        assertEquals("/ontology/calibers/{code}", rest.lastUrlTemplate, "code 走 URI 变量位，由 builder 编码");
        assertEquals(List.of("CAL_REV"), List.of(rest.lastVars));

        Map<String, Object> payload = Map.of("formula", "revenue");
        client.validate(payload);
        assertEquals("/ontology/calibers/validate", rest.lastUrlTemplate);
        assertEquals(HttpMethod.POST, rest.lastMethod);
        assertEquals(payload, rest.lastEntity.getBody());
    }

    @Test
    @DisplayName("客户端 data 段形态腿：list 面非行数组 / detail 面缺对象 ⇒ 均归 503（禁按空集合继续渲染）")
    void ownerClientRejectsShapeDrift() {
        OwnerReply listWithMap = new OwnerReply("list", 0, "ok", Map.of("code", "CAL_REV"));
        assertThrows(CaliberOwnerUnavailableException.class, listWithMap::rows);
        OwnerReply detailWithNull = new OwnerReply("detail", 0, "ok", null);
        assertThrows(CaliberOwnerUnavailableException.class, detailWithNull::object);
    }

    @Test
    @DisplayName("出网凭证中继单点在 exchange：三条写腿逐字带上 Authorization/信任头，无上下文时不伪造主语")
    void everyEgressLegRelaysCallerCredentialsVerbatim() {
        RecordingRest rest = new RecordingRest();
        rest.envelope = Map.of("code", 0, "errorCode", "ok", "data", Map.of("status", "APPROVED"));
        CaliberOwnerClient client = new CaliberOwnerClient(rest);
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-finance-user");
        inbound.addHeader("X-ECOS-USER", "u-approve");
        inbound.addHeader("X-ECOS-ROLE", "finance_manager");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        client.approveVersion("CAL_REV", "1");
        assertRelayed(rest.lastEntity.getHeaders(), "Bearer jwt-finance-user", "u-approve", "finance_manager");
        assertEquals(List.of(MediaType.APPLICATION_JSON),
                new ArrayList<>(rest.lastEntity.getHeaders().getAccept()),
                "approve 腿无请求体 ⇒ 只带 Accept，禁给它补 Content-Type");
        assertNull(rest.lastEntity.getBody(), "审批主语只走凭证头，禁写进请求体（伪造主语）");

        client.createHeadRow(Map.of("code", "CAL_NEW"));
        assertRelayed(rest.lastEntity.getHeaders(), "Bearer jwt-finance-user", "u-approve", "finance_manager");
        assertEquals(MediaType.APPLICATION_JSON, rest.lastEntity.getHeaders().getContentType(),
                "有体腿必须补 Content-Type，否则属主按表单解 ⇒ 七字段全空");

        client.addVersion("CAL_NEW", Map.of("formula", "revenue"));
        assertRelayed(rest.lastEntity.getHeaders(), "Bearer jwt-finance-user", "u-approve", "finance_manager");

        // 无请求上下文（异步／定时腿）⇒ 中继不加任何身份头，属主端按其默认策略 DENY；
        // 本面不发明 anonymous 兜底主语（那会把「取不到凭证」伪装成「有凭证」）
        RequestContextHolder.resetRequestAttributes();
        client.approveVersion("CAL_REV", "1");
        assertNull(rest.lastEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                "无上下文时禁伪造 Authorization");
        assertNull(rest.lastEntity.getHeaders().getFirst("X-ECOS-USER"));
    }

    private static void assertRelayed(HttpHeaders headers, String authorization, String user, String role) {
        assertEquals(authorization, headers.getFirst(HttpHeaders.AUTHORIZATION), "凭证逐字中继，不升权不改写");
        assertEquals(user, headers.getFirst("X-ECOS-USER"));
        assertEquals(role, headers.getFirst("X-ECOS-ROLE"));
    }

    // ── 实体编码单源 ──

    @Test
    @DisplayName("读守卫实体编码单源：caliber_definition 由服务持有，漂移即 403 且属主零出网")
    void entityCodeSingleSourcedInService() {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(c.applyRlsFilter(any(), any(), anyMap())).thenAnswer(inv ->
                "caliber_definition".equals(inv.getArgument(0))
                        ? RlsFilter.unrestricted()
                        : RlsFilter.deny("WRONG_ENTITY", true, List.of()));
        CaliberOwnerClient owner = ownerMock();
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(headRow("c1-id", "CAL_REV", "APPROVED"))));
        CaliberReadService svc = new CaliberReadService(owner,
                new ScenarioReadGuard(new AbacQueryFilter(c)), new CaliberAddressing(owner));

        assertEquals("ontology-owner-rest", svc.list(null, null).get("dataSource"),
                "实体编码逐字 caliber_definition ⇒ 过守卫拿到属主真值");
        verify(owner, times(1)).listHeadRows(isNull());

        WorkspaceSecurityEngineClient drifted = mock(WorkspaceSecurityEngineClient.class);
        Mockito.when(drifted.applyRlsFilter(any(), any(), anyMap()))
                .thenReturn(RlsFilter.deny("WRONG_ENTITY", true, List.of()));
        CaliberOwnerClient owner2 = ownerMock();
        CaliberReadService svc2 = new CaliberReadService(owner2,
                new ScenarioReadGuard(new AbacQueryFilter(drifted)), new CaliberAddressing(owner2));
        assertThrows(com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException.class,
                () -> svc2.list(null, null));
        verify(owner2, never()).listHeadRows(any());
    }

    // ── 夹具 ──

    /**
     * 真实子类替身：只记录出网形态并按脚本回信封。
     * 不用 mock(RestTemplate) 是为了让 varargs URI 变量逐字可比 ——
     * 「limit-only 还是 status+limit」正是本批防回归的那一条腿。
     */
    private static final class RecordingRest extends RestTemplate {
        Map<String, Object> envelope = Map.of("code", 0, "errorCode", "ok");
        RuntimeException transportFailure;
        String lastUrlTemplate;
        HttpMethod lastMethod;
        HttpEntity<?> lastEntity;
        Object[] lastVars = new Object[0];

        @SuppressWarnings("unchecked")
        @Override
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                             ParameterizedTypeReference<T> responseType,
                                             Object... uriVariables) {
            this.lastUrlTemplate = url;
            this.lastMethod = method;
            this.lastEntity = requestEntity;
            this.lastVars = uriVariables;
            if (transportFailure != null) {
                throw transportFailure;
            }
            return (ResponseEntity<T>) ResponseEntity.ok(envelope);
        }
    }

    private static OwnerReply okList(List<Map<String, Object>> rows) {
        return new OwnerReply("list", 0, "ok", rows);
    }

    private static Map<String, Object> headRow(String id, String code, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", code);
        row.put("name", "营业收入口径");
        row.put("status", status);
        row.put("unit", "万元");
        row.put("currency", "CNY");
        row.put("ownerRole", "finance_manager");
        row.put("dimensionJson", "{\"org\":\"dept\"}");
        row.put("domain", "FINANCE");
        return row;
    }

    private static Map<String, Object> versionRow(String status, int versionNo, String approvedBy,
                                                  String approvedAt) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("version_no", versionNo);
        v.put("status", status);
        v.put("formula", "revenue - cost");
        v.put("additive", 1);
        v.put("period_granularity", "MONTH");
        v.put("git_ref", "v2@abc123");
        v.put("create_time", "2026-09-01T00:00:00");
        if (approvedBy != null) {
            v.put("approved_by", approvedBy);
            v.put("approved_at", approvedAt);
        }
        return v;
    }

    private static Map<String, Object> violation(String gate, String code, String message, String symbol,
                                                 int line, int column) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("gate", gate);
        v.put("code", code);
        v.put("message", message);
        v.put("symbol", symbol);
        v.put("line", line);
        v.put("column", column);
        return v;
    }

    @Test
    @DisplayName("属主行缺列腿：无载体列不出现空串/兜底键，只带实存列 + 三枚显式缺席对")
    void absentOwnerColumnsAreNotFilledWithEmptyStrings() throws Exception {
        CaliberOwnerClient owner = ownerMock();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "c9-id");
        row.put("code", "CAL_PARTIAL");
        row.put("status", "DRAFT");
        when(owner.listHeadRows(isNull())).thenReturn(okList(List.of(row)));
        Map<String, Object> item = items(data(get(mvc(owner, rlsAllowAll()),
                "/api/v1/workspace/calibers"))).get(0);
        assertEquals(List.of("id", "code", "status",
                        "versionChain", "versionChainApplicable",
                        "approvedBy", "approvedByApplicable",
                        "effectivePeriod", "effectivePeriodApplicable"),
                new ArrayList<>(item.keySet()));
        assertTrue(!item.containsKey("unit") && !item.containsKey("currency"),
                "属主未回该列 ⇒ 键本身缺席，禁写空串伪装「有该列且值为空」");
    }
}
