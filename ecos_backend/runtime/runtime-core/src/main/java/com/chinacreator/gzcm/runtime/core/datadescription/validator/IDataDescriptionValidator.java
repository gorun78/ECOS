package com.chinacreator.gzcm.runtime.core.datadescription.validator;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;

/**
 * 鏁版嵁鎻忚堪楠岃瘉鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface IDataDescriptionValidator {
    
    /**
     * 楠岃瘉鏁版嵁鎻忚堪
     * 
     * @param description 鏁版嵁鎻忚堪瀵硅薄
     * @return 楠岃瘉缁撴灉
     * @throws Exception
     */
    ValidationResult validateDescription(DataDescription description) throws Exception;
    
    /**
     * 楠岃瘉鏁版嵁鏄惁绗﹀悎鎻忚堪
     * 
     * @param description 鏁版嵁鎻忚堪瀵硅薄
     * @param data 寰呴獙璇佺殑鏁版嵁
     * @return 楠岃瘉缁撴灉
     * @throws Exception
     */
    ValidationResult validateData(DataDescription description, Object data) throws Exception;
    
    /**
     * 楠岃瘉缁撴灉
     */
    class ValidationResult {
        private boolean valid;
        private String message;
        private List<String> errors;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
            this.errors = new java.util.ArrayList<>();
        }
        
        public ValidationResult(boolean valid, String message, List<String> errors) {
            this.valid = valid;
            this.message = message;
            this.errors = errors != null ? errors : new java.util.ArrayList<>();
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public void addError(String error) {
            this.errors.add(error);
        }
    }
}

