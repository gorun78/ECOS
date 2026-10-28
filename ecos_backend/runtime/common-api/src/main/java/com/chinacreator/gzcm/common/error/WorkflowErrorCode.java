package com.chinacreator.gzcm.common.error;

/**
 * 工作流域（ECOS-WF）统一错误码常量 —— 《ECOS 详细设计说明书-从零实施版》§6.3.3 错误码表
 * 中 {@code ECOS-WF-*} 族的代码侧单源。
 *
 * <p><b>#146y 新登</b>（收口差距 <b>P1.12 附录A 第 10 条</b>）：本族此前在代码面<b>零命中</b>
 * ——契约册（§6.3.3 表 + 附录A 第 10 条「错误码」列 + §6.5 状态矩阵「审批链断显示
 * {@code ECOS-WF-020 审批表不可达} 而非空列表」）已把该枚码冻结在册，但 {@code grep -rn
 * "ECOS-WF" ecos_backend --include=*.java} 在 main 面实扫 0 枚，即调用方按机读码分派
 * 「审批表不可达」这条失败态时恒落空。本类是 §6.3.3 已登记码的<b>唯一 Java 单源持有者</b>，
 * 本域其他类禁再复制字面量（字形枷由 {@code NoMachineCodeFactoryFaceRatchetTest#
 * wfFamilyLiteralsAreSingleSourced} 的零容差腿锁在 common-api 门禁面，与
 * {@link SystemErrorCode}／{@link RateLimitErrorCode}／{@link AuthErrorCode}／
 * {@link SecurityErrorCode} 四族同型）。</p>
 *
 * <p>纪律（与 {@link SystemErrorCode} 一致）：</p>
 * <ul>
 *   <li>码值即冻结值，禁改写——前端诊断面与测试按码匹配依赖字面量；</li>
 *   <li>本类<b>只登记 §6.3.3 在册枚</b>。工作流域其余失败态（任务不存在等）沿用域内既有
 *       符号码 {@code WF-0xx}（{@code WorkflowApprovalService} 的 {@code NotFoundException}
 *       文案面），不在本批把未在册的码补进族表——「族里多一枚」＝用代码冒充文档裁决；</li>
 *   <li>本类只含常量，无任何业务 import（common-api enforcer 白名单内）。</li>
 * </ul>
 *
 * <p>与 HTTP 状态行的关系：§6.3.3 把本枚钉在 <b>500</b>；承载方式与 {@code ECOS-SEC-501}
 * 同法——抛 {@code DataBridgeException}，机读码写在 {@code message} 起始位，由 gateway
 * {@code GlobalExceptionHandler#handleDataBridge} 经 {@code MachineErrorCode.orNumeric}
 * 单源取进信封 {@code errorCode} 字段（正则只认 message 起始位，故拼接形态必须是
 * {@code ECOS-WF-020 + ": " + 文案}，不可把码放在文案中段）。</p>
 */
public final class WorkflowErrorCode {

    private WorkflowErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 500 — 审批表不可达（schema 漂移）。
     *
     * <p><b>语义边界（§6.5 状态矩阵原文约束）</b>：审批链断裂<b>绝不</b>折叠成「空审批列表」
     * （200 + 空集）——空列表是「该实例确实没有审批行」这一真值，与「读不到表」是两个态，
     * 折叠后调用方无法区分，属 D-B4-71「依赖未就绪 ⇒ 折叠成正常空态」同型缺陷。因此本枚只能
     * 出现在<b>数据访问层抛错</b>的出口上；审批人省略、任务不存在等语义各有其码，禁借本码。</p>
     *
     * <p>属主出口＝buszhi {@code WorkflowInstanceApprovalController}（附录A 第 10 条契约面
     * {@code POST /api/v1/ontology/workflow-instances/{id}/approvals}）内
     * {@code catch (DataAccessException)} 腿。</p>
     */
    public static final String ECOS_WF_020 = "ECOS-WF-020";
}
