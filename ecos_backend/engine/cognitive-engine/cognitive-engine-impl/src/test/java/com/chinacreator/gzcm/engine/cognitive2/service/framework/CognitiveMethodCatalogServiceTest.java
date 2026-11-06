package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 认知方法目录：内置字典 + 已发布框架 usedBy 关联；未知方法 404。 */
class CognitiveMethodCatalogServiceTest {

    private final CognitiveFrameworkRegistryService registry = mock(CognitiveFrameworkRegistryService.class);
    private final CognitiveMethodCatalogService service = new CognitiveMethodCatalogService(registry);

    @Test
    void builtinsAreListedEvenWithoutFrameworks() {
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of());
        List<Map<String, Object>> methods = service.catalog();
        assertTrue(methods.size() >= 5);
        assertTrue(methods.stream().anyMatch(m -> "ABDUCTION".equals(m.get("methodId"))));
        assertTrue(methods.stream().allMatch(m -> Boolean.FALSE.equals(m.get("calibrated"))));
    }

    @Test
    void publishedFrameworkDeclarationsBecomeUsedBy() {
        CognitiveFrameworkDefinition f = new CognitiveFrameworkDefinition();
        f.setFrameworkId("CF-03-03");
        f.setVersion("1.0.0");
        f.setMethods(List.of(Map.of("methodId", "ABDUCTION", "stepId", "s2", "name", "溯因")));
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of(f));

        Map<String, Object> method = service.get("ABDUCTION");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> usedBy = (List<Map<String, Object>>) method.get("usedBy");
        assertEquals(1, usedBy.size());
        assertEquals("CF-03-03", usedBy.get(0).get("frameworkId"));
        assertEquals("s2", usedBy.get(0).get("stepId"));
    }

    @Test
    void unknownMethodIs404() {
        when(registry.list(null, null, "PUBLISHED")).thenReturn(List.of());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.get("NO_SUCH_METHOD"));
        assertEquals(CognitiveErrorCode.E404_MODEL_ROW, e.errorCode);
    }
}
