package com.chinacreator.gzcm.cli;

/** 面向用户的失败（带退出码）；消息由调用方保证已脱敏。 */
public class CliException extends RuntimeException {
    private final int exitCode;

    public CliException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
