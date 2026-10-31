package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * #146aw 钉值门 — F6-07 六族末枚 workflows 规范前缀别名承运面契约固定。
 *
 * <p>台账 F6-07（行 93 / 行 364）要求六族 objects/links/domains/versions/
 * workflows/git-history 在 {@code /api/v1/ontology/**} 规范前缀下实装；
 * 本类为 workflows 族末枚，落 buszhi-impl（PMO-57 T2 断链后工作流端点
 * 单点在 buszhi，#146y 同族审批别名同款落位）。
 *
 * <p>钉值两枚（严格全等，禁 ≥/contains 宽松判据）：
 * <ol>
 *   <li>类级基址恰为 {@code /api/v1/ontology/workflows} —— 若退回旧前缀
 *       {@code /api/v1/ecos/workflows}（= 与属主 WorkflowController 同址
 *       重复承运）即红；</li>
 *   <li>方法级映射面恰为 17 枚字面量（与属主 WorkflowController 端面
 *       1:1 同型，顺序不敏感、多重集严格相等）—— 删/增一枚端点即红。</li>
 * </ol>
 *
 * <p>additive 口径：属主承运面 {@code /api/v1/ecos/workflows} 零改动
 * （架构铁律 API 只增不改），本门只钉别名面不碰属主面。
 */
public class OntologyWorkflowApiControllerContractTest {

    private static final String CANONICAL_BASE = "/api/v1/ontology/workflows";

    /** 属主 WorkflowController 端面 17 枚字面量的排序多重集（严格真值，非推算）。 */
    private static final List<String> EXPECTED_SURFACE = List.of(
        "", // GET 列表
        "", // POST 创建
        "/{id}", // GET 详情
        "/{id}", // PUT 更新
        "/{id}", // DELETE 删除
        "/{id}/clone",
        "/{id}/export",
        "/{id}/preview",
        "/{id}/publish",
        "/{id}/start",
        "/{id}/test",
        "/instances",
        "/instances/{instanceId}",
        "/instances/{instanceId}/resume",
        "/instances/{instanceId}/suspend",
        "/instances/{instanceId}/terminate",
        "/validate"
    );

    @Test
    void canonicalBaseIsOntologyWorkflows() {
        RequestMapping rm = OntologyWorkflowApiController.class
            .getAnnotation(RequestMapping.class);
        assertNotNull(rm, "别名承运面缺失类级 @RequestMapping");
        assertArrayEquals(new String[]{CANONICAL_BASE}, rm.value(),
            "F6-07 六族末枚规范前缀基址被篡改（additive 别名必须挂在 "
                + CANONICAL_BASE + "，旧前缀 /api/v1/ecos/workflows 由属主独享）");
    }

    @Test
    void surfaceIsExactlySeventeenOwnerShapedEndpoints() {
        List<String> actual = new ArrayList<>();
        for (Method m : OntologyWorkflowApiController.class.getDeclaredMethods()) {
            for (Class<? extends Annotation> anno : methodMappingAnnotations()) {
                Annotation a = m.getAnnotation(anno);
                if (a == null) continue;
                List<String> values = literalValues(anno, a);
                // 映射注解无显式 value/path ⇒ 缺省空串数组 ⇒ 钉在类级基址上（字面量为空串 ""）
                actual.add(values.isEmpty() ? "" : values.get(0));
            }
        }
        assertEquals(sortedCopy(EXPECTED_SURFACE), sortedCopy(actual),
            "别名端面多重集与属主 WorkflowController 1:1 契约漂移（删/增/改名即红）");
        assertEquals(17, actual.size(),
            "端面基数 17 失守（实际 " + actual.size() + "）");
    }

    private static List<String> sortedCopy(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }

    private static List<Class<? extends Annotation>> methodMappingAnnotations() {
        return List.of(GetMapping.class, PostMapping.class, PutMapping.class,
            DeleteMapping.class, PatchMapping.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<String> literalValues(Class<? extends Annotation> anno, Annotation a) {
        try {
            Object v = anno.getMethod("value").invoke(a);
            if (v instanceof String[]) {
                String[] arr = (String[]) v;
                if (arr.length == 0) return List.of("");
                return List.of(arr[0]);
            }
            if (v instanceof String) return List.of((String) v);
        } catch (Exception ignored) { /* 无 value 属性 → 试试 path */ }
        try {
            Object v = anno.getMethod("path").invoke(a);
            if (v instanceof String[]) {
                String[] arr = (String[]) v;
                if (arr.length == 0) return List.of("");
                return List.of(arr[0]);
            }
            if (v instanceof String) return List.of((String) v);
        } catch (Exception ignored) { /* 无 path 属性 */ }
        return List.of("");
    }
}
