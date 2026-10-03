package com.chinacreator.gzcm.engine.security.service.decision;

/**
 * 详细设计-01 C.1 [3] — 三通道用途（页面/导出/AI 工具）。
 *
 * <p>不同通道降级口径不同（C.6 矩阵），decide 按 purpose 打标
 * 供调用方执行对应强制点（导出 = mask 裁决，AI = FAIL_CLOSED 护栏语义）。</p>
 */
public enum Purpose {
    page,
    export,
    ai
}
