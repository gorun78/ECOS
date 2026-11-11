package com.chinacreator.gzcm.cli.commands;

import com.chinacreator.gzcm.cli.CliCommand;
import com.chinacreator.gzcm.cli.CliErrorCodes;
import com.chinacreator.gzcm.cli.CliException;
import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.CliStatus;
import com.chinacreator.gzcm.cli.EnvelopeMapper;
import com.chinacreator.gzcm.cli.ExitCodes;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.IdempotencyKeys;
import com.chinacreator.gzcm.cli.OperationRegistry;
import com.chinacreator.gzcm.cli.PageWalker;
import com.chinacreator.gzcm.cli.SseReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用命令执行器——把「命令元数据 + 契约清单 + 参数」翻译成一次 HTTP 调用。
 *
 * <p><b>为什么通用</b>：14 条命令里绝大多数是「取路径参数 → GET/POST → 映射信封」的同一形态，
 * 逐条写实现会让「禁手写 URL」退化成 14 份重复代码。此处只做三件事：路径模板填充、query 拼装、
 * 信封映射；<b>不含</b>任何 URL 字面量（路径一律来自 {@link OperationRegistry#pathOf}）。</p>
 *
 * <p><b>未接入命令</b>（{@code wired=false}）：输出 {@code UNAVAILABLE} + source_refs，
 * error 指明缺的是服务端端点还是审批凭证——<b>禁</b>返回空集合伪装成功。</p>
 */
public final class GenericCommandRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GenericCommandRunner() {
    }

    public static Map<String, Object> run(OperationRegistry registry, CliOptions options,
                                          HttpTransport transport, CliCommand command) {
        Map<String, Object> refs = new LinkedHashMap<>();
        if (!command.operationIds().isEmpty()) {
            OperationRegistry.Operation op = registry.operation(command.operationIds().get(0));
            refs.put("operationId", op.operationId());
            refs.put("path", op.path());
        }

        if (!command.wired()) {
            return notWired(command, refs, transport);
        }
        if (command.requiresApproval() && options.value("--approval-token") == null) {
            return EnvelopeMapper.local(CliStatus.BLOCKED, CliErrorCodes.BLOCKED,
                    "L3 命令必须提供 --approval-token（服务端审批凭证，CLI 不得自签）",
                    "无 token 一律 BLOCKED，禁降级为无审批执行", List.of(refs), transport.traceId());
        }
        if (command.operationIds().size() != 1) {
            throw new CliException(ExitCodes.FAILED,
                    "通用执行器只支持单 operationId 命令: " + command.name());
        }

        OperationRegistry.Operation op = registry.operation(command.operationIds().get(0));
        String path = fillPath(registry, options, command, op);
        Map<String, String> query = collectQuery(registry, options, command, op);
        String pathWithQuery = path + toQuery(query);

        // --follow：对 getRun 走 streamRunEvents（SSE）真流；服务端降级（502 JSON）即如实 UNAVAILABLE
        if (options.flag("--follow") && "getRun".equals(op.operationId())
                && registry.has("streamRunEvents")) {
            return followStream(registry, options, transport);
        }

        if (options.flag("--dry-run")) {
            Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, CliErrorCodes.DRY_RUN,
                    "dry-run：未发请求", null, List.of(refs), transport.traceId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("plannedCall", transport.plannedCall(op.method(), pathWithQuery));
            data.put("query", query);
            out.put("data", data);
            return out;
        }

        String token = options.token();
        if (token == null) {
            return EnvelopeMapper.local(CliStatus.BLOCKED, CliErrorCodes.AUTH,
                    "未配置凭据（--token / ECOS_TOKEN / --token-file / --profile）",
                    "只读接口同样要求 JWT，禁匿名放行", List.of(refs), transport.traceId());
        }

        HttpTransport.Result result = isRead(op)
                ? transport.get(pathWithQuery, token)
                : transport.write(op.method(), pathWithQuery, token,
                        buildBody(registry, options, command, op), options.idempotencyKey(),
                        retrySafeKey(options));

        Map<String, Object> out = EnvelopeMapper.map(op.operationId(), op, result, transport.traceId(),
                command.requiresApproval());
        // 计划 §二：列表读面默认单页也回显 data.page；--all 才遍历。非列表响应不碰。
        // 首页复用上面已取的 result，避免默认单页也打两次请求。
        if (isRead(op) && isListShaped(out.get("data"))) {
            attachPageMeta(out, options, path, query, token, transport, result);
        }
        if (!isRead(op)) {
            attachIdempotency(out, options.idempotencyKey());
        }
        return out;
    }

    /**
     * 未接入命令的统一出口：明确区分「服务端端点缺」与「审批凭证未就绪」。
     * 已登记但不可用时 <b>绝不</b>发请求（避免半截写）。
     */
    private static Map<String, Object> notWired(CliCommand command, Map<String, Object> ref,
                                                HttpTransport transport) {
        String reason;
        String hint;
        if (command.requiresApproval()) {
            reason = "L3 审批凭证服务端校验与一次性消费未就绪（批 D）";
            hint = "服务端就绪前禁止任何发布/提交动作；CLI 不得自签或本地放行";
        } else if ("knowledge extract".equals(command.name())) {
            reason = "POST /api/v1/wagent/candidates 端点未实装（契约未登记）";
            hint = "抽取入口就绪后本命令接入；CLI 不得伪造候选";
        } else if ("model route".equals(command.name())) {
            reason = "POST /api/v1/ai/route/resolve 端点未实装（契约 status=planned）";
            hint = "路由解析就绪后接入；CLI 不得伪造路由结果与回退链";
        } else if ("cognition model run".equals(command.name())) {
            reason = "认知模型执行权按设计 §8.4.2 归属外部计算/预测服务，cognitive-engine 只登记资产"
                    + "（compute_owner NOT NULL，模型实例与运行结果不入 ecos_cognitive_model）；"
                    + "契约 startCognitiveModelRun 无输入体且 status=planned";
            hint = "需外部计算服务承接或另立运行端点（待裁决）后接入；CLI 不得伪造 runId 与产物";
        } else {
            reason = "属主端点未实装（契约 status=planned）";
            hint = "端点就绪后接入；CLI 不得伪造结果";
        }
        return EnvelopeMapper.local(CliStatus.UNAVAILABLE, CliErrorCodes.NOT_IMPLEMENTED,
                reason, hint, ref.isEmpty() ? List.of() : List.of(ref), transport.traceId());
    }

    /** 路径模板填充：契约 path 中的 {param} 用命令登记的 --flag 值替换，缺值即用法错。 */
    private static String fillPath(OperationRegistry registry, CliOptions options,
                                   CliCommand command, OperationRegistry.Operation op) {
        String path = op.path();
        if (!path.contains("{")) {
            return path;
        }
        for (OperationRegistry.PathParam p : registry.pathParamsOf(command.name()).values()) {
            String value = options.value(p.flag());
            if (value == null || value.isBlank()) {
                throw new CliException(ExitCodes.USAGE, "缺少必需参数 " + p.flag() + "（" + p.description() + "）");
            }
            path = path.replace("{" + p.parameter() + "}", URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        if (path.contains("{")) {
            throw new CliException(ExitCodes.FAILED,
                    "契约路径仍含未填充占位（命令参数登记不全）: " + path);
        }
        return path;
    }

    /** query 拼装：只取命令登记过的 flag，值非空才带（禁把空串当过滤条件）。 */
    private static Map<String, String> collectQuery(OperationRegistry registry, CliOptions options,
                                                    CliCommand command, OperationRegistry.Operation op) {
        Map<String, String> query = new LinkedHashMap<>();
        for (OperationRegistry.Flag flag : registry.flagsOf(command.name()).values()) {
            if (!flag.operationId().equals(op.operationId())) {
                continue;
            }
            String v = options.value(flag.flag());
            if (v != null && !v.isBlank()) {
                query.put(flag.parameter(), v);
            }
        }
        return query;
    }

    /**
     * 请求体构造：命令登记的 body 参数 → JSON 对象；{@code --body-json} 作为补充（后写覆盖）。
     * 两者都空时返回 null（无体调用，如 POST /cancel）。
     */
    private static String buildBody(OperationRegistry registry, CliOptions options,
                                    CliCommand command, OperationRegistry.Operation op) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (OperationRegistry.BodyParam b : registry.bodyParamsOf(command.name()).values()) {
            if (!b.operationId().equals(op.operationId())) {
                continue;
            }
            String v = options.value(b.flag());
            if (v != null && !v.isBlank()) {
                body.put(b.parameter(), v);
            }
        }
        String raw = options.value("--body-json");
        if (raw != null && !raw.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = MAPPER.readValue(raw, Map.class);
                body.putAll(extra);
            } catch (Exception e) {
                throw new CliException(ExitCodes.USAGE, "--body-json 不是合法 JSON 对象");
            }
        }
        if (body.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new CliException(ExitCodes.FAILED, "请求体序列化失败");
        }
    }

    private static boolean isRead(OperationRegistry.Operation op) {
        return "GET".equalsIgnoreCase(op.method());
    }

    /**
     * 写命令「可安全重试」的判据：<b>仅当调用方显式传了幂等键</b>。
     *
     * <p>缺省自动生成的 {@code cli-<uuid>} 每次都不同、服务端也无从去重（实测
     * {@code createCandidate} 直接 {@code UUID.randomUUID()} 建新行，`startRun` 之外
     * 的写端点均无幂等通道）。若把它当作重试依据，响应丢失后重试就会**重复落库**。
     * 故自动键只用于回显与人工重放，不能授权自动重试 —— 对应计划「写命令默认不重试
     * （除非带幂等键）」。</p>
     */
    private static boolean retrySafeKey(CliOptions options) {
        return IdempotencyKeys.isExplicit(options.value("--idempotency-key"));
    }

    /**
     * {@code --follow}：读 {@code streamRunEvents}（SSE）。
     *
     * <p>服务端 SSE 通道未就绪时该路径返回 502 JSON（降级体），{@link SseReader} 会返回 -1，
     * 此处如实报 UNAVAILABLE 并把「改用轮询」写进 hint —— <b>禁</b>当空流报成功。</p>
     */
    private static Map<String, Object> followStream(OperationRegistry registry, CliOptions options,
                                                    HttpTransport transport) {
        OperationRegistry.Operation sse = registry.operation("streamRunEvents");
        String runId = options.value("--run-id");
        if (runId == null || runId.isBlank()) {
            throw new CliException(ExitCodes.USAGE, "缺少必需参数 --run-id");
        }
        String path = sse.path().replace("{runId}", URLEncoder.encode(runId, StandardCharsets.UTF_8));
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("operationId", sse.operationId());
        ref.put("path", sse.path());

        if (options.flag("--dry-run")) {
            Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, CliErrorCodes.DRY_RUN,
                    "dry-run：未发请求", null, List.of(ref), transport.traceId());
            out.put("data", Map.of("plannedCall",
                    transport.plannedCall("GET", path), "follow", true));
            return out;
        }
        String token = options.token();
        if (token == null) {
            return EnvelopeMapper.local(CliStatus.BLOCKED, CliErrorCodes.AUTH,
                    "未配置凭据（--token / ECOS_TOKEN / --token-file / --profile）",
                    "SSE 与只读接口同样要求 JWT", List.of(ref), transport.traceId());
        }

        SseReader reader = new SseReader(options.baseUrl(), transport.traceId(), options.timeoutMs());
        List<Map<String, Object>> events = new ArrayList<>();
        int count = reader.stream(path, token, options.value("--last-event-id"),
                options.maxPages() * 10, event -> events.add(SseReader.toRow(event)));
        if (count < 0) {
            return EnvelopeMapper.local(CliStatus.UNAVAILABLE, CliErrorCodes.DEP_UNAVAILABLE,
                    "SSE 进度通道不可用（服务端 P-4 未就绪或鉴权失败）",
                    "改用不带 --follow 的 agent inspect 轮询 getRun", List.of(ref), transport.traceId());
        }
        Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, null, null, null,
                List.of(ref), transport.traceId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        data.put("events", events);
        out.put("data", data);
        return out;
    }

    /** --all：按 limit/offset 翻页取全量；默认单页不遍历。 */
    @SuppressWarnings("unchecked")
    private static void attachPageMeta(Map<String, Object> out, CliOptions options,
                                       String path, Map<String, String> baseQuery, String token,
                                       HttpTransport transport, HttpTransport.Result firstPage) {
        int pageSize = options.pageSize(100);
        boolean all = options.flag("--all");
        PageWalker.PageResult page;
        try {
            page = PageWalker.collectFromFirst(pageSize, options.maxPages(), all, firstPage,
                    (offset, limit) -> transport.get(
                            path + toQuery(offsetQuery(baseQuery, limit, offset)), token));
        } catch (PageWalker.PageFetchException e) {
            // 首页失败：保留原映射结果（status/error 已由 EnvelopeMapper 决定），只追加不吞错
            Map<String, Object> failed = Map.of("pagesFetched", 1, "truncated", true,
                    "all", all, "note", "首页失败，未翻页");
            withPageMeta(out, failed);
            return;
        }
        Map<String, Object> meta = PageWalker.pageMeta(page, all);
        // 保留后端分页体其余字段（size 等），只替换 items 与本 CLI 的 page 元数据
        if (out.get("data") instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) m;
            data.put("items", page.rows());
            out.put("data", data);
        } else {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", page.rows());
            out.put("data", data);
        }
        withPageMeta(out, meta);
    }

    /** 列表形态两种：裸数组，或设计册规定的分页体 {@code {items,total,page,size}}。 */
    private static boolean isListShaped(Object data) {
        if (data instanceof List) {
            return true;
        }
        return data instanceof Map<?, ?> m && m.get("items") instanceof List;
    }

    /** {@code data.page} 为规范位置；顶层 {@code page} 保留为兼容别名（旧调用方按顶层读取）。 */
    private static void withPageMeta(Map<String, Object> out, Map<String, Object> meta) {
        Object existing = out.get("data");
        if (existing instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) m;
            data.put("page", meta);
        } else {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", existing);
            data.put("page", meta);
            out.put("data", data);
        }
        out.put("page", meta);
    }

    /**
     * 续页 query：<b>先继承首页全部过滤条件</b>，再覆盖 {@code limit/offset}。
     *
     * <p>只拼 limit/offset 会把过滤条件丢掉 —— 第 2 页起变成「无过滤全量」，与首页语义不一致。</p>
     */
    private static Map<String, String> offsetQuery(Map<String, String> baseQuery, int limit, int offset) {
        Map<String, String> q = new LinkedHashMap<>(baseQuery == null ? Map.of() : baseQuery);
        q.put("limit", String.valueOf(limit));
        q.put("offset", String.valueOf(offset));
        return q;
    }

    private static void attachIdempotency(Map<String, Object> out, String key) {
        if (out.get("data") instanceof Map<?, ?> data) {
            ((Map<String, Object>) data).put("idempotencyKey", key);
        } else {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("idempotencyKey", key);
            wrapper.put("result", out.get("data"));
            out.put("data", wrapper);
        }
    }

    private static String toQuery(Map<String, String> query) {
        if (query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }
}
