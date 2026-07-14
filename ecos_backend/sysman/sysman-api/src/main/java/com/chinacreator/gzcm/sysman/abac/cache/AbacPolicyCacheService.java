package com.chinacreator.gzcm.sysman.abac.cache;

import java.util.List;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;

/**
 * ABAC 策略缓存服务抽象，后续可切换为 Redis 等实现。
 */
public interface AbacPolicyCacheService {

    List<AbacPolicy> getAllPolicies();

    void refreshAll(List<AbacPolicy> policies);

    void evictAll();
}


