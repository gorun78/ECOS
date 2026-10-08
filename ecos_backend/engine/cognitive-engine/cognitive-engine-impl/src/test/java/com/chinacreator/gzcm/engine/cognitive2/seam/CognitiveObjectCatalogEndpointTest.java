package com.chinacreator.gzcm.engine.cognitive2.seam;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveObjectCatalogController;
import com.chinacreator.gzcm.engine.cognitive2.service.objects.CognitiveObjectCatalogService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对象目录只读端点验收（详细设计-05 §8.1 接缝义务「提供对象目录 + 只读解析端点」/ REQ-COG-06）。
 *
 * <p>与 {@code CognitiveObjectCatalogTest}（目录本体：11 类、载体/引用态二选一、禁名零命中）分工：
 * 本类钉的是<b>端点面</b>三条不变量 —— 返回态与枚举单源一致、解析失败 fail-loud、表面只读且前缀不漂移。
 * HTTP 状态码矩阵由 {@code CognitiveErrorAdvice} 承担（既有 advice 面），本类不断言传输层。</p>
 */
@DisplayName("C 层对象目录只读端点（§8.1 接缝）")
class CognitiveObjectCatalogEndpointTest {

    private static final int FROZEN_COUNT = 11;

    private final CognitiveObjectCatalogService service = new CognitiveObjectCatalogService();
    private final CognitiveObjectCatalogController controller = new CognitiveObjectCatalogController(service);

    @Test
    @DisplayName("GET /objects：11 类逐项返回，载体集合与枚举单源逐行一致（禁第二套清单）")
    void catalogEndpointReturnsFrozenEntriesFromSingleSource() {
        ApiResponse<List<CognitiveObjectClass.CatalogEntry>> resp = controller.catalog();
        List<CognitiveObjectClass.CatalogEntry> data = resp.getData();

        assertEquals(FROZEN_COUNT, data.size(), "端点返回类数必须等于附件冻结的 11 类");
        assertTrue(resp.isSuccess(), "目录读取不得返回错误态");

        List<String> carriers = new ArrayList<>();
        for (CognitiveObjectClass.CatalogEntry e : data) {
            boolean hasCarrier = e.carrier() != null && !e.carrier().isBlank();
            boolean hasReference = e.reference() != null && !e.reference().isBlank();
            assertTrue(hasCarrier ^ hasReference, e.objectKey() + "：载体与引用态必须二选一");
            if (hasCarrier) {
                assertTrue(e.carrier().contains("."), e.objectKey() + "：载体必须 schema 限定（DR01）");
                assertEquals("PHYSICAL", e.carrierKind(), e.objectKey() + "：有载体 ⇒ PHYSICAL");
                carriers.add(e.carrier());
            } else {
                assertEquals("REFERENCE", e.carrierKind(), e.objectKey() + "：有引用态 ⇒ REFERENCE");
            }
        }
        assertEquals(CognitiveObjectClass.physicalCarriers(), carriers,
                "端点载体清单必须与 CognitiveObjectClass 单源逐行一致（C.8 契约单源）");
    }

    @Test
    @DisplayName("GET /objects/{objectKey}：大小写/空白不敏感解析；未登记对象名 ⇒ ECOS-COG-400（禁静默空对象）")
    void resolveEndpointIsFailLoudOnUnknownKey() {
        assertEquals("belief", controller.resolve("  BELIEF ").getData().objectKey(),
                "目录 key 与 byKey 同口径（大小写/首尾空白不敏感）");

        CognitiveGuardException unknown = assertThrows(CognitiveGuardException.class,
                () -> controller.resolve("kb_cognitive_mind"));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, unknown.errorCode,
                "未登记对象名必须 fail-loud，不得回落默认载体或空对象");

        assertThrows(CognitiveGuardException.class, () -> controller.resolve(null),
                "null key 同样 fail-loud");
    }

    @Test
    @DisplayName("端点表面只读：类前缀取 COGNITIVE 常量、处理器全为 GET、路径取 OBJECTS_* 常量")
    void catalogSurfaceIsReadOnlyAndConstantDriven() {
        RequestMapping root = CognitiveObjectCatalogController.class.getAnnotation(RequestMapping.class);
        assertEquals(1, root.value().length, "类级前缀必须单值（禁顺手加 /api/ 旧前缀双写）");
        assertEquals(CognitiveApiPaths.COGNITIVE, root.value()[0],
                "必须复用既有 /api/v1/cognitive 前缀 ⇒ 不新增 VersionPrefixRewriteFilter 条目");

        int getHandlers = 0;
        for (Method m : CognitiveObjectCatalogController.class.getDeclaredMethods()) {
            assertTrue(m.getAnnotation(PostMapping.class) == null
                            && m.getAnnotation(PutMapping.class) == null
                            && m.getAnnotation(DeleteMapping.class) == null
                            && m.getAnnotation(PatchMapping.class) == null,
                    m.getName() + "：目录端点必须只读（出现写映射即违反 §8.1 的只读解析义务）");
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) {
                getHandlers++;
                assertEquals(1, get.value().length, m.getName() + "：路径必须取 CognitiveApiPaths 常量");
                assertTrue(CognitiveApiPaths.OBJECTS_REL.equals(get.value()[0])
                                || CognitiveApiPaths.OBJECTS_ITEM_REL.equals(get.value()[0]),
                        m.getName() + "：路径必须为 OBJECTS_REL 或 OBJECTS_ITEM_REL");
            }
        }
        assertEquals(2, getHandlers, "端点数 = 列表 + 单项解析 共 2 个 GET");
    }
}
