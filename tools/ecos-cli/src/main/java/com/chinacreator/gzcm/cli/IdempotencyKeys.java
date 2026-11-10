package com.chinacreator.gzcm.cli;

import java.util.UUID;

/**
 * 幂等键（详设 §16.3 {@code idempotency_policy}）。
 *
 * <p>写命令未显式传 {@code --idempotency-key} 时由本类生成，并在输出 {@code data.idempotencyKey}
 * 回显——调用方据此重放同一请求，服务端据此去重。生成规则必须稳定可读，便于日志对账。</p>
 */
public final class IdempotencyKeys {

    private static final int MAX_LENGTH = 128;

    private IdempotencyKeys() {
    }

    /** 解析用户传入值；非法字符（空格/换行）直接 USAGE，避免头部注入。 */
    public static String resolve(String explicit) {
        if (explicit == null || explicit.isBlank()) {
            return generate();
        }
        String v = explicit.trim();
        if (v.length() > MAX_LENGTH) {
            throw new CliException(ExitCodes.USAGE, "--idempotency-key 长度不得超过 " + MAX_LENGTH);
        }
        if (!v.matches("[A-Za-z0-9._:-]+")) {
            throw new CliException(ExitCodes.USAGE,
                    "--idempotency-key 只允许字母数字与 . _ : -（禁空白与控制字符）");
        }
        return v;
    }

    public static String generate() {
        return "cli-" + UUID.randomUUID();
    }
}
