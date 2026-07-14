package com.chinacreator.gzcm.runtime.core.compliance.classification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.security.policy.model.DataClassificationPolicy;
import com.chinacreator.gzcm.sysman.compliance.classification.DataClassificationService;

/**
 * 数据分级分类服务实现
 * 提供数据分级分类功能，包括自动分级分类、查询和统计
 */
@Service
public class DataClassificationServiceImpl implements DataClassificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataClassificationServiceImpl.class);
    
    // 内存存储（实际应使用数据库）
    private final Map<String, ClassificationResult> classificationStore = new ConcurrentHashMap<>();
    
    // 分级分类策略（实际应从配置或数据库加载）
    private DataClassificationPolicy classificationPolicy;
    
    public DataClassificationServiceImpl() {
        // 初始化默认策略
        initializeDefaultPolicy();
    }
    
    /**
     * 初始化默认策略
     */
    private void initializeDefaultPolicy() {
        classificationPolicy = new DataClassificationPolicy();
        
        // 初始化分级标准
        Map<String, DataClassificationPolicy.ClassificationLevel> levels = new HashMap<>();
        
        DataClassificationPolicy.ClassificationLevel publicLevel = new DataClassificationPolicy.ClassificationLevel();
        publicLevel.setLevelCode("PUBLIC");
        publicLevel.setLevelName("公开");
        publicLevel.setLevelValue(1);
        levels.put("PUBLIC", publicLevel);
        
        DataClassificationPolicy.ClassificationLevel internalLevel = new DataClassificationPolicy.ClassificationLevel();
        internalLevel.setLevelCode("INTERNAL");
        internalLevel.setLevelName("内部");
        internalLevel.setLevelValue(2);
        levels.put("INTERNAL", internalLevel);
        
        DataClassificationPolicy.ClassificationLevel secretLevel = new DataClassificationPolicy.ClassificationLevel();
        secretLevel.setLevelCode("SECRET");
        secretLevel.setLevelName("秘密");
        secretLevel.setLevelValue(3);
        levels.put("SECRET", secretLevel);
        
        DataClassificationPolicy.ClassificationLevel confidentialLevel = new DataClassificationPolicy.ClassificationLevel();
        confidentialLevel.setLevelCode("CONFIDENTIAL");
        confidentialLevel.setLevelName("机密");
        confidentialLevel.setLevelValue(4);
        levels.put("CONFIDENTIAL", confidentialLevel);
        
        classificationPolicy.setLevels(levels);
        
        // 初始化自动分类规则
        List<DataClassificationPolicy.AutoClassificationRule> autoRules = new ArrayList<>();
        
        // PII规则
        DataClassificationPolicy.AutoClassificationRule piiRule = new DataClassificationPolicy.AutoClassificationRule();
        piiRule.setRuleId("pii-rule-1");
        piiRule.setRuleName("PII识别规则");
        piiRule.setPattern("(身份证|手机号|邮箱|姓名|地址)");
        piiRule.setPatternType("KEYWORD");
        piiRule.setDataType("PII");
        piiRule.setLevel("SECRET");
        autoRules.add(piiRule);
        
        // 金融数据规则
        DataClassificationPolicy.AutoClassificationRule financialRule = new DataClassificationPolicy.AutoClassificationRule();
        financialRule.setRuleId("financial-rule-1");
        financialRule.setRuleName("金融数据识别规则");
        financialRule.setPattern("(银行卡|账户|余额|交易金额)");
        financialRule.setPatternType("KEYWORD");
        financialRule.setDataType("FINANCIAL");
        financialRule.setLevel("CONFIDENTIAL");
        autoRules.add(financialRule);
        
        classificationPolicy.setAutoRules(autoRules);
    }
    
    @Override
    public ClassificationResult classify(String resourceId, String resourceType, 
                                         Map<String, Object> resourceMetadata) throws ClassificationException {
        if (resourceId == null || resourceType == null) {
            throw new ClassificationException("资源ID和资源类型不能为空");
        }
        
        try {
            // 尝试自动分类
            ClassificationResult result = autoClassify(resourceId, resourceType, resourceMetadata);
            
            // 如果自动分类失败，使用默认值
            if (result == null) {
                result = new ClassificationResult();
                result.setResourceId(resourceId);
                result.setResourceType(resourceType);
                result.setLevel("INTERNAL");
                result.setDataType("GENERAL");
                result.setMethod("DEFAULT");
                result.setReason("未匹配到分类规则，使用默认分类");
                result.setConfidence(0.5);
            }
            
            result.setClassifiedTime(System.currentTimeMillis());
            classificationStore.put(buildKey(resourceId, resourceType), result);
            
            logger.info("分级分类完成: resourceId={}, resourceType={}, level={}, dataType={}", 
                resourceId, resourceType, result.getLevel(), result.getDataType());
            return result;
        } catch (Exception e) {
            throw new ClassificationException("分级分类失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<ClassificationResult> batchClassify(List<Map<String, Object>> resources) throws ClassificationException {
        if (resources == null || resources.isEmpty()) {
            throw new ClassificationException("资源列表不能为空");
        }
        
        List<ClassificationResult> results = new ArrayList<>();
        for (Map<String, Object> resource : resources) {
            String resourceId = (String) resource.get("resourceId");
            String resourceType = (String) resource.get("resourceType");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) resource.get("metadata");
            
            ClassificationResult result = classify(resourceId, resourceType, metadata);
            results.add(result);
        }
        
        return results;
    }
    
    @Override
    public ClassificationResult getClassification(String resourceId, String resourceType) throws ClassificationException {
        if (resourceId == null || resourceType == null) {
            throw new ClassificationException("资源ID和资源类型不能为空");
        }
        
        ClassificationResult result = classificationStore.get(buildKey(resourceId, resourceType));
        if (result == null) {
            throw new ClassificationException("未找到分级分类信息: resourceId=" + resourceId + ", resourceType=" + resourceType);
        }
        
        return result;
    }
    
    @Override
    public ClassificationStatistics getStatistics(Map<String, Object> filter) throws ClassificationException {
        try {
            ClassificationStatistics statistics = new ClassificationStatistics();
            Map<String, Long> levelCount = new HashMap<>();
            Map<String, Long> dataTypeCount = new HashMap<>();
            Map<String, Map<String, Long>> levelDataTypeCount = new HashMap<>();
            
            // 统计所有分类结果
            for (ClassificationResult result : classificationStore.values()) {
                // 应用过滤条件
                if (filter != null) {
                    if (filter.containsKey("level") && !filter.get("level").equals(result.getLevel())) {
                        continue;
                    }
                    if (filter.containsKey("dataType") && !filter.get("dataType").equals(result.getDataType())) {
                        continue;
                    }
                    if (filter.containsKey("resourceType") && !filter.get("resourceType").equals(result.getResourceType())) {
                        continue;
                    }
                }
                
                // 统计分级
                levelCount.put(result.getLevel(), levelCount.getOrDefault(result.getLevel(), 0L) + 1);
                
                // 统计分类
                dataTypeCount.put(result.getDataType(), dataTypeCount.getOrDefault(result.getDataType(), 0L) + 1);
                
                // 统计分级×分类
                levelDataTypeCount.computeIfAbsent(result.getLevel(), k -> new HashMap<>())
                    .put(result.getDataType(), 
                        levelDataTypeCount.get(result.getLevel()).getOrDefault(result.getDataType(), 0L) + 1);
            }
            
            statistics.setLevelCount(levelCount);
            statistics.setDataTypeCount(dataTypeCount);
            statistics.setLevelDataTypeCount(levelDataTypeCount);
            statistics.setTotalCount(classificationStore.size());
            
            return statistics;
        } catch (Exception e) {
            throw new ClassificationException("查询统计失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void updateClassification(String resourceId, String resourceType, String level, 
                                    String dataType, String operator) throws ClassificationException {
        if (resourceId == null || resourceType == null) {
            throw new ClassificationException("资源ID和资源类型不能为空");
        }
        
        try {
            ClassificationResult result = classificationStore.get(buildKey(resourceId, resourceType));
            if (result == null) {
                result = new ClassificationResult();
                result.setResourceId(resourceId);
                result.setResourceType(resourceType);
            }
            
            if (level != null) {
                result.setLevel(level);
            }
            if (dataType != null) {
                result.setDataType(dataType);
            }
            result.setMethod("MANUAL");
            result.setReason("手动更新，操作者: " + operator);
            result.setClassifiedTime(System.currentTimeMillis());
            
            classificationStore.put(buildKey(resourceId, resourceType), result);
            logger.info("更新分级分类: resourceId={}, level={}, dataType={}, operator={}", 
                resourceId, level, dataType, operator);
        } catch (Exception e) {
            throw new ClassificationException("更新分级分类失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 自动分类
     */
    private ClassificationResult autoClassify(String resourceId, String resourceType, 
                                             Map<String, Object> resourceMetadata) {
        if (classificationPolicy == null || classificationPolicy.getAutoRules() == null) {
            return null;
        }
        
        // 提取资源文本（用于匹配）
        String resourceText = extractResourceText(resourceMetadata);
        
        // 遍历自动分类规则
        for (DataClassificationPolicy.AutoClassificationRule rule : classificationPolicy.getAutoRules()) {
            if (matchRule(rule, resourceText)) {
                ClassificationResult result = new ClassificationResult();
                result.setResourceId(resourceId);
                result.setResourceType(resourceType);
                result.setLevel(rule.getLevel());
                result.setDataType(rule.getDataType());
                result.setMethod("RULE");
                result.setReason("匹配规则: " + rule.getRuleName());
                result.setConfidence(rule.getConfidence() != null ? rule.getConfidence() : 0.8);
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * 提取资源文本
     */
    private String extractResourceText(Map<String, Object> resourceMetadata) {
        if (resourceMetadata == null) {
            return "";
        }
        
        StringBuilder text = new StringBuilder();
        if (resourceMetadata.containsKey("name")) {
            text.append(resourceMetadata.get("name").toString()).append(" ");
        }
        if (resourceMetadata.containsKey("description")) {
            text.append(resourceMetadata.get("description").toString()).append(" ");
        }
        if (resourceMetadata.containsKey("content")) {
            text.append(resourceMetadata.get("content").toString()).append(" ");
        }
        
        return text.toString();
    }
    
    /**
     * 匹配规则
     */
    private boolean matchRule(DataClassificationPolicy.AutoClassificationRule rule, String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String pattern = rule.getPattern();
        String patternType = rule.getPatternType();
        
        if ("KEYWORD".equals(patternType)) {
            // 关键词匹配
            return text.contains(pattern);
        } else if ("REGEX".equals(patternType)) {
            // 正则表达式匹配
            try {
                Pattern regex = Pattern.compile(pattern);
                return regex.matcher(text).find();
            } catch (Exception e) {
                logger.warn("正则表达式匹配失败: pattern={}, error={}", pattern, e.getMessage());
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 构建存储键
     */
    private String buildKey(String resourceId, String resourceType) {
        return resourceType + ":" + resourceId;
    }
}
