package com.chinacreator.gzcm.sysman.config.service.impl;

import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.service.IConfigValidator;

/**
 * 配置验证服务实现
 * 
 * @author CDRC Design Team
 */
public class ConfigValidatorImpl implements IConfigValidator {
    
    @Override
    public ValidationResult validateFormat(Config config) throws ValidationException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (config == null) {
            errors.add("配置不能为空");
            return new ValidationResult(false, errors, warnings);
        }
        
        String content = config.getConfigContent();
        if (content == null || content.trim().isEmpty()) {
            errors.add("配置内容不能为空");
        } else {
            // 简单的格式验证
            try {
                // 尝试解析JSON
                if (content.trim().startsWith("{")) {
                    // JSON格式验证（简化实现）
                    if (!content.trim().endsWith("}")) {
                        errors.add("JSON格式不完整");
                    }
                } else if (content.trim().startsWith("-") || content.contains(":")) {
                    // YAML格式验证（简化实现）
                    // 实际应该使用YAML解析
                } else {
                    warnings.add("无法识别的配置格式");
                }
            } catch (Exception e) {
                errors.add("配置格式解析失败: " + e.getMessage());
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public ValidationResult validateDependencies(Config config) throws ValidationException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 简化实现，实际应该解析配置内容，检查引用的资源
        // 例如：检查引用的数据产品、策略等是否存在
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public ValidationResult validateIntegrity(Config config) throws ValidationException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (config == null) {
            errors.add("配置不能为空");
            return new ValidationResult(false, errors, warnings);
        }
        
        // 检查必填字
        if (config.getConfigType() == null || config.getConfigType().trim().isEmpty()) {
            errors.add("配置类型不能为空");
        }
        
        if (config.getConfigName() == null || config.getConfigName().trim().isEmpty()) {
            errors.add("配置名称不能为空");
        }
        
        if (config.getConfigContent() == null || config.getConfigContent().trim().isEmpty()) {
            errors.add("配置内容不能为空");
        }
        
        // 检查配置类型是否有
        String[] validTypes = {"DATA_PRODUCT", "POLICY", "PIPELINE", "LLM_APP"};
        boolean validType = false;
        for (String type : validTypes) {
            if (type.equals(config.getConfigType())) {
                validType = true;
                break;
            }
        }
        if (!validType && config.getConfigType() != null) {
            errors.add("无效的配置类型: " + config.getConfigType());
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public ValidationResult validate(Config config) throws ValidationException {
        ValidationResult formatResult = validateFormat(config);
        ValidationResult integrityResult = validateIntegrity(config);
        ValidationResult dependencyResult = validateDependencies(config);
        
        List<String> allErrors = new ArrayList<>();
        allErrors.addAll(formatResult.getErrors());
        allErrors.addAll(integrityResult.getErrors());
        allErrors.addAll(dependencyResult.getErrors());
        
        List<String> allWarnings = new ArrayList<>();
        allWarnings.addAll(formatResult.getWarnings());
        allWarnings.addAll(integrityResult.getWarnings());
        allWarnings.addAll(dependencyResult.getWarnings());
        
        return new ValidationResult(allErrors.isEmpty(), allErrors, allWarnings);
    }
}


