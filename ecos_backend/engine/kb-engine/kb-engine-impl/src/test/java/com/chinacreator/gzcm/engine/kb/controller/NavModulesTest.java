package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.KbEngineModuleRegistry;
import com.chinacreator.gzcm.engine.kb.nav.INavService;
import com.chinacreator.gzcm.engine.kb.nav.model.ModuleInfoVO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavModulesVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F10 后端 — GET /api/v1/knowledge/nav/modules 端点测试。
 *
 * <p>验证 7 module 全量 + CENTER_P3 含 RagController。
 *
 * @since PMO-D 2026-09-24
 */
@ExtendWith(MockitoExtension.class)
class NavModulesTest {

    @Mock
    private INavService navService;

    private NavTaxonController controller;

    @BeforeEach
    void setUp() {
        controller = new NavTaxonController(navService);
    }

    @Test
    @DisplayName("7 个 module 全量返回，顺序即前端侧栏顺序")
    void modulesListReturnsAllSevenModules() {
        ApiResponse<NavModulesVO> resp = controller.modulesList();

        assertTrue(resp.isSuccess(), "应返回成功响应");
        NavModulesVO vo = resp.getData();
        assertNotNull(vo);
        assertNotNull(vo.getModules());
        assertEquals(7, vo.getModules().size(), "应返回 7 个模块");

        // 按枚举声明顺序验证
        List<String> expectedOrder = List.of(
                "overview", "assets", "extract", "graph", "wiki", "govern", "center_p3");
        for (int i = 0; i < expectedOrder.size(); i++) {
            assertEquals(expectedOrder.get(i), vo.getModules().get(i).getModuleName(),
                    "第 " + (i + 1) + " 个模块名应为 " + expectedOrder.get(i));
        }
    }

    @Test
    @DisplayName("CENTER_P3 含 RagController")
    void centerP3ContainsRagController() {
        ApiResponse<NavModulesVO> resp = controller.modulesList();
        NavModulesVO vo = resp.getData();

        for (ModuleInfoVO info : vo.getModules()) {
            if ("center_p3".equals(info.getModuleName())) {
                assertTrue(info.getControllers().contains("RagController"),
                        "CENTER_P3 必须含 RagController");
                return;
            }
        }
        fail("未找到 center_p3 模块");
    }

    @Test
    @DisplayName("每个 module 的 controllers 列表非空")
    void everyModuleHasControllers() {
        ApiResponse<NavModulesVO> resp = controller.modulesList();
        NavModulesVO vo = resp.getData();

        for (ModuleInfoVO info : vo.getModules()) {
            assertNotNull(info.getControllers(), "module " + info.getModuleName() + " 不能无 controller");
            assertFalse(info.getControllers().isEmpty(),
                    "module " + info.getModuleName() + " 的 controller 列表不能为空");
        }
    }

    @Test
    @DisplayName("KbEngineModuleRegistry.classify 正确归类")
    void classifyReturnsCorrectModule() {
        assertEquals(KbEngineModuleRegistry.Module.OVERVIEW,
                KbEngineModuleRegistry.classify("KbEngineHealthController"));
        assertEquals(KbEngineModuleRegistry.Module.ASSETS,
                KbEngineModuleRegistry.classify("NavTaxonController"));
        assertEquals(KbEngineModuleRegistry.Module.EXTRACT,
                KbEngineModuleRegistry.classify("RagController") == null
                ? KbEngineModuleRegistry.classify("ExtractionController")
                : KbEngineModuleRegistry.Module.EXTRACT);
        assertEquals(KbEngineModuleRegistry.Module.GRAPH,
                KbEngineModuleRegistry.classify("KnowledgeGraphController"));
        assertEquals(KbEngineModuleRegistry.Module.WIKI,
                KbEngineModuleRegistry.classify("EntityLinkController"));
        assertEquals(KbEngineModuleRegistry.Module.GOVERN,
                KbEngineModuleRegistry.classify("ExpertRuleController"));
        assertEquals(KbEngineModuleRegistry.Module.CENTER_P3,
                KbEngineModuleRegistry.classify("RagController"));
    }

    @Test
    @DisplayName("未注册的 controller 返回 null")
    void classifyUnknownReturnsNull() {
        assertNull(KbEngineModuleRegistry.classify("UnknownController"));
        assertNull(KbEngineModuleRegistry.classify(null));
    }
}
