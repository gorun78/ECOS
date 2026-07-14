package com.chinacreator.gzcm.runtime.core.modelaccess;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;

/**
 * ModelAccessServiceImpl 单元测试
 */
@DisplayName("模型访问服务测试")
class ModelAccessServiceImplTest {

    private ModelAccessServiceImpl modelAccessService;

    @BeforeEach
    void setUp() {
        modelAccessService = new ModelAccessServiceImpl();
    }

    @Test
    @DisplayName("获取模型信息")
    void testGetModelInfo() throws Exception {
        String modelId = "test-model-001";
        ModelInfo info = modelAccessService.getModelInfo(modelId);
        assertNotNull(info);
        assertEquals(modelId, info.getModelId());
    }

    @Test
    @DisplayName("获取null模型ID应抛出异常")
    void testGetModelInfoWithNullId() {
        assertThrows(ModelAccessException.class, () -> {
            modelAccessService.getModelInfo(null);
        });
    }

    @Test
    @DisplayName("加载模型")
    void testLoadModel() throws Exception {
        String modelId = "test-model-001";
        String version = "1.0.0";

        ModelInfo info = modelAccessService.loadModel(modelId, version);
        assertNotNull(info);
        assertEquals(modelId, info.getModelId());
        assertEquals("LOADED", info.getStatus());
    }

    @Test
    @DisplayName("卸载模型")
    void testUnloadModel() throws Exception {
        String modelId = "test-model-001";
        modelAccessService.loadModel(modelId, "1.0.0");

        boolean unloaded = modelAccessService.unloadModel(modelId);
        assertTrue(unloaded);

        boolean isLoaded = modelAccessService.isModelLoaded(modelId);
        assertFalse(isLoaded);
    }

    @Test
    @DisplayName("检查模型是否已加载")
    void testIsModelLoaded() throws Exception {
        String modelId = "test-model-001";
        assertFalse(modelAccessService.isModelLoaded(modelId));

        modelAccessService.loadModel(modelId, "1.0.0");
        assertTrue(modelAccessService.isModelLoaded(modelId));
    }

    @Test
    @DisplayName("执行模型推理")
    @Disabled("需要外部OpenAI服务，跳过网络依赖测试")
    void testInfer() throws Exception {
        String modelId = "test-model-001";
        modelAccessService.loadModel(modelId, "1.0.0");

        String input = "测试输入";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("temperature", 0.7);

        InferenceResult result = modelAccessService.infer(modelId, input, parameters);
        assertNotNull(result);
        assertEquals(modelId, result.getModelId());
        assertNotNull(result.getOutput());
    }

    @Test
    @DisplayName("未加载模型推理应抛出异常")
    void testInferWithUnloadedModel() {
        String modelId = "test-model-001";
        assertThrows(ModelAccessException.class, () -> {
            modelAccessService.infer(modelId, "test input", null);
        });
    }

    @Test
    @DisplayName("批量推理")
    @Disabled("需要外部OpenAI服务，跳过网络依赖测试")
    void testInferBatch() throws Exception {
        String modelId = "test-model-001";
        modelAccessService.loadModel(modelId, "1.0.0");

        List<String> inputs = List.of("input1", "input2", "input3");
        Map<String, Object> parameters = new HashMap<>();

        List<InferenceResult> results = modelAccessService.inferBatch(modelId, inputs, parameters);
        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("获取已加载的模型列表")
    void testGetLoadedModels() throws Exception {
        modelAccessService.loadModel("model-001", "1.0.0");
        modelAccessService.loadModel("model-002", "1.0.0");

        List<ModelInfo> loaded = modelAccessService.getLoadedModels();
        assertNotNull(loaded);
        assertTrue(loaded.size() >= 2);
    }
}

