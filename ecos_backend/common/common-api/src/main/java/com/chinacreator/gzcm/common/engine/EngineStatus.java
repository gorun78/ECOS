package com.chinacreator.gzcm.common.engine;

/**
 * 引擎运行状态枚举。
 *
 * @author ECOS PMO
 * @since 1.0.0
 */
public enum EngineStatus {

    /** 正常运行，所有功能可用 */
    RUNNING,

    /** 降级运行，部分功能不可用 */
    DEGRADED,

    /** 已停止 */
    STOPPED
}
