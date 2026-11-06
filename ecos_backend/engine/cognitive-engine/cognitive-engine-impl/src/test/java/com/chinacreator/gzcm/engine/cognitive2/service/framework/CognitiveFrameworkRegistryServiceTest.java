package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.framework.CognitiveFrameworkDefinition;
import com.chinacreator.gzcm.engine.cognitive2.dao.CognitiveFrameworkDao;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 框架版本注册/发布门（CE-03/CE-04）：同版本冲突、已发布不可变、契约不全拒绝发布。 */
class CognitiveFrameworkRegistryServiceTest {

    private CognitiveFrameworkDao dao;
    private CognitiveFrameworkRegistryService service;

    @BeforeEach
    void setUp() {
        dao = mock(CognitiveFrameworkDao.class);
        service = new CognitiveFrameworkRegistryService(dao);
    }

    @Test
    void registerForcesDraftAndComputesContentHash() {
        when(dao.exists("CF-01-01", "1.0.0")).thenReturn(false);
        CognitiveFrameworkDefinition def = service.register(body(), "tester");
        assertEquals("DRAFT", def.getStatus());
        assertNotNull(def.getContentHash());
        verify(dao).insert(any(), anyString());
    }

    @Test
    void unknownExecutorTypeIsRejected() {
        Map<String, Object> body = new java.util.LinkedHashMap<>(body());
        body.put("steps", List.of(Map.of("stepId", "s1", "executorType", "arbitrary_code")));
        when(dao.exists("CF-01-01", "1.0.0")).thenReturn(false);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.register(body, "tester"));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        verify(dao, never()).insert(any(), anyString());
    }

    @Test
    void duplicateVersionRegistrationIsRejected() {
        when(dao.exists("CF-01-01", "1.0.0")).thenReturn(true);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.register(body(), "tester"));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, e.errorCode);
        verify(dao, never()).insert(any(), anyString());
    }

    @Test
    void publishedVersionCannotBePublishedAgainOrMutated() {
        CognitiveFrameworkDefinition published = definition("PUBLISHED");
        when(dao.find("CF-01-01", "1.0.0")).thenReturn(Optional.of(published));
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.publish("CF-01-01", "1.0.0", "tester"));
        assertEquals(CognitiveErrorCode.E409_MODEL_IMMUTABLE, e.errorCode);
        verify(dao, never()).publish(anyString(), anyString(), anyString());
    }

    @Test
    void publishRequiresResolvableContractAndNoSideEffects() {
        CognitiveFrameworkDefinition draft = definition("DRAFT");
        draft.setInputSchemaRef("");
        when(dao.find("CF-01-01", "1.0.0")).thenReturn(Optional.of(draft));
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.publish("CF-01-01", "1.0.0", "tester"));
        assertEquals(CognitiveErrorCode.E422_GATES_NOT_PASSED, e.errorCode);
    }

    @Test
    void legalTransitionsAreGuarded() {
        when(dao.find("CF-01-01", "1.0.0")).thenReturn(Optional.of(definition("PUBLISHED")));
        CognitiveGuardException illegal = assertThrows(CognitiveGuardException.class,
                () -> service.transition("CF-01-01", "1.0.0", "VALIDATING", "tester"));
        assertEquals(CognitiveErrorCode.E409_STATE_ILLEGAL, illegal.errorCode);
    }

    private static Map<String, Object> body() {
        return Map.of(
                "frameworkId", "CF-01-01",
                "version", "1.0.0",
                "name", "问题定义",
                "category", "PROBLEM_FRAMING",
                "steps", List.of(Map.of("stepId", "s1")),
                "inputSchemaRef", "ecos://in",
                "outputSchemaRef", "ecos://out");
    }

    private static CognitiveFrameworkDefinition definition(String status) {
        CognitiveFrameworkDefinition d = new CognitiveFrameworkDefinition();
        d.setFrameworkId("CF-01-01");
        d.setVersion("1.0.0");
        d.setName("x");
        d.setCategory("c");
        d.setStatus(status);
        d.setInputSchemaRef("ecos://in");
        d.setOutputSchemaRef("ecos://out");
        d.setContractResolvable(true);
        d.setSteps(List.of(Map.of("stepId", "s1")));
        d.setGovernance(Map.of("allowDirectSideEffects", false));
        return d;
    }
}
