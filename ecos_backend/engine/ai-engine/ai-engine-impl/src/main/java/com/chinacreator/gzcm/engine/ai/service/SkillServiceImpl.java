package com.chinacreator.gzcm.engine.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ai.SkillService;
import com.chinacreator.gzcm.engine.ai.entity.SkillEntity;
import com.chinacreator.gzcm.engine.ai.repository.SkillRepository;

@Service
public class SkillServiceImpl implements SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);

    private final SkillRepository skillRepository;

    public SkillServiceImpl(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Override
    public List<SkillEntity> listSkills(String category, Boolean enabled) {
        return skillRepository.findAll(category, enabled);
    }

    @Override
    public Optional<SkillEntity> getSkill(Long id) {
        return skillRepository.findById(id);
    }

    @Override
    public SkillEntity createSkill(Map<String, Object> body) {
        SkillEntity entity = new SkillEntity();
        entity.setName(getString(body, "name"));
        entity.setDescription(getString(body, "description"));
        entity.setVersion(getString(body, "version", "1.0.0"));
        entity.setEnabled(body.containsKey("enabled") ? getBoolean(body, "enabled") : true);
        entity.setCategory(getString(body, "category"));
        entity.setPackageInfo(getString(body, "packageInfo"));
        entity.setCreatedBy(getString(body, "createdBy"));

        skillRepository.insert(entity);
        log.info("Skill created: id={} name={} version={}", entity.getId(), entity.getName(), entity.getVersion());
        return entity;
    }

    @Override
    public Optional<SkillEntity> updateSkill(Long id, Map<String, Object> body) {
        Optional<SkillEntity> existing = skillRepository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        SkillEntity entity = existing.get();
        if (body.containsKey("name")) entity.setName(getString(body, "name"));
        if (body.containsKey("description")) entity.setDescription(getString(body, "description"));
        if (body.containsKey("version")) entity.setVersion(getString(body, "version"));
        if (body.containsKey("enabled")) entity.setEnabled(getBoolean(body, "enabled"));
        if (body.containsKey("category")) entity.setCategory(getString(body, "category"));
        if (body.containsKey("packageInfo")) entity.setPackageInfo(getString(body, "packageInfo"));
        if (body.containsKey("createdBy")) entity.setCreatedBy(getString(body, "createdBy"));

        skillRepository.update(entity);
        log.info("Skill updated: id={}", id);
        return Optional.of(entity);
    }

    @Override
    public boolean deleteSkill(Long id) {
        int affected = skillRepository.deleteById(id);
        if (affected > 0) {
            log.info("Skill deleted: id={}", id);
            return true;
        }
        return false;
    }

    @Override
    public Optional<SkillEntity> toggleSkill(Long id, boolean enabled) {
        Optional<SkillEntity> existing = skillRepository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        skillRepository.updateEnabled(id, enabled);
        SkillEntity entity = existing.get();
        entity.setEnabled(enabled);
        log.info("Skill toggled: id={} enabled={}", id, enabled);
        return Optional.of(entity);
    }

    @Override
    public List<SkillEntity> listVersions(String name) {
        return skillRepository.findByName(name);
    }

    @Override
    public long totalCount() {
        return skillRepository.count();
    }

    // ── 工具方法 ──

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }

    private String getString(Map<String, Object> body, String key, String defaultValue) {
        Object val = body.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private Boolean getBoolean(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val instanceof Boolean b) return b;
        if (val != null) return Boolean.valueOf(val.toString());
        return false;
    }
}
