package com.chinacreator.gzcm.runtime.core.task.parser;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;

/**
 * 浠诲姟瑙ｆ瀽鍣ㄦ帴鍙?
 * 璐熻矗灏嗕换鍔℃弿杩帮紙TaskDescription锛夎В鏋愪负鍙墽琛岀殑浠诲姟璁″垝锛圱askExecutionPlan锛?
 * 
 * @author CDRC Runtime Team
 */
public interface ITaskParser {

    /**
     * 瑙ｆ瀽浠诲姟鎻忚堪锛岀敓鎴愭墽琛岃鍒?
     * 
     * @param taskDescription 浠诲姟鎻忚堪
     * @return 浠诲姟鎵ц璁″垝
     * @throws TaskParseException 瑙ｆ瀽澶辫触鏃舵姏鍑哄紓甯?
     */
    TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException;

    /**
     * 妫€鏌ユ槸鍚︽敮鎸佹寚瀹氱殑浠诲姟绫诲瀷
     * 
     * @param taskType 浠诲姟绫诲瀷
     * @return 鏄惁鏀寔
     */
    boolean supports(String taskType);

    /**
     * 楠岃瘉浠诲姟鎻忚堪鏄惁鏈夋晥
     * 
     * @param taskDescription 浠诲姟鎻忚堪
     * @throws TaskParseException 楠岃瘉澶辫触鏃舵姏鍑哄紓甯?
     */
    void validate(TaskDescription taskDescription) throws TaskParseException;

    /**
     * 浠诲姟瑙ｆ瀽寮傚父
     */
    class TaskParseException extends Exception {
        private static final long serialVersionUID = 1L;

        public TaskParseException(String message) {
            super(message);
        }

        public TaskParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

