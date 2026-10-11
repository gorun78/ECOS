package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/**
 * 幂等键冲突（H-5）：同 {@code idempotencyKey} 但 {@code contentHash} 不一致 ⇒ 拒绝复用（HTTP 409）。
 *
 * <p>确定性铁律：同 key 同 hash 复用旧 run；同 key 异 hash 属内容漂移，禁止覆盖或静默复用。</p>
 */
public class ForecastRunConflictException extends ScenarioDomainException {

    public ForecastRunConflictException(String idempotencyKey, String expectedHash, String actualHash) {
        super(409, "IDEMPOTENCY_CONFLICT",
                "相同 idempotencyKey 的内容指纹不一致，拒绝复用: idempotencyKey=" + idempotencyKey);
        putDetail("idempotencyKey", idempotencyKey);
        putDetail("expectedContentHash", expectedHash);
        putDetail("actualContentHash", actualHash);
    }
}
