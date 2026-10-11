package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.dao.CaliberDao;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidateDTO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidationVO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * H-2（详设-09 v1.2 §十一 / PRD-09 v1.2 §1.2·§1.4）— 口径公式 DSL 四关校验装配层。
 *
 * <p>{@code POST /api/v1/ontology/calibers/validate}（只校验不落库，向导第 3 步预检）的
 * Service 落点：从<b>指标注册表</b> {@code ecos_metric_definition}（R-4 唯一语义入口，
 * 口径主权归本体 R-6 ①）加载 {@code code → expression} 快照供给
 * {@link CaliberDslValidator}；注册表读取失败 = 依赖不可用，异常上抛由 Controller
 * 映射 500 {@code ECOS-ONTO-042} fail-closed（铁律 §2.4，<b>禁默认通过</b>）。</p>
 *
 * <p>边界：本服务<b>不求值</b>公式（求值属 cognitive-engine，按同一 EBNF 实现，
 * 文法原文见 {@link CaliberDslParser} 类注释）；不写任何表（R-60 ② 禁第五套版本栈，
 * {@code ecos_fc_caliber} 维持 V214 引用视图）。</p>
 *
 * <p><b>三处消费面（同一判定源，09 册 H-2 收口）</b>：
 * ①{@code POST /calibers/validate} 预检（{@link #validate}）；
 * ②{@link CaliberService} 的 {@code insertVersion} 公式落库咽喉；
 * ③{@link CaliberService} 的 {@code archiveVersion}（置 APPROVED 前）审批咽喉。
 * 后两者经 {@link #validateFormula} 走同一四关，避免「只在独立端点上报错、写入路径照样落库」。</p>
 */
@Component
public class CaliberFormulaDslService {

    private static final Logger log = LoggerFactory.getLogger(CaliberFormulaDslService.class);

    private final CaliberDao caliberDao;
    private final CaliberDslValidator validator = new CaliberDslValidator();

    public CaliberFormulaDslService(CaliberDao caliberDao) {
        this.caliberDao = caliberDao;
    }

    /** 四关校验总入口（V1 语法 → V2 引用闭合 → V4 无环 → V3 量纲；违一即记违反项）。 */
    public CaliberFormulaValidationVO validate(CaliberFormulaValidateDTO dto) {
        CaliberDslValidator.MetricSymbols symbols = loadMetricSymbols();
        CaliberFormulaValidationVO vo = validator.validate(dto, symbols);
        log.info("caliber-dsl validate target={} valid={} violations={} registryMetrics={}",
                vo.getTarget(), vo.isValid(), vo.getViolations().size(), symbols.size());
        return vo;
    }

    /**
     * 写入 / 审批咽喉用的窄口径入口：只凭公式文本跑四关（目标取公式左端 IDENT，
     * 私有事实字段与声明单位走内建登记册），指标注册表仍从
     * {@code ecos_metric_definition} 实时加载（R-4 唯一语义入口）。
     *
     * <p>与 {@code POST /api/v1/ontology/calibers/validate} 同一判定源，
     * 保证「预检通过 ⇒ 落库必过」不发生两套标准漂移。</p>
     */
    public CaliberFormulaValidationVO validateFormula(String formula) {
        CaliberFormulaValidateDTO dto = new CaliberFormulaValidateDTO();
        dto.setFormula(formula);
        return validate(dto);
    }

    /** 指标注册表快照：code → expression（列式，显式列清单经 CaliberDao；DR05 软删过滤）。 */
    CaliberDslValidator.MetricSymbols loadMetricSymbols() {
        List<Map<String, Object>> rows = caliberDao.listMetricDefinitionExpressions();
        Map<String, String> byCode = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object code = row.get("code");
            if (code == null) {
                continue;
            }
            Object expr = row.get("expression");
            byCode.put(String.valueOf(code), expr == null ? null : String.valueOf(expr));
        }
        return new CaliberDslValidator.MetricSymbols(byCode);
    }
}
