package com.chinacreator.gzcm.engine.cognitive2.service.scenario;

import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 领域名 → Mind 能力映射（设计-05 F05-11 / D.2 / REQ-COG-04 规格 2、3）。
 *
 * <p><b>别名表单源 = sysman 配置门面</b>（键 {@code ecos.cognition.domain_alias}，
 * 铁律 §十 配置单源 / §E.5）："新增别名不改代码" ⇒ 本类<b>不内置</b>任何别名兜底表
 * （内置即代码/配置两态漂移，正是 E.5 红线要消除的形态）。未配置或形态不符 ⇒ 空表，
 * 未知 domain 由 {@code BeliefReader}/{@code BeliefScorer} 按 DG-7 记 {@code capability=null}、行不丢。</p>
 *
 * <p>匹配次序：① domain 直等于 {@code MindCapabilityMask} 枚举名（大小写不敏感）；
 * ② 门面别名表命中。两步都不中 ⇒ null。</p>
 */
@Component
public class DomainAliasResolver {

    private final CognitiveConfigQueryService configQueryService;

    public DomainAliasResolver(CognitiveConfigQueryService configQueryService) {
        this.configQueryService = configQueryService;
    }

    /** 未匹配返 null；不抛异常（配置门面抖动也不影响读路径主流程）。 */
    public String resolve(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String trimmed = domain.trim();
        String direct = directMaskName(trimmed);
        if (direct != null) {
            return direct;
        }
        try {
            Map<String, String> aliases = configQueryService.domainAliases();
            String hit = aliases.get(trimmed);
            return hit == null || hit.isBlank() ? null : hit;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 枚举名直等（大小写不敏感）；{@code MindCapabilityMask.fromName} 是严格等值，故此处自行折叠大小写。 */
    private static String directMaskName(String trimmed) {
        for (com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask m :
                com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask.values()) {
            if (m.name().equalsIgnoreCase(trimmed)) {
                return m.name();
            }
        }
        return null;
    }
}
