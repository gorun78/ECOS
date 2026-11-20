package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeSettingsService;
import com.chinacreator.gzcm.sysman.config.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识库配置读写，经 sysman 配置门面 {@link ISysConfigService}。
 *
 * <p><b>为什么用 {@link ObjectProvider} 而非强注入</b>：本类随 kb-engine-impl 被独立微服务
 * dccheng（:18086）装配，而该服务的 classpath 只有 {@code sysman-api} 契约、没有
 * `SysConfigService` 实现（实现只在 sysman 模块内）。强注入会让 dccheng 在
 * context refresh 阶段因 {@code No qualifying bean of type 'ISysConfigService'} 启动失败。
 * 同批改造中 ai-engine 四处（`AgentToolSqlWhitelist` / `SessionTokenQuotaService` /
 * `SessionCompressionService` / `WAgentBudgetService`）已用本形态，此处补齐。</p>
 *
 * <p><b>门面缺席时的语义（fail-closed，禁伪造）</b>：读操作返回空/null
 * （调用方 `"true".equalsIgnoreCase(null)` 即为拒绝）；写操作抛 {@link IllegalStateException}
 * ——配置写不进去必须让调用方看见，不能静默成功。</p>
 */
@Service
public class KnowledgeSettingsServiceImpl implements KnowledgeSettingsService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSettingsServiceImpl.class);

    private static final String UNAVAILABLE_MSG =
            "知识库配置门面不可用：ISysConfigService 未在本服务装配（dccheng 仅含 sysman-api 契约）；"
                    + "配置写操作必须经 sysman 处理，禁止静默丢弃";

    private final ObjectProvider<ISysConfigService> sysConfigProvider;

    public KnowledgeSettingsServiceImpl(ObjectProvider<ISysConfigService> sysConfigProvider) {
        this.sysConfigProvider = sysConfigProvider;
    }

    /** 取门面；缺席返回 null（读路径据此降级，写路径抛异常）。 */
    private ISysConfigService facadeOrNull() {
        return sysConfigProvider.getIfAvailable();
    }

    @Override
    public List<Map<String, Object>> getAllSettings() {
        ISysConfigService facade = facadeOrNull();
        if (facade == null) {
            log.info("知识库配置门面不可用，返回空设置列表（fail-closed）");
            return List.of();
        }
        return facade.listByGroup("knowledge");
    }

    @Override
    public int batchUpdate(List<Map<String, String>> updates) {
        ISysConfigService facade = facadeOrNull();
        if (facade == null) {
            throw new IllegalStateException(UNAVAILABLE_MSG);
        }
        return facade.updateBatch(updates);
    }

    @Override
    public String getSetting(String key) {
        ISysConfigService facade = facadeOrNull();
        if (facade == null) {
            log.debug("知识库配置门面不可用，配置项 {} 按未配置处理（fail-closed）", key);
            return null;
        }
        List<Map<String, Object>> settings = facade.listByGroup("knowledge");
        for (Map<String, Object> s : settings) {
            if (key.equals(s.get("configKey"))) {
                Object val = s.get("configValue");
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }

    @Override
    public void upsertSetting(String key, String value, String group, String type, String description) {
        ISysConfigService facade = facadeOrNull();
        if (facade == null) {
            throw new IllegalStateException(UNAVAILABLE_MSG);
        }
        facade.upsertValue(key, value, group, type, description);
    }
}