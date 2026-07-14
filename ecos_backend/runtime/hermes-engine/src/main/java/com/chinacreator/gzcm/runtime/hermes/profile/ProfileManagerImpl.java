package com.chinacreator.gzcm.runtime.hermes.profile;

import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.hermes.repository.ProfileConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProfileManager 实现 — 两级缓存（L1: subsystem → Map&lt;name, ProfileConfig&gt;）
 */
@Service
public class ProfileManagerImpl implements ProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ProfileManagerImpl.class);

    /** L1 缓存: subsystem → (profileName → ProfileConfig) */
    private final ConcurrentHashMap<String, Map<String, ProfileConfig>> l1Cache = new ConcurrentHashMap<>();

    @Autowired
    private ProfileConfigRepository repository;

    @Override
    public ProfileConfig getProfile(String subsystem, String profileName) {
        if (!StringUtils.hasText(subsystem)) {
            throw new IllegalArgumentException("subsystem must not be empty");
        }
        if (!StringUtils.hasText(profileName)) {
            throw new IllegalArgumentException("profileName must not be empty");
        }

        // 从 L1 缓存取
        Map<String, ProfileConfig> subMap = l1Cache.get(subsystem);
        if (subMap != null) {
            ProfileConfig cached = subMap.get(profileName);
            if (cached != null) {
                if (Boolean.FALSE.equals(cached.getEnabled())) {
                    throw new IllegalArgumentException(
                            "Profile [" + profileName + "] is disabled for subsystem [" + subsystem + "]");
                }
                return cached;
            }
        }

        // 缓存 miss → 查 DB 并回填
        ProfileConfig config = repository.findBySubsystemAndProfileName(subsystem, profileName);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Profile [" + profileName + "] not found for subsystem [" + subsystem + "]");
        }

        // 回填 L1 缓存
        l1Cache.computeIfAbsent(subsystem, k -> new ConcurrentHashMap<>())
                .put(profileName, config);

        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new IllegalArgumentException(
                    "Profile [" + profileName + "] is disabled for subsystem [" + subsystem + "]");
        }

        return config;
    }

    @Override
    public List<ProfileConfig> listProfiles(String subsystem) {
        if (!StringUtils.hasText(subsystem)) {
            return Collections.emptyList();
        }

        // 优先从缓存取
        Map<String, ProfileConfig> subMap = l1Cache.get(subsystem);
        if (subMap != null) {
            return new ArrayList<>(subMap.values());
        }

        // 查 DB
        List<ProfileConfig> profiles = repository.findBySubsystem(subsystem);
        if (profiles != null && !profiles.isEmpty()) {
            Map<String, ProfileConfig> cache = new ConcurrentHashMap<>();
            for (ProfileConfig p : profiles) {
                cache.put(p.getProfileName(), p);
            }
            l1Cache.put(subsystem, cache);
        }
        return profiles != null ? profiles : Collections.emptyList();
    }

    @Override
    public List<ProfileConfig> listAllProfiles() {
        List<ProfileConfig> all = repository.findAll();
        return all != null ? all : Collections.emptyList();
    }

    @Override
    public void validateProfile(ProfileConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ProfileConfig must not be null");
        }
        if (!StringUtils.hasText(config.getProfileName())) {
            throw new IllegalArgumentException("profileName is required");
        }
        if (!StringUtils.hasText(config.getSubsystem())) {
            throw new IllegalArgumentException("subsystem is required");
        }
        if (!StringUtils.hasText(config.getProvider())) {
            throw new IllegalArgumentException("provider is required");
        }
        if (!StringUtils.hasText(config.getModel())) {
            throw new IllegalArgumentException("model is required");
        }
        log.debug("Profile [{}] for subsystem [{}] validated OK",
                config.getProfileName(), config.getSubsystem());
    }

    @Override
    public void refreshCache(String subsystem) {
        if (subsystem == null) {
            l1Cache.clear();
            log.info("All profile caches cleared");
        } else {
            l1Cache.remove(subsystem);
            log.info("Profile cache cleared for subsystem [{}]", subsystem);
        }
    }
}
