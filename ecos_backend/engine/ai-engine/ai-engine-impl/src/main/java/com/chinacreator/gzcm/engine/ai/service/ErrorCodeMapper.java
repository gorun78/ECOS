package com.chinacreator.gzcm.engine.ai.service;

import java.util.Map;

/**
 * 错误码友好文案映射 — 将 Java 异常类名转为用户可读的中文提示。
 */
public class ErrorCodeMapper {

    private static final Map<String, String> FRIENDLY_MAP = Map.ofEntries(
        Map.entry("NullPointerException", "系统忙，请稍后重试"),
        Map.entry("SocketTimeoutException", "AI服务响应超时，请稍后重试或简化您的问题"),
        Map.entry("HttpTimeoutException", "AI服务响应超时，请稍后重试或简化您的问题"),
        Map.entry("ConnectException", "AI服务暂时不可用，请稍后重试"),
        Map.entry("IOException", "网络异常，请检查网络连接后重试"),
        Map.entry("HttpHostConnectException", "AI服务暂时不可用，请稍后重试"),
        Map.entry("UnknownHostException", "AI服务域名解析失败，请检查网络配置"),
        Map.entry("SSLException", "安全连接失败，请稍后重试"),
        Map.entry("TimeoutException", "操作超时，请稍后重试"),
        Map.entry("IllegalStateException", "系统状态异常，请刷新后重试"),
        Map.entry("RuntimeException", "系统处理异常，请稍后重试"),
        Map.entry("Exception", "系统处理异常，请稍后重试"),
        Map.entry("HttpClientErrorException", "请求参数错误，请检查输入"),
        Map.entry("HttpServerErrorException", "AI服务内部错误，请稍后重试")
    );

    /**
     * 将异常简单名转为友好文案。
     *
     * @param exception 原始异常
     * @return 友好文案；未匹配时返回原始消息
     */
    public static String toFriendly(Exception exception) {
        if (exception == null) {
            return "未知错误";
        }
        String simpleName = exception.getClass().getSimpleName();
        // 遍历 cause chain 查找已知异常
        Throwable t = exception;
        while (t != null) {
            String name = t.getClass().getSimpleName();
            String friendly = FRIENDLY_MAP.get(name);
            if (friendly != null) {
                return friendly;
            }
            t = t.getCause();
        }
        // 未匹配 → 返回异常简名 + 消息
        String msg = exception.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return "系统处理异常: " + simpleName;
    }
}
