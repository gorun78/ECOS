package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.MetricDefinition;
import com.chinacreator.gzcm.engine.ontology.repository.MetricDefinitionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 指标定义领域服务（F03-01 规则 2/4、F03-09）。
 *
 * <p>规则（不可协商）：
 * <ul>
 *   <li>口径升版**不自动**传播到指标：指标须经 {@link #bindMetricCaliber} 显式绑定新 formula_version；</li>
 *   <li>{@code DRAFT} 指标可无口径；{@code PUBLISHED} 指标无口径 = 发布失败（400 {@code ECOS-ONTO-040}）；</li>
 *   <li>{@code caliber_snapshot} 在指标发布时刻一次性写入，之后只读（命中该列的更新 → 409 {@code ECOS-ONTO-041}）。</li>
 * </ul>
 */
@Service
public class MetricDefinitionService {

    public static final String CODE_BIND = "ECOS-ONTO-040";
    public static final String CODE_SNAPSHOT = "ECOS-ONTO-041";

    private static final Logger log = LoggerFactory.getLogger(MetricDefinitionService.class);

    private final MetricDefinitionRepository repo;
    private final CaliberService caliberService;

    public MetricDefinitionService(MetricDefinitionRepository repo, CaliberService caliberService) {
        this.repo = repo;
        this.caliberService = caliberService;
    }

    public List<MetricDefinition> listAll() {
        return repo.findAll();
    }

    public Optional<MetricDefinition> findByCode(String code) {
        return repo.findByCode(code);
    }

    @Transactional
    public MetricDefinition create(MetricDefinition m, String operator) {
        if (m.getCode() != null && repo.findByCode(m.getCode()).isPresent()) {
            throw new MetricException(CODE_SNAPSHOT, 409, "指标编码重复: " + m.getCode());
        }
        if (m.getId() == null || m.getId().isBlank()) m.setId(UUID.randomUUID().toString());
        if (m.getStatus() == null) m.setStatus("DRAFT");
        LocalDateTime now = LocalDateTime.now();
        m.setCreateTime(now);
        m.setUpdateTime(now);
        if (m.getCreateBy() == null) m.setCreateBy(operator);
        m.setUpdateBy(operator);
        enforcePublishedBinding(m);
        repo.insert(m);
        log.info("metric created code={} status={} by={}", m.getCode(), m.getStatus(), operator);
        return m;
    }

    /**
     * 显式绑定口径版本（§2.2-2 升版不自动传播的入口）。
     * 若目标状态为 PUBLISHED，须校验口径版本已 APPROVED，否则 400 ECOS-ONTO-040。
     */
    @Transactional
    public MetricDefinition bindMetricCaliber(String metricCode, String caliberId, String formulaVersion,
                                              String targetStatus, String operator) {
        MetricDefinition m = repo.findByCode(metricCode)
                .orElseThrow(() -> new MetricException("ECOS-ONTO-080", 404, "指标不存在: " + metricCode));
        if (caliberId == null || caliberId.isBlank()) {
            throw new MetricException(CODE_BIND, 400, "绑定指标必须指定 caliber_id");
        }
        String status = targetStatus != null ? targetStatus : m.getStatus();
        if ("PUBLISHED".equals(status) && !caliberService.isVersionApproved(caliberId, formulaVersion)) {
            throw new MetricException(CODE_BIND, 400,
                    "指标 " + metricCode + " 发布态所绑口径版本 " + formulaVersion + " 非 APPROVED（V1 门禁）");
        }
        // 快照冻结：PUBLISHED 且尚无快照时，一次性写入当前口径公式文本，之后只读
        String snapshot = m.getCaliberSnapshot();
        if ("PUBLISHED".equals(status) && (snapshot == null || snapshot.isBlank())) {
            snapshot = currentFormulaText(caliberId, formulaVersion);
        }
        if (snapshot != null && !snapshot.isBlank() && !snapshot.equals(m.getCaliberSnapshot())) {
            // 已冻结后又试图改写快照 → 409 ECOS-ONTO-041
            if (m.getCaliberSnapshot() != null && !m.getCaliberSnapshot().isBlank()
                    && !"PUBLISHED".equals(m.getStatus())) {
                throw new MetricException(CODE_SNAPSHOT, 409, "指标口径快照已冻结，禁止改写");
            }
        }
        repo.bindCaliber(m.getId(), caliberId, formulaVersion, snapshot, status, operator);
        log.info("metric bound code={} caliber={} v={} status={} by={}",
                metricCode, caliberId, formulaVersion, status, operator);
        m.setCaliberId(caliberId);
        m.setFormulaVersion(formulaVersion);
        if (snapshot != null) m.setCaliberSnapshot(snapshot);
        m.setStatus(status);
        return m;
    }

    private String currentFormulaText(String caliberId, String versionNo) {
        // 只读消费口径主权源（R-6①）：取该版本的公式明文作为冻结快照
        return caliberService.findVersionFormula(caliberId, versionNo);
    }

    private void enforcePublishedBinding(MetricDefinition m) {
        if ("PUBLISHED".equals(m.getStatus()) && (m.getCaliberId() == null || m.getCaliberId().isBlank())) {
            throw new MetricException(CODE_BIND, 400,
                    "发布态指标必须绑定口径（caliber_id 必填），DRAFT 除外");
        }
    }

    public static class MetricException extends RuntimeException {
        public final int httpStatus;
        public final String code;
        public MetricException(String code, int httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }
    }
}
