package com.chinacreator.gzcm.engine.kb.cognition;

import com.chinacreator.gzcm.engine.kb.dao.CognitionReadDao;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 场景认知三态只读读取 — 详细设计-04 F04-08 / REQ-KB-04。
 *
 * <p>kb 对场景侧<b>只读</b>开放 hypothesis/belief/mind。读表真身见 {@link CognitionContractGuard}
 * 常量映射（{@code ecos_cognitive.ecos_cognitive_*}）。认知三态属 <b>认知引擎独立持久</b>（控制域外），
 * standard 档可能不存在对应表 → 读取命中 {@code DataAccessException} 时<b>返回空列表而非 404/500</b>
 *（认知数据"场景侧不可用"是合法空态，不是 kb_nav_* 那种结构性表缺失）。
 *
 * <p>写路径一律不在此：写必须经 cognitive 引擎，kb 侧写代理会被 {@link CognitionContractGuard#assertReadOnly()}
 * 拒绝（405 ECOS-KB-070）。
 *
 * <p>R2-B2b 数据访问收口（架构铁律 §2.5 / IR06）：raw-SQL 下沉至
 * {@link CognitionReadDao}，本类保留 service 职责（data access 异常降级为空列表）。</p>
 */
@Service
public class CognitionReadService {

    private static final Logger log = LoggerFactory.getLogger(CognitionReadService.class);

    private final CognitionReadDao dao;
    private final CognitionContractGuard guard;

    public CognitionReadService(CognitionReadDao dao, CognitionContractGuard guard) {
        this.dao = dao;
        this.guard = guard;
    }

    /** 只读 hypothesis 三态（status 白名单内），domain 过滤，空/表缺失 → 空列表。 */
    public List<Map<String, Object>> hypotheses(String domain, int limit) {
        return read(CognitionContractGuard.HYPOTHESIS_REAL_TABLE, domain, limit);
    }

    /** 只读 belief 三态。 */
    public List<Map<String, Object>> beliefs(String domain, int limit) {
        return read(CognitionContractGuard.BELIEF_REAL_TABLE, domain, limit);
    }

    /** 只读 mind 三态。 */
    public List<Map<String, Object>> minds(String domain, int limit) {
        return read(CognitionContractGuard.MIND_REAL_TABLE, domain, limit);
    }

    private List<Map<String, Object>> read(String realTable, String domain, int limit) {
        if (dao == null) {
            return new ArrayList<>();
        }
        String[] valid = HypothesisStatus.validStatusCodes();
        try {
            List<Map<String, Object>> rows = dao.read(realTable, valid, domain, limit);
            return rows == null ? new ArrayList<>() : rows;
        } catch (DataAccessException e) {
            // 认知三态表可能不存在（standard 档 / 场景未启用）→ 合法空态，非结构性表缺失
            log.debug("认知三态读取降级为空 table={} domain={}: {}", realTable, domain, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── F04-08 供给侧只读端点专用（2026-10-12 复盘：fail-loud，禁空列表伪装"无数据"） ──

    /**
     * 供给侧只读 hypothesis：后端不可用（DAO 缺席 / {@code DataAccessException}）→
     * 抛 {@code 503 ECOS-KB-073}（{@link KbErrorCodes#COGNITION_SUPPLY_UNAVAILABLE}），
     * 严禁降级空列表伪装"无数据"。真实空结果集仍返回空列表（合法空态）。
     */
    public List<Map<String, Object>> hypothesesSupply(String domain, int limit) {
        return supplyRead(CognitionContractGuard.HYPOTHESIS_REAL_TABLE, domain, limit);
    }

    /** 供给侧只读 belief（fail-loud 语义同 {@link #hypothesesSupply}）。 */
    public List<Map<String, Object>> beliefsSupply(String domain, int limit) {
        return supplyRead(CognitionContractGuard.BELIEF_REAL_TABLE, domain, limit);
    }

    /** 供给侧只读 mind（fail-loud 语义同 {@link #hypothesesSupply}）。 */
    public List<Map<String, Object>> mindsSupply(String domain, int limit) {
        return supplyRead(CognitionContractGuard.MIND_REAL_TABLE, domain, limit);
    }

    private List<Map<String, Object>> supplyRead(String realTable, String domain, int limit) {
        if (dao == null) {
            throw KbErrorCodes.COGNITION_SUPPLY_UNAVAILABLE.exception(
                    "认知供给侧不可用: CognitionReadDao 未装配 table=" + realTable);
        }
        String[] valid = HypothesisStatus.validStatusCodes();
        try {
            List<Map<String, Object>> rows = dao.read(realTable, valid, domain, limit);
            return rows == null ? new ArrayList<>() : rows;
        } catch (DataAccessException e) {
            // 后端不可用 ≠ 无数据：fail-loud 抛 503，禁空列表伪装（沿用 ECOS-KB-* 错误码风格）
            log.error("认知供给侧读取失败 table={} domain={}: {}", realTable, domain, e.getMessage(), e);
            throw KbErrorCodes.COGNITION_SUPPLY_UNAVAILABLE.exception(
                    "认知供给侧后端不可用（表缺失/访问被拒）table=" + realTable + " 原因=" + e.getMessage());
        }
    }
}
