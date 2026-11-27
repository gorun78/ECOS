package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.engine.ai.service.ErrorCodeMapper;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设计资产归档的「尽力而为」执行器（详细设计-06 F06-15 控制器异常卫生）。
 *
 * <p><b>存在理由</b>：模型注册的落库是主事务，其后的 Git 版本归档是<b>副作用</b>——
 * 归档失败不得回滚已成功的落库（表只承载在用版本，历史版本走 Git，铁律 §2.5），
 * 也不得让异常逃逸出控制器（{@code AiExceptionHygieneTest} 禁控制器内
 * {@code catch (Exception)}，唯一许可面是 {@code AiErrorAdvice}）。
 * 但归档器自身会抛（JSON 序列化 / Git IO / 仓库未注册），若任其上浮，
 * {@code AiErrorAdvice} 会把「落库已成功」渲染成 5xx，调用方按失败重试就会重复注册一版
 * ⇒ 把一个已成功的数据写入变成用户可见的失败。</p>
 *
 * <p><b>为什么不是 Controller</b>：本类无 Spring stereotype 注解，故不在
 * {@code AiExceptionHygieneTest#controllersDeclareNoCatchAll} 的禁捕获面内 ——
 * 与同包 {@link AiAsyncSupport} 同一处置形态。</p>
 *
 * <p><b>原文纪律（X-49）</b>：异常原文只进日志与 {@link AiError#rawReason()}；
 * 返回给调用方的 note 只表达「哪一类失败 + 诊断码」，不带 SQL/连接串/文件路径片段，
 * 以免经响应体外泄。</p>
 */
public final class DesignAssetArchiveSupport {

    private static final Logger log = LoggerFactory.getLogger(DesignAssetArchiveSupport.class);

    private DesignAssetArchiveSupport() {
    }

    /**
     * 归档结果：{@code gitRef} 与 {@code note} 互斥 —— 成功时 note 为 null，
     * 失败时 gitRef 为 null 且 note 必非空（原因自证，禁 null/null 双空）。
     *
     * @param gitRef 归档提交 id（成功时非空）
     * @param note   未归档原因（成功时为 null）
     */
    public record Outcome(String gitRef, String note) {
    }

    /** 归档动作（由调用方提供，便于单测注入失败面）。 */
    @FunctionalInterface
    public interface ArchiveCall {
        /**
         * @return 归档结果（可为 null，按「归档器返回空」处理）；
         *         {@code commitId()} 为空白亦按失败处理
         */
        Outcome call() throws Exception;
    }

    /**
     * 执行归档，异常不外抛。
     *
     * @param label   日志定位标签（禁含用户数据；形如 {@code models/<modelKey>}）
     * @param call    归档动作；null 表示归档器未装配
     * @param onAbsent 归档器未装配时的原因文案
     * @return 永不返回 null；失败时 {@code note} 非空
     */
    public static Outcome tryArchive(String label, ArchiveCall call, String onAbsent) {
        if (call == null) {
            return new Outcome(null, onAbsent);
        }
        try {
            Outcome result = call.call();
            if (result == null) {
                log.warn("[design-archive] 归档器返回空（落库已成功）: label={}", label);
                return new Outcome(null, "归档器返回空");
            }
            if (result.gitRef() != null && !result.gitRef().isBlank()) {
                return new Outcome(result.gitRef(), null);
            }
            log.warn("[design-archive] 归档未完成（落库已成功）: label={} note={}", label, result.note());
            return new Outcome(null, result.note() == null || result.note().isBlank()
                    ? "归档器未给出原因" : result.note());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[design-archive] 归档被中断（落库已成功）: label={}", label);
            return new Outcome(null, "归档被中断（落库已成功，可稍后补归档）");
        } catch (Exception e) {
            // 原文只进日志（X-49）；响应体侧只给分类 + 诊断码
            AiError error = ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(),
                    AiTraceSupport.currentTraceId());
            log.warn("[design-archive] 归档异常（落库已成功）: label={} class={} code={} traceId={} raw={}",
                    label, error.errorClass().name(), error.errorCode(), error.traceId(),
                    error.rawReason(), e);
            return new Outcome(null, "归档异常（" + error.errorClass().name()
                    + "，落库已成功；诊断码 " + (error.traceId() == null ? "-" : error.traceId()) + "）");
        }
    }

    /** 归档器未装配的原因单源（响应键 {@code gitRefUnavailableReason} 的取值）。 */
    public static String absentReason(String component) {
        return component + " 未装配";
    }
}
