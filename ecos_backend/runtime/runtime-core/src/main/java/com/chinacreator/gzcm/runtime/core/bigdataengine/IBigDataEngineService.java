package com.chinacreator.gzcm.runtime.core.bigdataengine;

/**
 * 澶ф暟鎹紩鎿庤闂湇鍔℃帴鍙ｃ€?
 */
public interface IBigDataEngineService {

    EngineConnection connect(EngineConfig config) throws BigDataEngineException;

    JobResult submitJob(JobRequest request) throws BigDataEngineException;

    JobStatus getJobStatus(String jobId) throws BigDataEngineException;

    void cancelJob(String jobId) throws BigDataEngineException;

    String getJobLogs(String jobId) throws BigDataEngineException;
}


