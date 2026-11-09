package com.chinacreator.gzcm.cli.commands;

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
 */
public final class DoctorCommand {

    private static final String CMD = "doctor";

    private DoctorCommand() {
    }

    public static Map<String, Object> run(OperationRegistry registry, CliOptions options,
                                          HttpTransport transport) {
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
                case UNAVAILABLE, FAILED -> {
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

        Map<String, Object> out = EnvelopeMapper.local(overall, "ECOS-CLI-DOCTOR",
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

    private record Check(String name, String operationId, boolean tokenRequired) {
    }
}
