package com.chinacreator.gzcm.cli.commands;

import com.chinacreator.gzcm.cli.CliErrorCodes;
import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.CliStatus;
import com.chinacreator.gzcm.cli.EnvelopeMapper;
import com.chinacreator.gzcm.cli.ExitCodes;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.OperationRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ecos doctor} — 环境与连通性自检（严格只读，不产生任何写请求）。
 *
 * <p>四项检查逐项给 PASS/FAIL/BLOCKED/UNKNOWN（REQ-CLI-11：禁空默认值造假成功）：
 * 网关可达（匿名）· 认知引擎 · 监控聚合 · 凭据有效性。任一项 FAIL 不被其它项 PASS 掩盖。</p>
 *
 * <p>{@code --preflight} 走服务端 11 步预检（{@code POST /api/v1/cli/preflight}，只报告不建 Run）。
 * 该路径按报告里的 fail/blocked/notVerified 计数定级，<b>不</b>复用 HTTP 码映射——
 * 服务端用 503 表示「依赖不可用」，而 CLI 语义那属于 BLOCKED(2) 而非 UNAVAILABLE(3)。</p>
 */
public final class DoctorCommand {

    private static final String CMD = "doctor";
    private static final String PREFLIGHT_OPERATION = "runPreflight";

    private DoctorCommand() {
    }

    public static Map<String, Object> run(OperationRegistry registry, CliOptions options,
                                          HttpTransport transport) {
        if (options.flag("--preflight")) {
            return runPreflight(registry, options, transport);
        }
        String token = options.token();
        boolean dryRun = options.flag("--dry-run");

        List<Check> checks = List.of(
                new Check("gateway", "getGatewayHealth", false),
                new Check("cognitive-engine", "listCognitiveModels", true),
                new Check("monitor", "getMonitorHealth", true),
                new Check("identity", "getCurrentUserProfile", true));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> refs = new ArrayList<>();
        boolean anyFail = false;
        boolean anyUnavailable = false;
        boolean anyBlocked = false;
        boolean anyUnknown = false;

        for (Check check : checks) {
            OperationRegistry.Operation op = registry.operation(check.operationId());
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("operationId", op.operationId());
            ref.put("path", op.path());
            refs.add(ref);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", check.name());
            row.put("operationId", op.operationId());

            if (dryRun) {
                row.put("status", "UNKNOWN");
                row.put("detail", "dry-run：将调用 " + transport.plannedCall(op.method(), op.path()));
                anyUnknown = true;
                rows.add(row);
                continue;
            }

            if (check.tokenRequired() && token == null) {
                row.put("status", "BLOCKED");
                row.put("detail", "未配置凭据（--token / ECOS_TOKEN / --token-file / --profile）");
                anyBlocked = true;
                rows.add(row);
                continue;
            }

            Map<String, Object> mapped = EnvelopeMapper.map(op.operationId(), op,
                    transport.get(op.path(), token), transport.traceId());
            CliStatus status = CliStatus.valueOf(String.valueOf(mapped.get("status")));
            String result = switch (status) {
                case SUCCESS -> "PASS";
                case BLOCKED -> {
                    anyBlocked = true;
                    yield "BLOCKED";
                }
                case UNAVAILABLE -> {
                    anyUnavailable = true;
                    yield "FAIL";
                }
                case FAILED -> {
                    anyFail = true;
                    yield "FAIL";
                }
                case PARTIAL -> {
                    anyUnknown = true;
                    yield "UNKNOWN";
                }
            };
            row.put("status", result);
            row.put("detail", detailOf(mapped));
            rows.add(row);
        }

        CliStatus overall;
        if (dryRun) {
            overall = CliStatus.SUCCESS;
        } else if (anyFail) {
            overall = CliStatus.FAILED;
        } else if (anyUnavailable) {
            overall = CliStatus.UNAVAILABLE;
        } else if (anyBlocked) {
            overall = CliStatus.BLOCKED;
        } else if (anyUnknown) {
            overall = CliStatus.PARTIAL;
        } else {
            overall = CliStatus.SUCCESS;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseUrl", transport.baseUrl());
        data.put("checks", rows);

        Map<String, Object> out = EnvelopeMapper.local(overall, CliErrorCodes.DOCTOR,
                overall == CliStatus.SUCCESS ? "全部检查通过" : "存在未通过项（逐项见 data.checks）",
                null, refs, transport.traceId());
        out.put("data", data);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String detailOf(Map<String, Object> mapped) {
        Object error = mapped.get("error");
        if (error instanceof Map<?, ?> e) {
            return e.get("code") + ": " + e.get("message");
        }
        return "OK";
    }

    /**
     * 11 步预检入口（批 E）：只报告，不建 Run。
     *
     * <p>定级以报告计数为准：任一步 FAIL ⇒ 1；否则任一步 BLOCKED ⇒ 2；
     * 否则存在 NOT_VERIFIED ⇒ 4（部分可判定）；全 PASS ⇒ 0。</p>
     */
    private static Map<String, Object> runPreflight(OperationRegistry registry, CliOptions options,
                                                    HttpTransport transport) {
        OperationRegistry.Operation op = registry.operation(PREFLIGHT_OPERATION);
        List<Map<String, Object>> refs = new ArrayList<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("operationId", op.operationId());
        ref.put("path", op.path());
        refs.add(ref);

        if (options.flag("--dry-run")) {
            Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, CliErrorCodes.DRY_RUN,
                    "dry-run：未发请求（预检只报告，不建 Run）", null, refs, transport.traceId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("plannedCall", transport.plannedCall(op.method(), op.path()));
            data.put("createsRun", false);
            out.put("data", data);
            return out;
        }

        String token = options.token();
        if (token == null) {
            return EnvelopeMapper.local(CliStatus.BLOCKED, CliErrorCodes.AUTH,
                    "未配置凭据（--token / ECOS_TOKEN / --token-file / --profile）",
                    "预检逐项带同一凭据探测，禁匿名放行", refs, transport.traceId());
        }

        Map<String, Object> mapped = EnvelopeMapper.map(op.operationId(), op,
                transport.write(op.method(), op.path(), token, null, null), transport.traceId());
        Object raw = mapped.get("data");
        if (!(raw instanceof Map<?, ?> report)) {
            return mapped;
        }

        int pass = intOf(report.get("pass"));
        int fail = intOf(report.get("fail"));
        int blocked = intOf(report.get("blocked"));
        int notVerified = intOf(report.get("notVerified"));
        CliStatus overall;
        String message;
        String code;
        if (fail > 0) {
            overall = CliStatus.FAILED;
            code = CliErrorCodes.FAILED;
            message = "预检存在失败项（" + fail + "），逐项见 data.steps";
        } else if (blocked > 0) {
            overall = CliStatus.BLOCKED;
            code = CliErrorCodes.BLOCKED;
            message = "预检依赖不可用（" + blocked + "），逐项见 data.steps";
        } else if (notVerified > 0) {
            overall = CliStatus.PARTIAL;
            code = CliErrorCodes.PARTIAL;
            message = "预检完成但存在不可判定项（" + notVerified + "），逐项见 data.steps";
        } else {
            overall = CliStatus.SUCCESS;
            code = null;
            message = "预检全部通过（" + pass + " 步）";
        }

        Map<String, Object> out = EnvelopeMapper.local(overall, code, message, null, refs,
                String.valueOf(mapped.get("trace_id")));
        out.put("data", raw);
        return out;
    }

    private static int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private record Check(String name, String operationId, boolean tokenRequired) {
    }
}
