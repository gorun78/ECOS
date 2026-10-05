package com.chinacreator.gzcm.workspace.fc;

import com.chinacreator.gzcm.common.base.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分册09 F09-01/02/03 · 口径目录工作区 REST API（R-60② 已批准：口径主权在 ontology，
 * workspace 侧 7 端点全部走 delegation/hint——生产链路 workspace 通过 gateway 反向代理
 * 步骤会调用 {@code /api/v1/ontology/calibers...}；本批次 documenting the 契约位置，
 * live 状态下此 controller <b>只做路由提示</b>，避免分享电子表之外的"第五套口径源"）。
 *
 * <p>本层纪律：</p>
 * <ul>
 *   <li>R-60②：workspace 不建第二套口径写入栈；7 端点全部提供, 但 write-side 一律走
 *       ontology 已有的 {@code /api/v1/ontology/calibers...}（4 关校验 + 审批 + Git 归档）。
 *       #1/#2 读侧可以做 hint.routes；#3~#6 写侧 hint code=501 + routingNote 指向 ontology 地址；
 *       #7 validate 是 outlier —— workspace 侧允许直接以 {@code CaliberDsl.validate}
 *       做 4 关校验（白名单函数 / 变量形态 / 引用闭合 / 量纲），返回结构化错误详情。
 * </ul>
 *
 * <p>护栏：本类不写任何金额计算（铁律 §0.6 / C215）；六要素在同一 run 语义下由
 * FcForecastRunController 承载；本类只口径元数据。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/calibers")
public class FcCaliberController {

    private static final Logger log = LoggerFactory.getLogger(FcCaliberController.class);

    private static final String ONT_PREFIX = "/api/v1/ontology/calibers";

    // ── #1 GET /calibers —— listCalibers · 委托 ontology（生产链路 gateway 反向代理）──
    @Operation(operationId = "listCalibers", summary = "listCalibers（delegated to ontology via gateway）")
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String code,
                                                  @RequestParam(required = false) String status) {
        return response("listCalibers", "GET " + ONT_PREFIX
                + "?code=" + (code == null ? "" : code)
                + "&status=" + (status == null ? "" : status), null);
    }

    @Operation(operationId = "getCaliber", summary = "getCaliber（delegated to ontology）")
    @GetMapping("/{caliberId}")
    public ApiResponse<Map<String, Object>> getOne(@PathVariable String caliberId) {
        return response("getCaliber", "GET " + ONT_PREFIX + "/{caliberId}", null);
    }

    @Operation(operationId = "createCaliber", summary = "createCaliber（write-side delegated → ontology；本层 501 阻断避免第五套口径源 R-60②）")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        return writeSideDeny("createCaliber", "POST " + ONT_PREFIX + "（body 4 关校验 + status=DRAFT）");
    }

    @Operation(operationId = "submitCaliber", summary = "submitCaliber（write-side delegated → ontology）")
    @PostMapping("/{caliberId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String caliberId) {
        return writeSideDeny("submitCaliber", "POST " + ONT_PREFIX + "/{code}/versions (status=DRAFT→PENDING_APPROVAL path in ontology)");
    }

    @Operation(operationId = "approveCaliber", summary = "approveCaliber（write-side delegated → ontology，OPA + 审计经 Kafka ecos.audit）")
    @PostMapping("/{caliberId}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable String caliberId) {
        return writeSideDeny("approveCaliber", "POST " + ONT_PREFIX + "/{code}/versions (status=PENDING_APPROVAL→APPROVED，OPA allow + Kafka 审计)");
    }

    @Operation(operationId = "supersedeCaliber", summary = "supersedeCaliber（write-side delegated → ontology；change_reason 必填）")
    @PostMapping("/{caliberId}/supersede")
    public ApiResponse<Map<String, Object>> supersede(@PathVariable String caliberId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return writeSideDeny("supersedeCaliber", "POST " + ONT_PREFIX + "/{code}/versions (new version + change_reason required)");
    }

    /**
     * #7 — 本层唯一"实装"的端点：口径 4 关校验 preview（不落库）。
     * <p>消费方：口径编辑页第 3 步预检。承载 {@code com.chinacreator.gzcm.engine.cognitive2.fc.CaliberDsl} 白名单 4 关
     * 校验（V1 语法/V2 引用闭合/V3 量纲/V4 无环）。空 known_symbols 时跳过 V2；空 symbol_units/expected_unit 时跳过 V3。</p>
     */
    @Operation(operationId = "validateCaliber", summary = "validateCaliber（本层实装，4 关静态校验，不落库）")
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestBody Map<String, Object> dto) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object exprObj = dto == null ? null : dto.get("formula");
        if (!(exprObj instanceof String expr) || expr.isBlank()) {
            out.put("ok", false);
            out.put("violations", List.of("formula 缺失或空串"));
            return ApiResponse.success(out);
        }
        com.chinacreator.gzcm.engine.cognitive2.fc.CaliberDsl.ValidationResult res =
                com.chinacreator.gzcm.engine.cognitive2.fc.CaliberDsl.validate(
                        expr,
                        asStringSet(dto.get("knownSymbols")),
                        asStringMap(dto.get("symbolUnits")),
                        asString(dto.get("expectedUnit")));
        out.put("ok", res.passed());
        out.put("violations", res.violations());
        out.put("engine", "com.chinacreator.gzcm.engine.cognitive2.fc.CaliberDsl (white-list 4 gates: V1 syntax, V2 ref-closure, V3 dimension, V4 no-cycle)");
        log.debug("validateCaliber ok={} violations={}", res.passed(), res.violations());
        return ApiResponse.success(out);
    }

    // ── 私有辅助 ──
    private ApiResponse<Map<String, Object>> response(String op, String routing, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", op);
        m.put("routingTo", routing);
        m.put("status", "delegated (production: gateway reverse-proxy → ontology)");
        if (note != null) m.put("note", note);
        m.put("layer", "workspace-scene (R-60②: workspace 不建口径写栈)");
        return ApiResponse.success(m);
    }

    private ApiResponse<Map<String, Object>> writeSideDeny(String op, String routing) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", op);
        m.put("denied", true);
        m.put("reason", "R-60② approved: workspace 侧不建第二套口径写栈；write-side 唯一入口 = ontology engine（4 关 / 审批 / Git 归档）");
        m.put("routingTo", routing);
        m.put("hint", "如需写入口，请直接调用 routingTo 指向的 ontology API（X-User-Id header 传操作人）");
        return ApiResponse.success(m);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> asStringSet(Object o) {
        if (!(o instanceof List<?> l)) return null;
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (Object x : l) if (x != null) s.add(String.valueOf(x));
        return s;
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, String> asStringMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) return null;
        java.util.LinkedHashMap<String, String> r = new java.util.LinkedHashMap<>();
        for (var e : m.entrySet()) if (e.getKey() != null && e.getValue() != null) r.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        return r;
    }
    private static String asString(Object o) { return o == null ? null : String.valueOf(o); }
}
