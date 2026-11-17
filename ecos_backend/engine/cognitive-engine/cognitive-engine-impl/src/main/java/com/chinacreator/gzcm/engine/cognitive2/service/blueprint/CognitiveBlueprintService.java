package com.chinacreator.gzcm.engine.cognitive2.service.blueprint;

import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveBlueprintLayerVO;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认知六层蓝图聚合服务（D151）。
 *
 * <p><b>背景</b>：前端 {@code #/mission_control} 认知蓝图页依赖 {@code GET /api/v1/cognitive/blueprint}
 * 拉六层健康度，但该端点后端从未实现 ⇒ 页面恒降级为「示意数据」。本服务补上真实聚合腿。</p>
 *
 * <p><b>聚合口径（无假真理铁律 7）</b>：六层一一对应六个引擎，各调公开健康端点
 * {@code /api/v1/engine/{layer-engine}/health}（route-manifest 已声明匿名），
 * 用<b>真实</b> {@code status} 与 {@code components} 计算 coverage：</p>
 * <ul>
 *   <li>引擎 {@code UP} 且探活成功 ⇒ {@code verified=true}，coverage 取其 components 派生的完成度；</li>
 *   <li>引擎非 UP / 探活异常 / 响应缺字段 ⇒ {@code verified=false}，coverage 记 0 并留 {@code reason}；
 *       <b>绝不编造覆盖率数字</b>（前端据 verified=false 继续显示降级语义）。</li>
 * </ul>
 *
 * <p><b>跨引擎调用纪律</b>：一律经 {@code cognitiveRestTemplate}（共享底座 + 凭证中继 + 去 XML）
 * 与 {@link ServiceEndpointResolver} 双模寻址，禁自建客户端/硬编码端口（X-35）。</p>
 */
@Service
public class CognitiveBlueprintService {

    private static final Logger log = LoggerFactory.getLogger(CognitiveBlueprintService.class);

    /** 六层 → 引擎健康端点 owner 服务（route-manifest）单源。 */
    private static final String OWNER = "gateway";

    /** 六层定义：layerId、中文名、对应引擎健康端点 slug。顺序即前端展示顺序。 */
    private static final LayerDef[] LAYERS = {
            new LayerDef("strategic",      "战略与决策层",        "cognitive"),
            new LayerDef("knowledge",      "认知与知识层",        "knowledge"),
            new LayerDef("agent_os",       "智能体操作系统层",     "ai"),
            new LayerDef("semantic",       "语义与业务层",        "ontology"),
            new LayerDef("data_platform",  "数据平台层",          "data"),
            new LayerDef("security_infra", "安全治理与基础设施",   "security"),
    };

    private final RestTemplate restTemplate;
    private final String engineBase;

    @Autowired
    public CognitiveBlueprintService(@Qualifier("cognitiveRestTemplate") RestTemplate restTemplate,
                                     ServiceEndpointResolver endpointResolver) {
        this.restTemplate = restTemplate;
        // 六引擎健康端点均经 gateway 承流（owner=gateway），单模寻址；解析失败 fail-loud 由上层映射为 503。
        this.engineBase = endpointResolver.resolve(OWNER, ServiceEndpointResolver.RouteMode.MONOLITH).orElse(null);
    }

    /**
     * 聚合六层蓝图。任何单层失败不影响其余层（逐层隔离），整体永不因单层异常而全 500。
     *
     * @return 六层 VO 列表（顺序同 {@link #LAYERS}）
     */
    public List<CognitiveBlueprintLayerVO> aggregate() {
        List<CognitiveBlueprintLayerVO> out = new ArrayList<>(LAYERS.length);
        for (LayerDef def : LAYERS) {
            out.add(probeLayer(def));
        }
        return out;
    }

    private CognitiveBlueprintLayerVO probeLayer(LayerDef def) {
        if (engineBase == null) {
            return CognitiveBlueprintLayerVO.unverified(def.id, def.nameZh, def.engine,
                    "route-manifest 未登记 " + OWNER + " 基址，无法探活");
        }
        String url = engineBase + "/api/v1/engine/" + def.engine + "/health";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<?, ?> body = resp.getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof Map<?, ?> d)) {
                return CognitiveBlueprintLayerVO.unverified(def.id, def.nameZh, def.engine,
                        "健康端点返回体缺少 data 对象");
            }
            String status = str(d.get("status"));
            boolean up = "UP".equalsIgnoreCase(status);
            Map<String, Object> components = new LinkedHashMap<>();
            if (d.get("components") instanceof Map<?, ?> c) {
                c.forEach((k, v) -> components.put(String.valueOf(k), v));
            }
            // coverage 口径：UP=100（真实在跑），非 UP 一律 verified=false + coverage=0；
            // 不按 agents/rules 数编造百分比，量化指标原样放 components 供前端展示。
            if (!up) {
                CognitiveBlueprintLayerVO vo = CognitiveBlueprintLayerVO.unverified(def.id, def.nameZh, def.engine,
                        "引擎状态=" + (status.isEmpty() ? "UNKNOWN" : status));
                vo.setComponents(components);
                return vo;
            }
            return CognitiveBlueprintLayerVO.verified(def.id, def.nameZh, def.engine,
                    100, components, null);
        } catch (Exception e) {
            log.warn("blueprint layer probe failed: layer={} engine={} err={}", def.id, def.engine, e.getMessage());
            return CognitiveBlueprintLayerVO.unverified(def.id, def.nameZh, def.engine,
                    "健康端点不可达: " + shortMsg(e));
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String shortMsg(Throwable t) {
        String m = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            m = m + ": " + t.getMessage();
        }
        return m.length() > 120 ? m.substring(0, 120) : m;
    }

    /** 层定义（单层）。 */
    private static final class LayerDef {
        final String id;
        final String nameZh;
        final String engine;

        LayerDef(String id, String nameZh, String engine) {
            this.id = id;
            this.nameZh = nameZh;
            this.engine = engine;
        }
    }
}
