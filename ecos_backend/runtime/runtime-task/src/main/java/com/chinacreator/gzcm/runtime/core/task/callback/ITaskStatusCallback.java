package com.chinacreator.gzcm.runtime.core.task.callback;

import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;

/**
 * 浠诲姟鐘舵€佸洖璋冩帴鍙?
 * 鐢ㄤ簬浠诲姟鎵ц杩囩▼涓弽棣堜换鍔＄姸鎬?
 * 
 * @author CDRC Runtime Team
 */
public interface ITaskStatusCallback {

    /**
     * 浠诲姟鐘舵€佹洿鏂板洖璋?
     * 
     * @param status 浠诲姟鐘舵€?
     */
    void onStatusUpdate(TaskStatus status);

    /**
     * 浠诲姟杩涘害鏇存柊鍥炶皟
     * 
     * @param taskId 浠诲姟ID
     * @param progress 杩涘害鐧惧垎姣旓紙0-100锛?
     * @param message 杩涘害娑堟伅
     */
    void onProgressUpdate(String taskId, Integer progress, String message);

    /**
     * 姝ラ寮€濮嬪洖璋?
     * 
     * @param taskId 浠诲姟ID
     * @param stepId 姝ラID
     * @param stepName 姝ラ鍚嶇О
     */
    void onStepStart(String taskId, String stepId, String stepName);

    /**
     * 姝ラ瀹屾垚鍥炶皟
     * 
     * @param taskId 浠诲姟ID
     * @param stepId 姝ラID
     * @param stepName 姝ラ鍚嶇О
     * @param success 鏄惁鎴愬姛
     * @param message 娑堟伅
     */
    void onStepComplete(String taskId, String stepId, String stepName, boolean success, String message);

    /**
     * 浠诲姟瀹屾垚鍥炶皟
     * 
     * @param taskId 浠诲姟ID
     * @param success 鏄惁鎴愬姛
     * @param result 鎵ц缁撴灉锛圝SON鏍煎紡锛?
     * @param errorMessage 閿欒淇℃伅锛堝鏋滃け璐ワ級
     */
    void onTaskComplete(String taskId, boolean success, String result, String errorMessage);

    /**
     * 浠诲姟閿欒鍥炶皟
     * 
     * @param taskId 浠诲姟ID
     * @param error 閿欒淇℃伅
     * @param stackTrace 閿欒鍫嗘爤
     */
    void onError(String taskId, String error, String stackTrace);
}

