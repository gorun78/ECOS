package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.config.entity.Config;

/**
 * 配置验证服务接口
 * 
 * @author CDRC Design Team
 */
public interface IConfigValidator {
    
    /**
     * 验证配置格式
     */
    ValidationResult validateFormat(Config config) throws ValidationException;
    
    /**
     * 验证配置依赖
     */
    ValidationResult validateDependencies(Config config) throws ValidationException;
    
    /**
     * 验证配置完整）?
     */
    ValidationResult validateIntegrity(Config config) throws ValidationException;
    
    /**
     * 验证配置（全部验证）
     */
    ValidationResult validate(Config config) throws ValidationException;
    
    /**
     * 验证结果
     */
    class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
    }
    
    /**
     * 验证异常
     */
    class ValidationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


