package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.engine.ai.service.ErrorCodeMapper;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 控制器侧"发后即忘"任务体的分类执行器（详细设计-06 F06-15）。
 *
 * <p>存在理由：控制器类内禁写 {@code catch (Exception)}，但异步任务体（{@code runAsync}、
 * 完成回调）没有 {@code @RestControllerAdvice} 接管，异常若直接逃逸只会落进线程池默认
 * uncaught handler 而丢上下文。本类把这类任务体收在唯一咽喉：捕获后必经
 * {@link ErrorCodeMapper} 分类、按 D.5 级别留痕，绝不静默、绝不返回语义成功值掩盖失败。</p>
 *
 * <p>注：本类不是 Controller（无 Spring stereotype 注解），故不在
 * {@code AiExceptionHygieneTest#controllersDeclareNoCatchAll} 的禁捕获面内。</p>
 */
public final class AiAsyncSupport {

    private static final Logger log = LoggerFactory.getLogger(AiAsyncSupport.class);

    /** 可抛 checked 异常的任务体。 */
    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    private AiAsyncSupport() {
    }

    /**
     * 执行任务体，失败只分类留痕不外抛 —— 用于无调用方接管的异步面。
     *
     * @param task 任务名（日志定位用，禁含用户数据）
     * @param body 任务体
     */
    public static void runQuietly(String task, Body body) {
        try {
            body.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ai-async] 任务被中断: task={}", task);
        } catch (Exception e) {
            AiError error = ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(),
                    AiTraceSupport.currentTraceId());
            log.error("[ai-async] 后台任务失败: task={} class={} code={} traceId={} raw={}",
                    task, error.errorClass().name(), error.errorCode(), error.traceId(),
                    error.rawReason(), e);
        }
    }
}
