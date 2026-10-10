package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力读服务实现（§13 {@code GET /api/v1/ai/capabilities}）。
 *
 * <p><b>配置单源（§十/E.5）</b>：工具集分组从既有 {@code sys_config} 的
 * {@code wagent.toolset.*} 族 key 读，一律经 sysman {@link SysConfigService} 门面
 * （ObjectProvider 可空装配），<b>禁 @Value 散落</b>。能力目录三要件
 * （contractVersion/evidenceRequired/automationLevel）冻结在 {@link AiCapabilityId} 代码单源，
 * 配置只补充"工具集是否在册且 active"的归属标签，不改动目录词表（禁单侧漂移，同 V265/V266 纪律）。</p>
 */
@Service
public class CapabilityReadServiceImpl implements CapabilityReadService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityReadServiceImpl.class);

    /** wagent.toolset 族 key（V265 seed 组 4，config_group='wagent.toolset'）。 */
    public static final String GROUP_TOOLSET = "wagent.toolset";
    public static final String KEY_TOOLSET_CORE = "wagent.toolset.core";
    public static final String KEY_TOOLSET_ANALYTICS = "wagent.toolset.analytics";
    public static final String KEY_TOOLSET_ACTION = "wagent.toolset.action";

    /** 能力族 → 代码冻结默认 toolset 标签（承担目录初值；配置在册时以配置 group 为准回填属主）。 */
    private static final Map<String, String> FAMILY_TOOLSET = new LinkedHashMap<>();

    static {
        FAMILY_TOOLSET.put("DATA_ASSET", "core");
        FAMILY_TOOLSET.put("ONTOLOGY", "analytics");
        FAMILY_TOOLSET.put("TEXT", "analytics");
        FAMILY_TOOLSET.put("DOCUMENT", "action");
        FAMILY_TOOLSET.put("AUDIO", "action");
        FAMILY_TOOLSET.put("IMAGE", "action");
        FAMILY_TOOLSET.put("MULTIMODAL&TEXT", "core");
    }

    private final ObjectProvider<SysConfigService> sysConfigProvider;

    public CapabilityReadServiceImpl(ObjectProvider<SysConfigService> sysConfigProvider) {
        this.sysConfigProvider = sysConfigProvider;
    }

    @Override
    public List<CapabilityReadVO> listCapabilities() {
        List<String> presentToolsets = readToolsetGroup();
        List<CapabilityReadVO> out = new ArrayList<>(AiCapabilityId.all().size());
        for (AiCapabilityId id : AiCapabilityId.all()) {
            String hint = FAMILY_TOOLSET.getOrDefault(id.family(), "core");
            // 配置在册且 group active ⇒ toolset 归属以配置为准；缺配置回落代码冻结默认（禁报错）
            String toolset = presentToolsets.isEmpty() ? hint
                    : (presentToolsets.contains(hint) ? hint : null);
            out.add(CapabilityReadVO.from(id, toolset));
        }
        return out;
    }

    @Override
    public CapabilityReadVO getCapability(String capabilityId) {
        if (capabilityId == null || capabilityId.isBlank()) {
            return null;
        }
        for (CapabilityReadVO vo : listCapabilities()) {
            if (capabilityId.equals(vo.capabilityId())) {
                return vo;
            }
        }
        return null;
    }

    /** 读 wagent.toolset 族在册且 active 的工具集标签集合（sysman 未装配/读失败 ⇒ 空集回落代码默认）。 */
    private List<String> readToolsetGroup() {
        SysConfigService cfg = sysConfigProvider.getIfAvailable();
        if (cfg == null) {
            log.warn("SysConfigService 未装配，wagent.toolset 回落代码冻结默认（禁 @Value）");
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = cfg.getByGroup(GROUP_TOOLSET);
            List<String> present = new ArrayList<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String key = asString(row.get("config_key"));
                    String status = asString(row.get("status"));
                    if (key == null) {
                        continue;
                    }
                    if (KEY_TOOLSET_CORE.equals(key) && isActive(status)) {
                        present.add("core");
                    } else if (KEY_TOOLSET_ANALYTICS.equals(key) && isActive(status)) {
                        present.add("analytics");
                    } else if (KEY_TOOLSET_ACTION.equals(key) && isActive(status)) {
                        present.add("action");
                    }
                }
            }
            return present;
        } catch (Exception e) {
            log.warn("读取 wagent.toolset 族配置失败，回落代码冻结默认（目录词表不受影响）", e);
            return List.of();
        }
    }

    private static boolean isActive(String status) {
        return status == null || "active".equalsIgnoreCase(status.trim());
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
