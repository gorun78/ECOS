package com.chinacreator.gzcm.engine.ai.capability;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 能力请求（《补充设计 v1.0》§10.4 模型入参，样本 form；§12.2 统一 Trace 维度子集）。
 *
 * <p>纪律：</p>
 * <ul>
 *   <li>{@code inputRef} 只放<b>引用 ID</b>（文件/片段/向量引用），禁携带完整敏感输入正文
 *       （§10.5 末段：控制域表只存引用与元数据）；</li>
 *   <li>{@code modelRef} 允许缺失（= 由 Model Router 选择，§10.1/§10.3）；调用方指定模型
 *       视为路由提示，Router 仍校验密级/版本/发布状态，禁隐式绕过（SUP-MODEL-01）；</li>
 *   <li>{@code traceId}/{@code runId} 由执行侧解析并回填（§12.2 全维度贯通），请求侧可空；</li>
 *   <li>{@code asParams()} 序列化键名与 runtime-task {@code TaskDescription.parameters}
 *       对齐，异步提交单通道不二次翻译。</li>
 * </ul>
 */
public class CapabilityRequest {

    private AiCapabilityId capabilityId;
    /** 输入引用（file-001 / 向量引用 / 片段 ID）；NOT NULL（V269 input_ref NOT NULL）。 */
    private String inputRef;
    /** 路由提示：指定模型引用（可空 = Router 选择）。 */
    private String modelRef;
    /** 指定模型版本（可空 = Router 按发布状态选）。 */
    private String modelVersion;
    /** 租户（V269 tenant_id NOT NULL）。 */
    private String tenantId;
    /** 组织（V269 org_id）。 */
    private String orgId;
    /** 触发本调用的工具契约引用（V269 tool_contract_ref；§11 机校字段单源在契约侧）。 */
    private String toolContractRef;
    /** 关联运行 ID（V269 run_id / §12.2 统一 trace）。 */
    private String runId;
    /** traceId（可空；执行侧未传入时由网关链路上下文补齐，禁静默伪造）。 */
    private String traceId;
    /** 提交者（V269 submitter）。 */
    private String submitter;
    /** 幂等键（§7.5；同一键重试不得重复创建/重复计量）。 */
    private String idempotencyKey;
    /** 补充参数（模态特定；keys 为运营键，禁塞模型/供应商名）。 */
    private Map<String, Object> params;

    public AiCapabilityId getCapabilityId() {
        return capabilityId;
    }

    public void setCapabilityId(AiCapabilityId capabilityId) {
        this.capabilityId = capabilityId;
    }

    public String getInputRef() {
        return inputRef;
    }

    public void setInputRef(String inputRef) {
        this.inputRef = inputRef;
    }

    public String getModelRef() {
        return modelRef;
    }

    public void setModelRef(String modelRef) {
        this.modelRef = modelRef;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getToolContractRef() {
        return toolContractRef;
    }

    public void setToolContractRef(String toolContractRef) {
        this.toolContractRef = toolContractRef;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSubmitter() {
        return submitter;
    }

    public void setSubmitter(String submitter) {
        this.submitter = submitter;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * 展平为 runtime-task {@code TaskDescription.parameters}（异步通道单源；
     * capabilityId 取枚举名，禁自由字符串）。
     */
    public Map<String, Object> asParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (capabilityId != null) {
            m.put("capabilityId", capabilityId.name());
        }
        if (inputRef != null) {
            m.put("inputRef", inputRef);
        }
        if (modelRef != null) {
            m.put("modelRef", modelRef);
        }
        if (modelVersion != null) {
            m.put("modelVersion", modelVersion);
        }
        if (toolContractRef != null) {
            m.put("toolContractRef", toolContractRef);
        }
        if (runId != null) {
            m.put("runId", runId);
        }
        if (tenantId != null) {
            m.put("tenantId", tenantId);
        }
        if (traceId != null) {
            m.put("traceId", traceId);
        }
        if (idempotencyKey != null) {
            m.put("idempotencyKey", idempotencyKey);
        }
        if (params != null) {
            m.putAll(params);
        }
        return m;
    }
}
