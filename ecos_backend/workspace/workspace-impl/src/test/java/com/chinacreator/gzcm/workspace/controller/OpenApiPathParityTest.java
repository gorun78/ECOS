package com.chinacreator.gzcm.workspace.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-15 场景 API 契约面反射门禁（详细设计-07 验收
 * {@code everyWorkspaceMappingHasAnOperationId}）。
 *
 * <p>设计 F07-15 要求：新契约端点与代码同批落地，且契约文档（docs/40-实现/ScenarioOpenApi.md）
 * 的 operationId 全集需覆盖场景域全部 HTTP 映射。本测试不依赖 Spring 上下文，直接反射
 * 场景域 controller 的三个 controller 数组，逐条校验：</p>
 * <ol>
 *   <li>每个 HTTP 映射方法（Get/Post/Put/Patch/Delete）都必须带 {@link Operation} 且
 *       {@code operationId} 非空 —— 缺一项即 FAIL（防"文档先于实现/契约漂移"重演）；</li>
 *   <li>operationId 全集无重复 —— 防契约文档 generation 时 opId 撞名；</li>
 *   <li>本批 F07 新增的关键契约端点（completeness / 认知读侧四件套 / binding-catalog /
 *       pre-validate / runs / minds / status 迁移）逐字存在（与 ScenarioOpenApi.md 对齐）；</li>
 *   <li>场景端点数量基线 = 39 —— 增端点须同批补 opId（用例 1 拦）+ 掉基线（本用例拦）。</li>
 * </ol>
 *
 * <p>覆盖范围限于**场景域（scenario-domain）** controller；workspace 内非场景域的
 * object / knowledge / workbook / objectql controller 不在本册 F07-15 契约面内，故不在此列。</p>
 */
class OpenApiPathParityTest {

    /** F07-15 场景域 controller（本册契约面全部属主）。 */
    private static final Class<?>[] SCENARIO_CONTROLLERS = {
            ScenarioController.class,
            ScenarioCognitiveController.class,
            ScenarioCognitionReadController.class,
            ScenarioMindsController.class,
            ScenarioBindingLinkController.class,
            ScenarioOptionsController.class,
            ScenarioRunController.class,
            ScenarioPreValidateController.class,
            ScenarioSandboxController.class,
            BindingCatalogController.class
    };

    /** 汇总 (http verb, 完整路径, operationId)。 */
    static final class Endpoint {
        final String label;
        final String full;
        final String op;
        Endpoint(String label, String full, String op) {
            this.label = label;
            this.full = full;
            this.op = op;
        }
    }

    private static boolean isHttpMapped(Method m) {
        return m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
    }

    private static String value(Annotation a) {
        if (a instanceof GetMapping g) return first(g.value(), g.path());
        if (a instanceof PostMapping p) return first(p.value(), p.path());
        if (a instanceof PutMapping p) return first(p.value(), p.path());
        if (a instanceof PatchMapping p) return first(p.value(), p.path());
        if (a instanceof DeleteMapping d) return first(d.value(), d.path());
        return "";
    }

    private static String first(String[] a, String[] b) {
        if (a != null && a.length > 0) return a[0];
        if (b != null && b.length > 0) return b[0];
        return "";
    }

    /** 完整路径 = 类级 @RequestMapping 前缀 + 方法级路径（若方法级已带 /api/ 全路径则直接用）。 */
    private static String classBase(Class<?> c) {
        RequestMapping rm = c.getAnnotation(RequestMapping.class);
        if (rm == null) return "";
        String base = first(rm.value(), rm.path());
        if (base == null) base = "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private List<Endpoint> allEndpoints() {
        List<Endpoint> out = new ArrayList<>();
        for (Class<?> c : SCENARIO_CONTROLLERS) {
            String base = classBase(c);
            for (Method m : c.getDeclaredMethods()) {
                if (!isHttpMapped(m)) continue;
                String mv = m.getAnnotation(GetMapping.class) != null ? value(m.getAnnotation(GetMapping.class))
                        : m.getAnnotation(PostMapping.class) != null ? value(m.getAnnotation(PostMapping.class))
                        : m.getAnnotation(PutMapping.class) != null ? value(m.getAnnotation(PutMapping.class))
                        : m.getAnnotation(PatchMapping.class) != null ? value(m.getAnnotation(PatchMapping.class))
                        : value(m.getAnnotation(DeleteMapping.class));
                String full = mv.startsWith("/api/") ? mv : join(base, mv);
                Operation o = m.getAnnotation(Operation.class);
                String op = o == null ? "" : o.operationId();
                out.add(new Endpoint(c.getSimpleName() + "#" + m.getName(), full, op));
            }
        }
        return out;
    }

    private static String join(String base, String mv) {
        String b = (base == null || base.isEmpty()) ? "" : base + "/";
        return b + (mv.startsWith("/") ? mv.substring(1) : mv);
    }

    /** (1) 每个场景域 HTTP 映射端点都必须带显式 operationId（非空）。 */
    @Test
    void everyScenarioMappingHasAnOperationId() {
        List<String> missing = new ArrayList<>();
        for (Endpoint e : allEndpoints()) {
            if (e.op.isBlank()) {
                missing.add(e.label + " " + e.full);
            }
        }
        assertTrue(!allEndpoints().isEmpty(), "应至少扫描到 1 个场景域端点（SCENARIO_CONTROLLERS 列表是否正确？）");
        assertTrue(missing.isEmpty(),
                "以下场景域端点缺少 @Operation(operationId)，契约生成 opId 会退化为方法名或撞名: " + missing);
    }

    /** (2) 场景域 operationId 全集无重复（防契约文档 operationId 冲突）。 */
    @Test
    void scenarioOperationIdsAreUnique() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> dups = new ArrayList<>();
        for (Endpoint e : allEndpoints()) {
            if (e.op.isBlank()) continue;
            if (!seen.add(e.op)) dups.add(e.op);
        }
        assertTrue(dups.isEmpty(), "场景域 operationId 重复: " + dups);
    }

    /** (3) 场景域端点数量基线（新增/删除端点须同批同步 opId + 本基线）。 */
    @Test
    void scenarioEndpointInventoryIsPopulated() {
        assertEquals(39, allEndpoints().size(),
                "场景域端点基线被改动 —— 与 docs/40-实现/.../ScenarioOpenApi.md 与台账同步更新，否则是契约漂移");
    }

    /** (4) 关键契约端点（F07 本批新增）逐字存在。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/workspace/scenarios/{id}/completeness",
            "/api/v1/workspace/scenarios/{id}/status",
            "/api/v1/workspace/binding-catalog",
            "/api/v1/workspace/scenarios/{id}/pre-validate",
            "/api/v1/workspace/scenarios/{id}/runs",
            "/api/v1/workspace/scenarios/{id}/minds",
            "/api/v1/business/scenarios/{id}/cognition/detect",
            "/api/v1/business/scenarios/{id}/cognition/operation-eval",
            "/api/v1/business/scenarios/{id}/cognition/hypotheses",
            "/api/v1/business/scenarios/{id}/cognition/beliefs"
    })
    void keyContractEndpointsExist(String path) {
        Set<String> paths = new LinkedHashSet<>();
        for (Endpoint e : allEndpoints()) paths.add(e.full);
        assertTrue(paths.contains(path),
                "契约端点缺失: " + path + "（实际 " + Arrays.toString(paths.stream().sorted().toArray()) + "）");
    }
}
