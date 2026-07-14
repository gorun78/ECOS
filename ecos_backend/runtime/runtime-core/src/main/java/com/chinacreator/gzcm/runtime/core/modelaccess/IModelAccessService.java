package com.chinacreator.gzcm.runtime.core.modelaccess;

import java.util.List;
import java.util.Map;

/**
 * 妯″瀷璁块棶鏈嶅姟鎺ュ彛銆?
 * 鎻愪緵妯″瀷鍔犺浇銆佸嵏杞戒笌鎺ㄧ悊绛夎兘鍔涖€?
 */
public interface IModelAccessService {

    ModelInfo loadModel(String modelId, String version) throws ModelAccessException;

    boolean unloadModel(String modelId) throws ModelAccessException;

    InferenceResult infer(String modelId, String input, Map<String, Object> parameters) throws ModelAccessException;

    List<InferenceResult> inferBatch(String modelId, List<String> inputs, Map<String, Object> parameters)
            throws ModelAccessException;

    boolean isModelLoaded(String modelId);

    ModelInfo getModelInfo(String modelId) throws ModelAccessException;

    List<ModelInfo> getLoadedModels();
}


