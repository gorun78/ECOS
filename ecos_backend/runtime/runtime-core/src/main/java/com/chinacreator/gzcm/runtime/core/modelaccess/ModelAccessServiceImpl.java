package com.chinacreator.gzcm.runtime.core.modelaccess;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.modelaccess.InferenceResult;
import com.chinacreator.gzcm.runtime.core.modelaccess.ModelInfo;
import com.chinacreator.gzcm.runtime.core.modelaccess.ModelAccessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模型访问服务实现
 * 提供模型加载、推理和管理功能
 * 支持 OpenAI API、Azure OpenAI 和本地模型
 * 
 * @author CDRC Runtime Team
 */
public class ModelAccessServiceImpl implements IModelAccessService {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelAccessServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // 已加载的模型：modelId -> ModelInfo
    private final ConcurrentMap<String, ModelInfo> loadedModels = new ConcurrentHashMap<>();
    
    // 模型元数据：modelId -> ModelInfo（所有模型，包括未加载的）
    private final ConcurrentMap<String, ModelInfo> modelRegistry = new ConcurrentHashMap<>();
    
    // 推理历史：inferenceId -> InferenceResult
    private final ConcurrentMap<String, InferenceResult> inferenceHistory = new ConcurrentHashMap<>();
    
    // 模型适配器：modelId -> ModelAdapter
    private final ConcurrentMap<String, ModelAdapter> modelAdapters = new ConcurrentHashMap<>();
    
    @Override
    public ModelInfo getModelInfo(String modelId) throws ModelAccessException {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new ModelAccessException("Model ID cannot be null or empty");
        }
        
        ModelInfo info = modelRegistry.get(modelId);
        if (info == null) {
            // 如果不存在，创建一个默认的模型信息
            info = new ModelInfo();
            info.setModelId(modelId);
            info.setName("Unknown Model");
            info.setVersion("1.0.0");
            info.setStatus("UNLOADED");
        }
        
        // 检查是否已加载
        if (loadedModels.containsKey(modelId)) {
            info.setStatus("LOADED");
        }
        
