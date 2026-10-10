package com.chinacreator.gzcm.engine.cognitive2.service.context;

/**
 * 权威源读取失败（依赖不可达 / 超时 / 源侧 5xx，§5.5「引擎未启用/服务不可达」）。
 *
 * <p>读取失败必须抛本异常，<b>禁止以空结果伪装"没有数据"</b>（附录 B：
 * 模型/引擎返回空内容 ≠ 没有数据；空集合、未启用、无权限、依赖故障必须区分）。
 * ContextBuilderService 据 {@link ContextSourceRef#isRequired()} 决定：
 * 必需源 ⇒ 503 E503_DEPENDENCY_MISSING；可选源 ⇒ 部分上下文 + warnings + ready 判定。</p>
 */
public class ContextReadException extends RuntimeException {

    /** 来源类型（DATA_ASSET / KNOWLEDGE / ...）。 */
    public final String sourceType;
    /** 来源稳定 ID。 */
    public final String sourceId;

    public ContextReadException(String sourceType, String sourceId, String message) {
        super(message);
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }
}
