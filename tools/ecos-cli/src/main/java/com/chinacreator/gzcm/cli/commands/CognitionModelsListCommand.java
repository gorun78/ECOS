package com.chinacreator.gzcm.cli.commands;

import com.chinacreator.gzcm.cli.CliCommands;
import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.CliStatus;
import com.chinacreator.gzcm.cli.EnvelopeMapper;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.OperationRegistry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ecos cognition models list} — 认知模型注册表只读列表。
 *
 * <p>路径与 query 参数名全部取自契约 {@code listCognitiveModels}；本类不含任何 URL 字面量。</p>
 */
public final class CognitionModelsListCommand {

    private static final String OPERATION = "listCognitiveModels";

    private CognitionModelsListCommand() {
    }

    public static Map<String, Object> run(OperationRegistry registry, CliOptions options,
                                          HttpTransport transport) {
        OperationRegistry.Operation op = registry.operation(OPERATION);
        String token = options.token();

        Map<String, String> query = new LinkedHashMap<>();
        for (OperationRegistry.Flag flag : registry.flagsOf(CliCommands.COGNITION_MODELS_LIST).values()) {
            String v = options.value(flag.flag());
            if (v != null && !v.isBlank()) {
                query.put(flag.parameter(), v);
            }
        }
        String pathWithQuery = op.path() + toQuery(query);

        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("operationId", op.operationId());
        ref.put("path", op.path());

        if (options.flag("--dry-run")) {
            Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, "ECOS-CLI-DRY-RUN",
                    "dry-run：未发请求", null, List.of(ref), transport.traceId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("plannedCall", transport.plannedCall(op.method(), pathWithQuery));
            data.put("query", query);
            out.put("data", data);
            return out;
        }

        if (token == null) {
            return EnvelopeMapper.local(CliStatus.BLOCKED, "ECOS-CLI-AUTH",
                    "未配置凭据（--token / ECOS_TOKEN / --token-file / --profile）",
                    "只读接口同样要求 JWT，禁匿名放行", List.of(ref), transport.traceId());
        }

        Map<String, Object> out = EnvelopeMapper.map(op.operationId(), op,
                transport.get(pathWithQuery, token), transport.traceId());
        return out;
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
