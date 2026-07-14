package com.chinacreator.gzcm.runtime.core.monitor.collection;

import java.util.List;
import java.util.Map;

/**
 * 鏁版嵁閲囬泦鐩戞帶鏈嶅姟鎺ュ彛銆?
 */
public interface ICollectionMonitorService {

    /**
     * 璁板綍閲囬泦浠诲姟寮€濮嬨€?
     */
    void recordStart(String taskId, Map<String, Object> metadata);

    /**
     * 鏇存柊閲囬泦浠诲姟杩涘害銆?
     */
    void updateProgress(String taskId, CollectionProgress progress);

    /**
     * 璁板綍閲囬泦浠诲姟瀹屾垚銆?
     */
    void recordComplete(String taskId, Map<String, Object> result);

    /**
     * 璁板綍閲囬泦浠诲姟閿欒銆?
     */
    void recordError(String taskId, String error, Throwable cause);

    /**
     * 鑾峰彇閲囬泦浠诲姟鎬ц兘鎸囨爣銆?
     */
    Map<String, Object> getPerformanceMetrics(String taskId);

    /**
     * 鑾峰彇閲囬泦浠诲姟鍘嗗彶璁板綍銆?
     */
    List<Map<String, Object>> getCollectionHistory(String taskId, int limit);

    /**
     * 鍙戦€侀噰闆嗗憡璀︺€?
     */
    void sendAlert(String taskId, String alertType, String message);
}


