package com.chinacreator.gzcm.runtime.core.transform.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 杞崲缁撴灉妯″瀷
 * 
 * @author GZCM Runtime Team
 */
public class TransformResult {
    
    /**
     * 杈撳嚭鏁版嵁妗?
     */
    private DataFrame output;
    
    /**
     * 杞崲缁熻淇℃伅
     */
    private TransformStatistics statistics;
    
    /**
     * 閿欒淇℃伅鍒楄〃
     */
    private List<String> errors;
    
    /**
     * 璀﹀憡淇℃伅鍒楄〃
     */
    private List<String> warnings;
    
    /**
     * 鏄惁鎴愬姛
     */
    private boolean success = true;
    
    /**
     * 杞崲鑰楁椂锛堟绉掞級
     */
    private Long duration;
    
    /**
     * 鏋勯€犲嚱鏁?
     */
    public TransformResult() {
        this.output = new DataFrame();
        this.statistics = new TransformStatistics();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }
    
    /**
     * 娣诲姞閿欒淇℃伅
     * 
     * @param error 閿欒淇℃伅
     */
    public void addError(String error) {
        this.errors.add(error);
        this.success = false;
    }
    
    /**
     * 娣诲姞璀﹀憡淇℃伅
     * 
     * @param warning 璀﹀憡淇℃伅
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    // Getters and Setters
    
    public DataFrame getOutput() {
        return output;
    }
    
    public void setOutput(DataFrame output) {
        this.output = output;
    }
    
    public TransformStatistics getStatistics() {
        return statistics;
    }
    
    public void setStatistics(TransformStatistics statistics) {
        this.statistics = statistics;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public Long getDuration() {
        return duration;
    }
    
    public void setDuration(Long duration) {
        this.duration = duration;
    }
    
    /**
     * 杞崲缁熻淇℃伅
     */
    public static class TransformStatistics {
        /**
         * 杈撳叆璁板綍鏁?
         */
        private long inputCount;
        
        /**
         * 杈撳嚭璁板綍鏁?
         */
        private long outputCount;
        
        /**
         * 杩囨护璁板綍鏁?
         */
        private long filteredCount;
        
        /**
         * 閿欒璁板綍鏁?
         */
        private long errorCount;
        
        // Getters and Setters
        
        public long getInputCount() {
            return inputCount;
        }
        
        public void setInputCount(long inputCount) {
            this.inputCount = inputCount;
        }
        
        public long getOutputCount() {
            return outputCount;
        }
        
        public void setOutputCount(long outputCount) {
            this.outputCount = outputCount;
        }
        
        public long getFilteredCount() {
            return filteredCount;
        }
        
        public void setFilteredCount(long filteredCount) {
            this.filteredCount = filteredCount;
        }
        
        public long getErrorCount() {
            return errorCount;
        }
        
        public void setErrorCount(long errorCount) {
            this.errorCount = errorCount;
        }
    }
}

