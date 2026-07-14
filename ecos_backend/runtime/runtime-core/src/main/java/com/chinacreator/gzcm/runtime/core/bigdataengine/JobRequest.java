package com.chinacreator.gzcm.runtime.core.bigdataengine;

import java.util.Map;

/**
 * 浣滀笟鎻愪氦璇锋眰銆?
 */
public class JobRequest {

    private String jobName;
    private String engineType;
    private String code;
    private String codeType;
    private Map<String, String> options;

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCodeType() {
        return codeType;
    }

    public void setCodeType(String codeType) {
        this.codeType = codeType;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }
}


