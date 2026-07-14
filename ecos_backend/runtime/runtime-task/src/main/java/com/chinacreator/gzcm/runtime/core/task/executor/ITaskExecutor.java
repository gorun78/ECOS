package com.chinacreator.gzcm.runtime.core.task.executor;

import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;

/**
 * 浠诲姟鎵ц鍣ㄦ帴鍙?
 * 璐熻矗鎵ц浠诲姟璁″垝锛圱askExecutionPlan锛?
 * 
 * @author CDRC Runtime Team
 */
public interface ITaskExecutor {

    /**
     * 鎵ц浠诲姟璁″垝
     * 
     * @param executionPlan 浠诲姟鎵ц璁″垝
     * @param statusCallback 鐘舵€佸洖璋冩帴鍙ｏ紝鐢ㄤ簬鍙嶉浠诲姟鎵ц鐘舵€?
     * @return 浠诲姟鎵ц缁撴灉锛圝SON鏍煎紡锛?
     * @throws TaskExecutionException 鎵ц澶辫触鏃舵姏鍑哄紓甯?
     */
    String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback) throws TaskExecutionException;

    /**
     * 鍙栨秷浠诲姟鎵ц
     * 
     * @param taskId 浠诲姟ID
     * @throws TaskExecutionException 鍙栨秷澶辫触鏃舵姏鍑哄紓甯?
     */
    void cancel(String taskId) throws TaskExecutionException;

    /**
     * 鏆傚仠浠诲姟鎵ц
     * 
     * @param taskId 浠诲姟ID
     * @throws TaskExecutionException 鏆傚仠澶辫触鏃舵姏鍑哄紓甯?
     */
    void pause(String taskId) throws TaskExecutionException;

    /**
     * 鎭㈠浠诲姟鎵ц
     * 
     * @param taskId 浠诲姟ID
     * @throws TaskExecutionException 鎭㈠澶辫触鏃舵姏鍑哄紓甯?
     */
    void resume(String taskId) throws TaskExecutionException;

    /**
     * 鑾峰彇浠诲姟鐘舵€?
     * 
     * @param taskId 浠诲姟ID
     * @return 浠诲姟鐘舵€?
     * @throws TaskExecutionException 鑾峰彇鐘舵€佸け璐ユ椂鎶涘嚭寮傚父
     */
    TaskStatus getStatus(String taskId) throws TaskExecutionException;

    /**
     * 浠诲姟鎵ц寮傚父
     */
    class TaskExecutionException extends Exception {
        private static final long serialVersionUID = 1L;

        public TaskExecutionException(String message) {
            super(message);
        }

        public TaskExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

