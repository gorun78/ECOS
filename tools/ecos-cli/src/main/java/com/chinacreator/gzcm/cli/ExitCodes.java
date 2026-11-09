package com.chinacreator.gzcm.cli;

/** 退出码（README 与落地登记册为唯一口径）。 */
public final class ExitCodes {
    public static final int SUCCESS = 0;
    public static final int FAILED = 1;
    public static final int BLOCKED = 2;
    public static final int UNAVAILABLE = 3;
    public static final int PARTIAL = 4;
    public static final int USAGE = 64;

    public static int forStatus(CliStatus status) {
        return switch (status) {
            case SUCCESS -> SUCCESS;
            case FAILED -> FAILED;
            case BLOCKED -> BLOCKED;
            case UNAVAILABLE -> UNAVAILABLE;
            case PARTIAL -> PARTIAL;
        };
    }

    private ExitCodes() {
    }
}