        return info;
    }

    @Override
    public InferenceResult infer(String modelId, String input, Map<String, Object> parameters)
            throws ModelAccessException {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new ModelAccessException("Model ID cannot be null or empty");
        }
        
        if (!isModelLoaded(modelId)) {
            throw new ModelAccessException("Model " + modelId + " is not loaded");
        }
        
        if (input == null) {
            throw new ModelAccessException("Input cannot be null");
        }
        
        // 获取模型适配器
        ModelAdapter adapter = getModelAdapter(modelId);
        if (adapter == null) {
            throw new ModelAccessException("Model adapter not found for model: " + modelId);
        }
        
        // 执行推理
        long startTime = System.currentTimeMillis();
        try {
            String output = adapter.infer(input, parameters);
            long latency = System.currentTimeMillis() - startTime;
            
            InferenceResult result = new InferenceResult();
            result.setModelId(modelId);
            ModelInfo modelInfo = getModelInfo(modelId);
            if (modelInfo != null) {
                result.setModelVersion(modelInfo.getVersion());
            }
            result.setOutput(output);
            result.setLatency(latency);
            
            // 将输入和参数存储到metadata中
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("input", input);
            if (parameters != null) {
                metadata.putAll(parameters);
            }
            result.setMetadata(metadata);
            
            // 记录推理历史
            String inferenceId = UUID.randomUUID().toString();
            inferenceHistory.put(inferenceId, result);
            
            logger.debug("Inference completed for model: {}, latency: {}ms", modelId, latency);
            return result;
        } catch (Exception e) {
            logger.error("Failed to perform inference for model: " + modelId, e);
            throw new ModelAccessException("Inference failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ModelInfo loadModel(String modelId, String version) throws ModelAccessException {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new ModelAccessException("Model ID cannot be null or empty");
        }
        
        // 检查是否已加载
        if (loadedModels.containsKey(modelId)) {
            return loadedModels.get(modelId);
        }
        
        // 获取或创建模型信息
        ModelInfo info = modelRegistry.get(modelId);
        if (info == null) {
            info = new ModelInfo();
            info.setModelId(modelId);
            info.setName("Model " + modelId);
            info.setVersion(version != null ? version : "1.0.0");
            modelRegistry.put(modelId, info);
        }
        
        // 创建模型适配器（如果不存在）
        if (!modelAdapters.containsKey(modelId)) {
            ModelAdapter adapter = createModelAdapter(modelId, info);
            if (adapter != null) {
                modelAdapters.put(modelId, adapter);
            } else {
                throw new ModelAccessException("Failed to create model adapter for: " + modelId);
            }
        }
        
        info.setStatus("LOADED");
        info.setLoadedTime(System.currentTimeMillis());
        loadedModels.put(modelId, info);
        
        logger.info("Model loaded: {}", modelId);
        return info;
    }

    @Override
    public boolean unloadModel(String modelId) throws ModelAccessException {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new ModelAccessException("Model ID cannot be null or empty");
        }
        
        ModelInfo info = loadedModels.remove(modelId);
        if (info != null) {
            info.setStatus("UNLOADED");
            return true;
        }
        
        return false;
    }

    @Override
    public List<InferenceResult> inferBatch(String modelId, List<String> inputs,
            Map<String, Object> parameters) throws ModelAccessException {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new ModelAccessException("Model ID cannot be null or empty");
        }
        
        if (!isModelLoaded(modelId)) {
            throw new ModelAccessException("Model " + modelId + " is not loaded");
        }
        
        if (inputs == null || inputs.isEmpty()) {
            throw new ModelAccessException("Inputs cannot be null or empty");
        }
        
        List<InferenceResult> results = new ArrayList<>();
        for (String input : inputs) {
            InferenceResult result = infer(modelId, input, parameters);
            results.add(result);
        }
        
        return results;
    }

    @Override
    public boolean isModelLoaded(String modelId) {
        return modelId != null && loadedModels.containsKey(modelId);
    }

    @Override
    public List<ModelInfo> getLoadedModels() {
        return new ArrayList<>(loadedModels.values());
    }
    
    /**
     * 注册模型信息
     */
    public void registerModel(ModelInfo modelInfo) {
        if (modelInfo != null && modelInfo.getModelId() != null) {
            modelRegistry.put(modelInfo.getModelId(), modelInfo);
        }
    }
    
    /**
     * 获取推理历史
     */
    public List<InferenceResult> getInferenceHistory(String modelId) {
        List<InferenceResult> history = new ArrayList<>(inferenceHistory.values());
        if (modelId != null) {
            history.removeIf(r -> !modelId.equals(r.getModelId()));
        }
        return history;
    }
    
    /**
     * 获取推理历史（按ID）
     */
    public InferenceResult getInferenceById(String inferenceId) {
        return inferenceHistory.get(inferenceId);
    }
    
    /**
     * 获取模型适配器
     */
    private ModelAdapter getModelAdapter(String modelId) {
        return modelAdapters.get(modelId);
    }
    
    /**
     * 创建模型适配器
     * 根据模型类型创建相应的适配器
     */
    private ModelAdapter createModelAdapter(String modelId, ModelInfo modelInfo) {
        String modelType = modelInfo.getType();
        if (modelType == null || modelType.isEmpty()) {
            // 默认使用 OpenAI API
            modelType = "openai";
        }
        
        switch (modelType.toLowerCase()) {
            case "openai":
                return new OpenAIModelAdapter(modelId, modelInfo);
            case "azure-openai":
                return new AzureOpenAIModelAdapter(modelId, modelInfo);
            case "local":
            case "vllm":
            case "ollama":
                return new LocalModelAdapter(modelId, modelInfo);
            default:
                logger.warn("Unknown model type: {}, using OpenAI adapter", modelType);
                return new OpenAIModelAdapter(modelId, modelInfo);
        }
    }
    
    /**
     * 模型适配器接口
     */
    private interface ModelAdapter {
        String infer(String input, Map<String, Object> parameters) throws Exception;
    }
    
    /**
     * OpenAI 模型适配器
     */
    private static class OpenAIModelAdapter implements ModelAdapter {
        private final String modelId;
        @SuppressWarnings("unused")
        private final ModelInfo modelInfo;
        private final String apiKey;
        private final String apiEndpoint;
        
        public OpenAIModelAdapter(String modelId, ModelInfo modelInfo) {
            this.modelId = modelId;
            this.modelInfo = modelInfo;
            // 从环境变量或配置中获取 API 密钥
            this.apiKey = System.getenv("OPENAI_API_KEY");
            this.apiEndpoint = "https://api.openai.com/v1/chat/completions";
        }
        
        @Override
        public String infer(String input, Map<String, Object> parameters) throws Exception {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new ModelAccessException("OpenAI API key not configured");
            }
            
            // 构建请求
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelId);
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", input);
            messages.add(message);
            request.put("messages", messages);
            
            // 添加参数
            if (parameters != null) {
                if (parameters.containsKey("temperature")) {
                    request.put("temperature", parameters.get("temperature"));
                }
                if (parameters.containsKey("max_tokens")) {
                    request.put("max_tokens", parameters.get("max_tokens"));
                }
            }
            
            // 发送 HTTP 请求
            String requestBody = objectMapper.writeValueAsString(request);
            String response = sendHttpRequest(apiEndpoint, "POST", apiKey, requestBody);
            
            // 解析响应
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).get("message");
                if (messageNode != null) {
                    JsonNode content = messageNode.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
            
            throw new ModelAccessException("Failed to parse OpenAI response");
        }
    }
    
    /**
     * Azure OpenAI 模型适配器
     */
    private static class AzureOpenAIModelAdapter implements ModelAdapter {
        @SuppressWarnings("unused")
        private final String modelId;
        @SuppressWarnings("unused")
        private final ModelInfo modelInfo;
        private final String apiKey;
        private final String apiEndpoint;
        
        public AzureOpenAIModelAdapter(String modelId, ModelInfo modelInfo) {
            this.modelId = modelId;
            this.modelInfo = modelInfo;
            // 从环境变量或配置中获取 API 密钥和端点
            this.apiKey = System.getenv("AZURE_OPENAI_API_KEY");
            String resourceName = System.getenv("AZURE_OPENAI_RESOURCE_NAME");
            String deploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
            if (resourceName != null && deploymentName != null) {
                this.apiEndpoint = String.format("https://%s.openai.azure.com/openai/deployments/%s/chat/completions?api-version=2023-05-15",
                    resourceName, deploymentName);
            } else {
                this.apiEndpoint = null;
            }
        }
        
        @Override
        public String infer(String input, Map<String, Object> parameters) throws Exception {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new ModelAccessException("Azure OpenAI API key not configured");
            }
            if (apiEndpoint == null) {
                throw new ModelAccessException("Azure OpenAI endpoint not configured");
            }
            
            // 构建请求（与 OpenAI 类似）
            Map<String, Object> request = new HashMap<>();
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", input);
            messages.add(message);
            request.put("messages", messages);
            
            // 添加参数
            if (parameters != null) {
                if (parameters.containsKey("temperature")) {
                    request.put("temperature", parameters.get("temperature"));
                }
                if (parameters.containsKey("max_tokens")) {
                    request.put("max_tokens", parameters.get("max_tokens"));
                }
            }
            
            // 发送 HTTP 请求
            String requestBody = objectMapper.writeValueAsString(request);
            String response = sendHttpRequest(apiEndpoint, "POST", apiKey, requestBody);
            
            // 解析响应
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).get("message");
                if (messageNode != null) {
                    JsonNode content = messageNode.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
            
            throw new ModelAccessException("Failed to parse Azure OpenAI response");
        }
    }
    
    /**
     * 本地模型适配器（vLLM/Ollama）
     */
    private static class LocalModelAdapter implements ModelAdapter {
        private final String modelId;
        @SuppressWarnings("unused")
        private final ModelInfo modelInfo;
        private final String apiEndpoint;
        
        public LocalModelAdapter(String modelId, ModelInfo modelInfo) {
            this.modelId = modelId;
            this.modelInfo = modelInfo;
            // 从环境变量或配置中获取本地模型端点
            String endpoint = System.getenv("LOCAL_MODEL_ENDPOINT");
            if (endpoint == null || endpoint.isEmpty()) {
                endpoint = "http://localhost:8000/v1/chat/completions"; // 默认 vLLM 端点
            }
            this.apiEndpoint = endpoint;
        }
        
        @Override
        public String infer(String input, Map<String, Object> parameters) throws Exception {
            // 构建请求（与 OpenAI 兼容的格式）
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelId);
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", input);
            messages.add(message);
            request.put("messages", messages);
            
            // 添加参数
            if (parameters != null) {
                if (parameters.containsKey("temperature")) {
                    request.put("temperature", parameters.get("temperature"));
                }
                if (parameters.containsKey("max_tokens")) {
                    request.put("max_tokens", parameters.get("max_tokens"));
                }
            }
            
            // 发送 HTTP 请求（本地模型通常不需要 API 密钥）
            String requestBody = objectMapper.writeValueAsString(request);
            String response = sendHttpRequest(apiEndpoint, "POST", null, requestBody);
            
            // 解析响应
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).get("message");
                if (messageNode != null) {
                    JsonNode content = messageNode.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
            
            throw new ModelAccessException("Failed to parse local model response");
        }
    }
    
    /**
     * 发送 HTTP 请求
     */
    private static String sendHttpRequest(String urlString, String method, String apiKey, String requestBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setDoOutput(true);
        
        // 发送请求体
        if (requestBody != null && !requestBody.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }
        
        // 读取响应
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } else {
            // 读取错误响应
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorResponse = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    errorResponse.append(responseLine.trim());
                }
                throw new ModelAccessException("HTTP error " + responseCode + ": " + errorResponse.toString());
            }
        }
    }
}

