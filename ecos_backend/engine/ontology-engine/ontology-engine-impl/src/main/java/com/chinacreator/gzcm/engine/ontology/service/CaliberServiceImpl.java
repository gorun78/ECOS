package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.Caliber;
import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;
import com.chinacreator.gzcm.engine.ontology.repository.CaliberRepository;
import com.chinacreator.gzcm.engine.ontology.repository.CaliberVersionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 口径领域服务实现（F03-01）。
 *
 * <p>状态机与行不可变：
 * <ul>
 *   <li>版本状态 DRAFT → REVIEWING → APPROVED → RETIRED，只允许按序前进；</li>
 *   <li>版本内容列（formula/additive/period）一经写入不可变 —— 本实现不提供内容列 UPDATE 路径，
 *       仅 {@code updateStatus} 允许改状态/审批人；</li>
 *   <li>重复 {@code (caliber_id, version_no)} 唯一索引 → 409 {@code ECOS-ONTO-041}。</li>
 * </ul>
 */
@Service
public class CaliberServiceImpl implements CaliberService {

    private static final Logger log = LoggerFactory.getLogger(CaliberServiceImpl.class);

    private static final Set<String> TRANSITIONS = Set.of(
            "DRAFT->REVIEWING", "REVIEWING->APPROVED", "APPROVED->RETIRED", "REVIEWING->RETIRED", "DRAFT->RETIRED");

    public static final String CODE_CONFLICT = "ECOS-ONTO-041";
    public static final String CODE_BIND = "ECOS-ONTO-040";
    public static final String CODE_UNAVAILABLE = "ECOS-ONTO-042";

    private final CaliberRepository caliberRepo;
    private final CaliberVersionRepository versionRepo;

    public CaliberServiceImpl(CaliberRepository caliberRepo, CaliberVersionRepository versionRepo) {
        this.caliberRepo = caliberRepo;
        this.versionRepo = versionRepo;
    }

    @Override
    public List<Caliber> listCalibers() {
        return caliberRepo.findAll();
    }

    @Override
    public Optional<Caliber> findByCode(String code) {
        return caliberRepo.findByCode(code);
    }

    @Override
    public Optional<CaliberVersion> findCurrentApprovedVersion(String caliberCode) {
        Caliber c = caliberRepo.findByCode(caliberCode).orElse(null);
        if (c == null) return Optional.empty();
        List<CaliberVersion> approved = versionRepo.findApprovedByCaliberId(c.getId());
        return approved.isEmpty() ? Optional.empty() : Optional.of(approved.get(0));
    }

    @Override
    public boolean isVersionApproved(String caliberId, String versionNo) {
        if (caliberId == null || versionNo == null) return false;
        return versionRepo.findByCaliberIdAndVersion(caliberId, versionNo)
                .map(v -> "APPROVED".equals(v.getStatus()))
                .orElse(false);
    }

    @Override
    public String findVersionFormula(String caliberId, String versionNo) {
        if (caliberId == null || versionNo == null) return null;
        return versionRepo.findByCaliberIdAndVersion(caliberId, versionNo)
                .map(com.chinacreator.gzcm.engine.ontology.model.CaliberVersion::getFormula)
                .orElse(null);
    }

    @Override
    public Caliber createCaliber(Caliber caliber, String operator) {
        if (caliber.getCode() != null && !caliberRepo.findByCode(caliber.getCode()).isEmpty()) {
            throw new BusinessException(CODE_CONFLICT, 409, "口径编码重复: " + caliber.getCode());
        }
        if (caliber.getId() == null || caliber.getId().isBlank()) {
            caliber.setId(UUID.randomUUID().toString());
        }
        if (caliber.getStatus() == null) caliber.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        caliber.setCreateTime(now);
        caliber.setUpdateTime(now);
        if (caliber.getCreateBy() == null) caliber.setCreateBy(operator);
        caliber.setUpdateBy(operator);
        caliberRepo.insert(caliber);
        log.info("caliber created code={} by={}", caliber.getCode(), operator);
        return caliber;
    }

    @Override
    public CaliberVersion publishCaliberVersion(CaliberVersion version, String operator) {
        String caliberId = version.getCaliberId();
        if (caliberId == null || caliberId.isBlank()) {
            throw new BusinessException(CODE_BIND, 400, "口径不存在或未指定 caliberId，无法升版");
        }
        if (versionRepo.findByCaliberIdAndVersion(caliberId, version.getVersionNo()).isPresent()) {
            throw new BusinessException(CODE_CONFLICT, 409,
                    "口径版本 " + version.getVersionNo() + " 已存在（升级而非覆盖）");
        }
        if (version.getId() == null || version.getId().isBlank()) {
            version.setId(UUID.randomUUID().toString());
        }
        if (version.getStatus() == null) version.setStatus("DRAFT");
        LocalDateTime now = LocalDateTime.now();
        version.setCreateTime(now);
        version.setUpdateTime(now);
        if (version.getCreateBy() == null) version.setCreateBy(operator);
        version.setUpdateBy(operator);
        if ("APPROVED".equals(version.getStatus()) && version.getApprovedAt() == null) {
            version.setApprovedAt(now);
        }
        versionRepo.insert(version);
        log.info("caliber version created caliberId={} v={} status={} by={}",
                caliberId, version.getVersionNo(), version.getStatus(), operator);
        return version;
    }

    @Override
    public CaliberVersion transition(String versionId, String targetStatus, String operator) {
        CaliberVersion v = versionRepo.findById(versionId)
                .orElseThrow(() -> new BusinessException("ECOS-ONTO-080", 404, "口径版本不存在: " + versionId));
        String key = v.getStatus() + "->" + targetStatus;
        if (!TRANSITIONS.contains(key)) {
            throw new BusinessException(CODE_CONFLICT, 409,
                    "非法口径版本状态迁移 " + v.getStatus() + " → " + targetStatus);
        }
        LocalDateTime approvedAt = "APPROVED".equals(targetStatus) ? LocalDateTime.now() : v.getApprovedAt();
        versionRepo.updateStatus(versionId, targetStatus,
                "APPROVED".equals(targetStatus) ? operator : v.getApprovedBy(), approvedAt, operator);
        v.setStatus(targetStatus);
        if ("APPROVED".equals(targetStatus)) {
            v.setApprovedBy(operator);
            v.setApprovedAt(approvedAt);
        }
        return v;
    }

    @Override
    public boolean isAvailable() {
        try {
            caliberRepo.findAll();
            return true;
        } catch (Exception e) {
            log.error("caliber service unavailable", e);
            return false;
        }
    }

    /** 口径域业务异常（携错误码，供 GlobalExceptionHandler 映射 4xx/5xx）。 */
    public static class BusinessException extends RuntimeException {
        public final int httpStatus;
        public final String code;
        public BusinessException(String code, int httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }
    }
}
