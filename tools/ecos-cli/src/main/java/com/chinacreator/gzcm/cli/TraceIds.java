package com.chinacreator.gzcm.cli;

import java.util.UUID;

/** 全链 trace ID 生成（--trace-id 未指定时使用）。 */
public final class TraceIds {
    public static String next() {
        return "cli-" + UUID.randomUUID();
    }

    private TraceIds() {
    }
}
