package com.chinacreator.gzcm.runtime.core.logging.archive;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.logging.ILogArchiveService;
import com.chinacreator.gzcm.runtime.core.logging.archive.TieredArchiveStrategy;

/**
 * 日志归档调度器
 * 定时执行日志归档任务
 * 默认每天凌晨2点执行归档
 * 
 * @author CDRC Runtime Team
 */
public class LogArchiveScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(LogArchiveScheduler.class);

    private final ILogArchiveService archiveService;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    public LogArchiveScheduler(ILogArchiveService archiveService) {
        this.archiveService = archiveService;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "LogArchive-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动调度器
     * 每天凌晨2点执行归档任务
     */
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        
        // 计算到下一个凌晨2点的延迟时间
        long initialDelay = calculateInitialDelay();
        
        // 每天执行一次（24小时）
        scheduler.scheduleAtFixedRate(() -> {
            try {
                executeArchiveTasks();
            } catch (Exception e) {
                logger.error("执行归档任务失败", e);
            }
        }, initialDelay, 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 启动调度器（自定义执行时间）
     * 
     * @param hour 执行小时（0-23）
     * @param minute 执行分钟（0-59）
     */
    public void start(int hour, int minute) {
        if (running) {
            return;
        }
        
        running = true;
        
        // 计算到指定时间的延迟时间
        long initialDelay = calculateInitialDelay(hour, minute);
        
        // 每天执行一次（24小时）
        scheduler.scheduleAtFixedRate(() -> {
            try {
                executeArchiveTasks();
            } catch (Exception e) {
                logger.error("执行归档任务失败", e);
            }
        }, initialDelay, 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 停止调度器
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 执行归档任务
     */
    private void executeArchiveTasks() {
        System.out.println("开始执行日志归档任务: " + new Date());
        
        // 执行分级归档
        executeTieredArchive();
        
        System.out.println("日志归档任务执行完成: " + new Date());
    }
    
    /**
     * 执行分级归档
     */
    private void executeTieredArchive() {
        // 数据变更日志分级归档
        try {
            if (archiveService instanceof LogArchiveServiceImpl) {
                LogArchiveServiceImpl impl = (LogArchiveServiceImpl) archiveService;
                impl.archiveTiered("data_change");
                logger.info("数据变更日志分级归档完成");
            }
        } catch (Exception e) {
            logger.error("数据变更日志分级归档失败", e);
        }
        
        // 任务执行日志分级归档
        try {
            if (archiveService instanceof LogArchiveServiceImpl) {
                LogArchiveServiceImpl impl = (LogArchiveServiceImpl) archiveService;
                impl.archiveTiered("task_execution");
                logger.info("任务执行日志分级归档完成");
            }
        } catch (Exception e) {
            logger.error("任务执行日志分级归档失败", e);
        }
        
        // 用户操作日志分级归档
        try {
            if (archiveService instanceof LogArchiveServiceImpl) {
                LogArchiveServiceImpl impl = (LogArchiveServiceImpl) archiveService;
                impl.archiveTiered("user_operation");
                logger.info("用户操作日志分级归档完成");
            }
        } catch (Exception e) {
            logger.error("用户操作日志分级归档失败", e);
        }
        
        // 系统日志分级归档
        try {
            if (archiveService instanceof LogArchiveServiceImpl) {
                LogArchiveServiceImpl impl = (LogArchiveServiceImpl) archiveService;
                impl.archiveTiered("system");
                logger.info("系统日志分级归档完成");
            }
        } catch (Exception e) {
            logger.error("系统日志分级归档失败", e);
        }
    }
    
    /**
     * 计算到下一个凌晨2点的延迟时间
     */
    private long calculateInitialDelay() {
        return calculateInitialDelay(2, 0);
    }
    
    /**
     * 计算到指定时间的延迟时间
     */
    private long calculateInitialDelay(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        
        // 如果目标时间已过，设置为明天
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        return target.getTimeInMillis() - now.getTimeInMillis();
    }
    
    /**
     * 获取N天前的日期
     */
    private Date getDateBeforeDays(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return cal.getTime();
    }
    
    /**
     * 手动触发归档任务（用于测试）
     */
    public void triggerArchive() {
        executeArchiveTasks();
    }
}

