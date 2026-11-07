package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextPack;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context Pack 主体验级授权裁剪器 —— security-engine 裁决面适配（B39/P1-9 生产实现）。
 *
 * <p>设计依据（详设-01 §4.7.2 / SUP-CTX-02）：行/列过滤与脱敏已在源引擎读接口执行，
 * 本类补<b>主体维度的授权复核</b>（双层防线）；任一 DENY 即拒绝，0 泄露。</p>
 *
 * <p><b>主体来源</b>：取本请求已认证的 {@link SecurityContextHolder} 主体（gateway
 * JwtAuthenticationFilter 验签产物），不读请求体自报字段、不发明匿名主语。</p>
 *
 * <p><b>裁决契约</b>：{@code POST /api/v1/security/decision}
 * （{@code assetRef={table,columns:["*"]}} + {@code purpose=ai} + {@code subject.subjectId=<已认证主体>}）。
 * 本层只消费 <b>rls 段</b>做主体级复核：仅接受恒真谓词（{@code 1=1}）且 {@code noPolicyMatched=false}；
 * 非平凡谓词无法下推到元数据读面 ⇒ DENY（与 workspace {@code assertReadable} 同口径）。
 * 列级 CLS/脱敏按设计在源引擎读接口执行（双层防线），{@code columns=["*"]} 仅为取得整表裁决；
 * 若应答带 {@code blockedColumns} 则如实转成隐藏项。响应缺段/非 2xx ⇒ 抛异常，由
 * {@code ContextBuilderService} 按 fail-closed 转 403；依赖不可用（连不上/5xx）同样不得放行。</p>
 *
 * <p><b>来源→保护表映射</b>：DATA_ASSET→{@code ecos_data.ecos_data_asset}；
 * KNOWLEDGE→{@code ecos_knowledge.knowledge_article}；
 * ONTOLOGY→{@code ecos_ontology.ecos_ontology_entity}；
 * COGNITIVE→{@code ecos_cognitive.ecos_cognitive_belief}。
 * 未登记类型一律 DENY（禁把未知来源当"无敏感数据"放行）。</p>
 */
@Component
public class SecurityEngineContextTrimmer implements ContextSecurityTrimmer {

    private static final Logger log = LoggerFactory.getLogger(SecurityEngineContextTrimmer.class);

    static final String DECISION_PATH = "/api/v1/security/decision";
    private static final String OWNER_SERVICE = "sysman";
    private static final String PURPOSE_AI = "ai";

    private static final Map<String, String> SOURCE_TABLES = Map.of(
            "DATA_ASSET", "ecos_data.ecos_data_asset",
            "KNOWLEDGE", "ecos_knowledge.knowledge_article",
            "ONTOLOGY", "ecos_ontology.ecos_ontology_entity",
            "COGNITIVE", "ecos_cognitive.ecos_cognitive_belief");

    private final RestTemplate restTemplate;
    private final ServiceEndpointResolver endpointResolver;

    public SecurityEngineContextTrimmer(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                        ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public TrimDecision trim(ContextBuildRequest request, ContextSourceRef ref, ContextPack pack) {
        if (ref == null || ref.getSourceType() == null) {
            return TrimDecision.deny();
        }
        String table = SOURCE_TABLES.get(ref.getSourceType().toUpperCase());
        if (table == null) {
            log.warn("Context 裁剪：未登记来源类型，按 DENY 处置 type={}", ref.getSourceType());
            return TrimDecision.deny();
        }
        String subjectId = currentSubjectId();
        if (subjectId == null) {
            log.warn("Context 裁剪：无已认证主体，按 DENY 处置 sourceId={}", ref.getSourceId());
            return TrimDecision.deny();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assetRef", Map.of("table", table, "columns", List.of("*")));
        body.put("purpose", PURPOSE_AI);
        body.put("subject", Map.of("subjectId", subjectId));

        String base = endpointResolver.resolve(OWNER_SERVICE, ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> new IllegalStateException(
                        "route-manifest 缺 sysman 条目，无法访问安全裁决面"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.postForEntity(base + DECISION_PATH, new HttpEntity<>(body, headers), Map.class);
        } catch (HttpStatusCodeException e) {
            // 明确拒绝（4xx）＝无权；5xx 交给上层视为不可用（同样 fail-closed）
            log.warn("Context 裁剪：裁决拒绝 table={} status={}", table, e.getStatusCode().value());
            if (e.getStatusCode().is5xxServerError()) {
                throw new IllegalStateException("security decision unavailable status="
                        + e.getStatusCode().value(), e);
            }
            return TrimDecision.deny();
        }
        Map<?, ?> payload = resp.getBody();
        if (payload == null || !(payload.get("data") instanceof Map<?, ?> data)) {
            throw new IllegalStateException("security decision 应答缺 data 段（fail-closed）");
        }
        // 单源读数：RlsExecutionContract.read 承载四道不变量（缺片段/参数不齐/空参/残留占位符 ⇒ DENIED）
        RlsExecutionContract.Execution exec = RlsExecutionContract.read(
                data.get("rls"), "context-trim:" + table);
        if (!exec.allowAll()) {
            log.warn("Context 裁剪：RLS 非恒真（state={} reason={}），DENY table={}",
                    exec.state(), exec.reason(), table);
            return TrimDecision.deny();
        }
        // 列面：源侧已执行 CLS；此处整表请求仅为取得主体级裁决，blockedColumns 如实带回（若有）
        Map<?, ?> columns = data.get("columns") instanceof Map<?, ?> c ? c : null;
        List<String> blocked = columns == null ? List.of() : stringList(columns.get("blockedColumns"));
        return blocked.isEmpty() ? TrimDecision.allowAll() : TrimDecision.allowWithHidden(blocked);
    }

    /** 当前已认证主体 id；未认证/匿名 => null（不发明主语）。 */
    private static String currentSubjectId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        String name = String.valueOf(auth.getPrincipal());
        if (name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }
}
