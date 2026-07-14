package com.chinacreator.gzcm.runtime.core.format;

import java.io.InputStream;

import com.chinacreator.gzcm.runtime.core.format.model.FormatMetadata;

/**
 * 鏍煎紡楠岃瘉鍣ㄦ帴鍙?
 * 
 * @author CDRC Runtime Team
 */
public interface FormatValidator {
    
    /**
     * 楠岃瘉鏍煎紡
     * 
     * @param input 杈撳叆娴?
     * @param format 鏍煎紡
     * @param metadata 鏍煎紡鍏冩暟鎹?
     * @return 楠岃瘉缁撴灉
     * @throws FormatException
     */
    ValidationResult validate(InputStream input, Format format, FormatMetadata metadata) 
            throws FormatException;
    
    /**
     * 楠岃瘉缁撴灉
     */
    class ValidationResult {
        private boolean valid;
        private String message;
        private java.util.List<String> errors;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
            this.errors = new java.util.ArrayList<>();
        }
        
        public ValidationResult(boolean valid, String message, java.util.List<String> errors) {
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
        
        public java.util.List<String> getErrors() {
            return errors;
        }
        
        public void addError(String error) {
            this.errors.add(error);
        }
    }
}

