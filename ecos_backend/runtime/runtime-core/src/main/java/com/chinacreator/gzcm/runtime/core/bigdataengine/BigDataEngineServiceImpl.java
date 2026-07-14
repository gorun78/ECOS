package com.chinacreator.gzcm.runtime.core.bigdataengine;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 大数据引擎服务实现
 * 提供大数据引擎连接和作业管理功能
 * 
 * @author CDRC Runtime Team
 */
public class BigDataEngineServiceImpl implements IBigDataEngineService {
    
    // 连接池：configId -> EngineConnection
    private final ConcurrentMap<String, EngineConnection> connections = new ConcurrentHashMap<>();
    
    // 作业管理：jobId -> JobResult
    private final ConcurrentMap<String, JobResult> jobs = new ConcurrentHashMap<>();
    
    // 作业日志：jobId -> logs
    private final ConcurrentMap<String, StringBuilder> jobLogs = new ConcurrentHashMap<>();
    
    @Override
    public EngineConnection connect(EngineConfig config) throws BigDataEngineException {
        if (config == null) {
            throw new BigDataEngineException("Engine config cannot be null");
        }
        
        // 使用引擎类型和主URL作为配置ID
        String configId = config.getEngineType() + "_" + config.getMasterUrl();
        if (configId == null || configId.trim().isEmpty()) {
            configId = UUID.randomUUID().toString();
        }
        
        // 检查是否已存在连接
        EngineConnection existing = connections.get(configId);
        if (existing != null && existing.isConnected()) {
            return existing;
        }
        
        // 创建新连接
        EngineConnection connection = new EngineConnection();
        connection.setConnectionId(UUID.randomUUID().toString());
        connection.setEngineType(config.getEngineType());
        connection.setMasterUrl(config.getMasterUrl());
        connection.setConnected(true);
        
        connections.put(configId, connection);
        return connection;
    }

    @Override
    public JobResult submitJob(JobRequest request) throws BigDataEngineException {
        if (request == null) {
            throw new BigDataEngineException("Job request cannot be null");
        }
        
        String jobId = UUID.randomUUID().toString();
        
        JobResult result = new JobResult();
        result.setJobId(jobId);
        result.setStatus(JobStatus.SUBMITTED);
        result.setSubmitTime(System.currentTimeMillis());
        
        jobs.put(jobId, result);
        
        // 初始化作业日志
        jobLogs.put(jobId, new StringBuilder());
        appendJobLog(jobId, "Job submitted: " + jobId);
        
        // 模拟作业执行（实际应异步执行）
        // 这里简化为立即完成
        result.setStatus(JobStatus.RUNNING);
        appendJobLog(jobId, "Job started");
        
        // 模拟作业完成
        result.setStatus(JobStatus.SUCCEEDED);
        result.setFinishTime(System.currentTimeMillis());
        appendJobLog(jobId, "Job completed successfully");
        
        return result;
    }

    @Override
    public JobStatus getJobStatus(String jobId) throws BigDataEngineException {
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new BigDataEngineException("Job ID cannot be null or empty");
        }
        
        JobResult job = jobs.get(jobId);
        if (job == null) {
            throw new BigDataEngineException("Job with ID " + jobId + " not found");
        }
        
        return job.getStatus();
    }

    @Override
    public void cancelJob(String jobId) throws BigDataEngineException {
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new BigDataEngineException("Job ID cannot be null or empty");
        }
        
        JobResult job = jobs.get(jobId);
        if (job == null) {
            throw new BigDataEngineException("Job with ID " + jobId + " not found");
        }
        
        if (job.getStatus() == JobStatus.SUCCEEDED || job.getStatus() == JobStatus.FAILED) {
            throw new BigDataEngineException("Cannot cancel job in status: " + job.getStatus());
        }
        
        job.setStatus(JobStatus.CANCELLED);
        job.setFinishTime(System.currentTimeMillis());
        appendJobLog(jobId, "Job cancelled by user");
    }

    @Override
    public String getJobLogs(String jobId) throws BigDataEngineException {
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new BigDataEngineException("Job ID cannot be null or empty");
        }
        
        StringBuilder logs = jobLogs.get(jobId);
        if (logs == null) {
            throw new BigDataEngineException("Job logs for ID " + jobId + " not found");
        }
        
        return logs.toString();
    }
    
    /**
     * 追加作业日志
     */
    private void appendJobLog(String jobId, String log) {
        StringBuilder logs = jobLogs.computeIfAbsent(jobId, k -> new StringBuilder());
        logs.append(new Date()).append(" - ").append(log).append("\n");
    }
    
    /**
     * 断开连接
     */
    public void disconnect(String configId) {
        EngineConnection connection = connections.get(configId);
        if (connection != null) {
            connection.setConnected(false);
            connections.remove(configId);
        }
    }
    
    /**
     * 获取所有作业
     */
    public ConcurrentMap<String, JobResult> getAllJobs() {
        return new ConcurrentHashMap<>(jobs);
    }
}

