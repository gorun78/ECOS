package com.chinacreator.gzcm.engine.ai.tool;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 口径批准状态源（详细设计-06 F06-13 要点 1）—— 拒绝规则第一条的判据入口。
 *
 * <p><b>跨册接缝义务（§八）</b>：口径是本体域（分册 03）的一等实体，权威读取路径应为
 * {@code GET /api/v1/ontology/calibers/{code}/current} 的版本状态。{@code ontology-engine-api}
 * 当前<b>未导出</b>任何口径门面（全仓 grep 零命中），依铁律 §2.4/规则 5（跨引擎不操作对方表、
 * 安全与主数据经属主服务），ai-engine <b>不得</b>直查本体表，也不得自造第二个口径真源。</p>
 *
 * <p>因此本实现是带显式登记的过渡态：批准清单来自 sysman 配置门面
 * （{@code ecos.ai.caliber.approved}，单源配置，非硬编码），<b>清单外一律 UNKNOWN</b>，
 * 而 {@code UNKNOWN} 在 {@link RefusalRulesService} 里按 fail-closed 归入"未批准"→ 只澄清不给数。
 * 接缝就位后把本类换成经 security/ontology REST 的实现，调用点不变。</p>
 */
@Component
@ConfigurationProperties(prefix = "ecos.ai.caliber")
public class CaliberStatusSource {

    private static final Logger log = LoggerFactory.getLogger(CaliberStatusSource.class);

    /** 状态字面量（与 PRD-06 §4.2 拒绝规则口径一致）。 */
    public static final String APPROVED = "APPROVED";
    public static final String UNAPPROVED = "UNAPPROVED";
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * 已批准口径编码清单（配置注入）。默认空 ⇒ 一切编码 UNKNOWN ⇒ 数值路径默认被澄清拦住
     * （与"清单配错也不放行"的 fail-closed 取向一致，宁可不给数也不给无口径的数）。
     */
    private Set<String> approved = java.util.Set.of();

    public Set<String> getApproved() {
        return approved;
    }

    public void setApproved(Set<String> approved) {
        this.approved = approved == null ? Set.of() : Set.copyOf(approved);
    }

    /**
     * 判定口径状态：{@code APPROVED} / {@code UNAPPROVED} / {@code UNKNOWN}。
     *
     * <p>显式登记为未批准的编码（前缀 {@code !}，如 {@code !gmv_legacy_v1}）返回 UNAPPROVED，
     * 便于把"已知不该用"的口径与"尚未登记"的口径在文案上区分开。</p>
     */
    public String statusOf(String caliberCode) {
        if (caliberCode == null || caliberCode.isBlank()) return UNKNOWN;
        String code = caliberCode.trim().toLowerCase(Locale.ROOT);
        for (String entry : approved) {
            if (entry == null || entry.isBlank()) continue;
            String e = entry.trim().toLowerCase(Locale.ROOT);
            if (e.equals(code)) return APPROVED;
            if (e.equals("!" + code)) return UNAPPROVED;
        }
        log.debug("F06-13 口径状态不可判定（按未批准处理）: caliber={}", caliberCode);
        return UNKNOWN;
    }

    public boolean isApproved(String caliberCode) {
        return APPROVED.equals(statusOf(caliberCode));
    }

    /** 已批准清单快照（只读，供诊断端点显示"当前放行面"，禁回写）。 */
    public List<String> approvedSnapshot() {
        return approved.stream().filter(s -> s != null && !s.isBlank() && !s.startsWith("!"))
                .map(String::trim).sorted().toList();
    }
}
