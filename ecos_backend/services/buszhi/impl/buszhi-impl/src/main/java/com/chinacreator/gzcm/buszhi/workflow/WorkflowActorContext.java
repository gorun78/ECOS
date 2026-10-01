package com.chinacreator.gzcm.buszhi.workflow;

/**
 * 工作流域当前操作人上下文 — W86 软删 update_by 取值。
 *
 * <p>buszhi-impl 对 {@code sysman-api} (com.chinacreator.gzcm.sysman.iam.context.UserContext)
 * **无编译期依赖**（W88/M1 已将 buszhi 收口到 "sysman REST 化"，避免 bean/依赖冲突）。
 * 为拿到软删审计列 {@code update_by}，本类提供包内 ThreadLocal 持有点：
 * <ul>
 *   <li>入口 Controller / 鉴权拦截器登录落地后调用 {@link #setActor(String)}；</li>
 *   <li>请求结束在 finally 调 {@link #clear()}；</li>
 *   <li>未设置时 {@link #currentActor()} 回退 {@code "system"}，保证 update_by 非空。</li>
 * </ul>
 * 不新建 Maven 模块 / 三方依赖（架构铁律：Driver/底座收敛，buszhi 不直连 sysman）。
 */
public final class WorkflowActorContext {

    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();
    private static final String FALLBACK = "system";

    private WorkflowActorContext() {}

    public static void setActor(String actor) {
        ACTOR.set(actor);
    }

    /** 当前操作人，未设置返回 {@code "system"}。 */
    public static String currentActor() {
        String a = ACTOR.get();
        return (a == null || a.isBlank()) ? FALLBACK : a;
    }

    public static void clear() {
        ACTOR.remove();
    }
}
