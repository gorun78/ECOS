package com.chinacreator.gzcm.runtime.core.task.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;

/**
 * 浠诲姟绠＄悊鏈嶅姟鎺ュ彛
 * 缁熶竴绠＄悊浠诲姟鐨勬彁浜ゃ€佽В鏋愩€佹墽琛屻€佺姸鎬佹煡璇㈢瓑
 * 
 * @author CDRC Runtime Team
 */
public interface ITaskManagementService {

    /**
     * 鎻愪氦浠诲姟
     * 
     * @param taskDescription 浠诲姟鎻忚堪
     * @return 浠诲姟ID
     * @throws TaskManagementException 鎻愪氦澶辫触鏃舵姏鍑哄紓甯?
     */
    String submitTask(TaskDescription taskDescription) throws TaskManagementException;

    /**
     * 瑙ｆ瀽浠诲姟
     * 
     * @param taskId 浠诲姟ID
     * @return 浠诲姟鎵ц璁″垝
     * @throws TaskManagementException 瑙ｆ瀽澶辫触鏃舵姏鍑哄紓甯?
     */
    TaskExecutionPlan parseTask(String taskId) throws TaskManagementException;

    /**
     * 鎵ц浠诲姟
     * 
     * @param taskId 浠诲姟ID
     * @return 浠诲姟鎵ц缁撴灉锛圝SON鏍煎紡锛夛紝濡傛灉鏄紓姝ヤ换鍔″垯杩斿洖null
     * @throws TaskManagementException 鎵ц澶辫触鏃舵姏鍑哄紓甯?
     */
    String executeTask(String taskId) throws TaskManagementException;

    /**
     * 鎻愪氦骞舵墽琛屼换鍔★紙涓€姝ュ畬鎴愶級
     * 
     * @param taskDescription 浠诲姟鎻忚堪
     * @return 浠诲姟鎵ц缁撴灉锛圝SON鏍煎紡锛夛紝濡傛灉鏄紓姝ヤ换鍔″垯杩斿洖null
     * @throws TaskManagementException 鎵ц澶辫触鏃舵姏鍑哄紓甯?
     */
    String submitAndExecute(TaskDescription taskDescription) throws TaskManagementException;

    /**
     * 鑾峰彇浠诲姟鐘舵€?
     * 
     * @param taskId 浠诲姟ID
     * @return 浠诲姟鐘舵€?
     * @throws TaskManagementException 鑾峰彇鐘舵€佸け璐ユ椂鎶涘嚭寮傚父
     */
    TaskStatus getTaskStatus(String taskId) throws TaskManagementException;

    /**
     * 鍙栨秷浠诲姟
     * 
     * @param taskId 浠诲姟ID
     * @throws TaskManagementException 鍙栨秷澶辫触鏃舵姏鍑哄紓甯?
     */
    void cancelTask(String taskId) throws TaskManagementException;

    /**
     * 鏆傚仠浠诲姟
     * 
     * @param taskId 浠诲姟ID
     * @throws TaskManagementException 鏆傚仠澶辫触鏃舵姏鍑哄紓甯?
     */
    void pauseTask(String taskId) throws TaskManagementException;

    /**
     * 鎭㈠浠诲姟
     * 
     * @param taskId 浠诲姟ID
     * @throws TaskManagementException 鎭㈠澶辫触鏃舵姏鍑哄紓甯?
     */
    void resumeTask(String taskId) throws TaskManagementException;

    /**
     * 鏌ヨ浠诲姟鍒楄〃
     * 
     * @param condition 鏌ヨ鏉′欢锛堝彲鍖呭惈taskType銆乻tatus銆乼enantId銆乧reatedBy绛夛級
     * @param offset 鍋忕Щ閲?
     * @param limit 闄愬埗鏁伴噺
     * @return 浠诲姟鎻忚堪鍒楄〃
     * @throws TaskManagementException 鏌ヨ澶辫触鏃舵姏鍑哄紓甯?
     */
    List<TaskDescription> queryTasks(Map<String, Object> condition, int offset, int limit) throws TaskManagementException;

    /**
     * 获取任务统计
     * 
     * @return Map包含 total/running/pending/succeeded/failed/cancelled 计数
     */
    Map<String, Object> getTaskStats();

    /**
     * 鑾峰彇浠诲姟鎻忚堪
     * 
     * @param taskId 浠诲姟ID
     * @return 浠诲姟鎻忚堪
     * @throws TaskManagementException 鑾峰彇澶辫触鏃舵姏鍑哄紓甯?
     */
    TaskDescription getTaskDescription(String taskId) throws TaskManagementException;

    /**
     * 鑾峰彇浠诲姟鎵ц璁″垝
     * 
     * @param taskId 浠诲姟ID
     * @return 浠诲姟鎵ц璁″垝
     * @throws TaskManagementException 鑾峰彇澶辫触鏃舵姏鍑哄紓甯?
     */
    TaskExecutionPlan getTaskExecutionPlan(String taskId) throws TaskManagementException;

    /**
     * 娉ㄥ唽浠诲姟瑙ｆ瀽鍣?
     * 
     * @param taskType 浠诲姟绫诲瀷
     * @param parser 浠诲姟瑙ｆ瀽鍣?
     */
    void registerParser(String taskType, com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser parser);

    /**
     * 娉ㄥ唽浠诲姟鎵ц鍣?
     * 
     * @param executorType 鎵ц鍣ㄧ被鍨?
     * @param executor 浠诲姟鎵ц鍣?
     */
    void registerExecutor(String executorType, com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor executor);

    /**
     * 浠诲姟绠＄悊寮傚父
     */
    class TaskManagementException extends Exception {
        private static final long serialVersionUID = 1L;

        public TaskManagementException(String message) {
            super(message);
        }

        public TaskManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

