package com.chinacreator.gzcm.runtime.core.cache;

/**
 * 缓存失效广播的投递口（§2.9.8④ 跨节点失效；#147AN）。
 *
 * <p><b>为什么只有这一个方法、且放在 {@code cache} 包而不是 outbox 包</b>：
 * 失效底座（{@link RuntimeCacheManager}）需要「写路径提交后把失效意图交出去」，
 * 而交出意图的实现（Outbox → Redis 发布）在 {@code cache.outbox} 子包里反过来又依赖底座。
 * 让底座只认这个接口，依赖方向就是单向的 {@code cache.outbox → cache}，
 * 不会出现包级环。</p>
 *
 * @author ECOS Runtime #147AN
 */
public interface CacheInvalidationNotifier {

    /**
     * 入队一次「某 env 下某域整域失效」的意图。
     *
     * @return 投递凭据（Outbox 行 id）；实现方若异步丢弃也须返回非 null 的空标识
     */
    String enqueueDomainInvalidation(String env, String domain, String reason);
}
