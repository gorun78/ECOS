package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.engine.cognitive2.service.asset.CognitiveAssetLifecycleService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C 资产读取面：复用受控 resolve；版本必载；不存在/异常不折叠为空成功。 */
class CognitiveAssetContextSourceReaderTest {

    private final CognitiveAssetLifecycleService lifecycle = mock(CognitiveAssetLifecycleService.class);
    private final CognitiveAssetContextSourceReader reader = new CognitiveAssetContextSourceReader(lifecycle);

    @Test
    void supportsCognitiveOnly() {
        assertTrue(reader.supports("COGNITIVE"));
        assertFalse(reader.supports("KNOWLEDGE"));
        assertFalse(reader.supports("CAPABILITY"));
    }

    @Test
    void resolvesAssetWithVersion() {
        when(lifecycle.resolve("hyp-1")).thenReturn(Map.of(
                "id", "hyp-1", "status", "PROPOSED", "updatedAt", "2026-11-03T00:00:00Z"));
        ContextSourceReader.SourceReadResult r = reader.read(new ContextBuildRequest(), ref("hyp-1"));
        assertEquals("2026-11-03T00:00:00Z", r.getVersion());
        assertEquals("hyp-1", r.getAssetRefs().get(0).getRefId());
        assertEquals("PROPOSED", r.getAssetRefs().get(0).getStatus());
    }

    @Test
    void missingVersionOrResolveFailureIsNotAnEmptySuccess() {
        when(lifecycle.resolve("hyp-2")).thenReturn(Map.of("id", "hyp-2"));
        when(lifecycle.resolve("missing")).thenThrow(new RuntimeException("not found"));
        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("hyp-2")));
        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref("missing")));
        assertThrows(ContextReadException.class,
                () -> reader.read(new ContextBuildRequest(), ref(" ")));
    }

    private static ContextSourceRef ref(String id) {
        ContextSourceRef r = new ContextSourceRef();
        r.setSourceType("COGNITIVE");
        r.setSourceId(id);
        r.setVersion("v1");
        r.setRequired(true);
        return r;
    }
}
