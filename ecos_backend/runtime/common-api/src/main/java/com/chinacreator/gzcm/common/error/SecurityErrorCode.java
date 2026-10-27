package com.chinacreator.gzcm.common.error;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

/**
 * 安全裁决域（ECOS-SEC）统一错误码常量 — 《ECOS 详细设计说明书·从零实施版》（2026-10-12）
 * 篇4 §4.3.3 错误码表（501/502/401/402/410/411/412/420/430/440）的 Java 单源。
 *
 * <p>设计约束（《从零实施版》§4.3.3 错误码总表，行 3266~3274／错误码清单行 10363~10372）：</p>
 * <ul>
 *   <li>{@code ECOS-SEC-501}（503）＝security-engine 不可用，操作 DENY（fail-closed，
 *       禁渲染空列表冒充「无数据」）；</li>
 *   <li>{@code ECOS-SEC-502}（503）＝OPA 不可用，fail-close 拒绝；</li>
 *   <li>{@code ECOS-SEC-401}（403）＝RLS 无匹配策略且 {@code denyIfEmpty}；</li>
 *   <li>{@code ECOS-SEC-402}（403）＝CLS 允许列为空集；</li>
 *   <li>{@code ECOS-SEC-410}（400）＝RLS 模板含非白名单字面量或关键字；</li>
 *   <li>{@code ECOS-SEC-411}（400）＝CLS 作用域或列名非法；</li>
 *   <li>{@code ECOS-SEC-412}（400）＝安全资产 {@code assetKey} 或灵敏度非法；</li>
 *   <li>{@code ECOS-SEC-420}（403）＝导出无脱敏策略的敏感列；</li>
 *   <li>{@code ECOS-SEC-430}（500）＝加密主密钥缺失，禁静默建钥（平台侧 critical 告警）；</li>
 *   <li>{@code ECOS-SEC-440}（400）＝审计时间窗过宽或结果过大（强制收窄）。</li>
 * </ul>
 *
 * <p><b>#146n/B4-3e-95 新登</b>（收口差距 <b>P1.1 错误码族系统性漂移</b>中「ECOS-SEC-401/402/420/440/501
 * 未定义」子项）：台账原句按《详细设计-00》§5.2 取证——该表只登 {@code ECOS-SEC-501} 一枚；
 * 按《从零实施版》取证则上表 10 枚<b>全部在册</b>，生产面字面量面经本批实测为：
 * 501（144 处）已由 {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE} 单源化（#142/B4-3e-2／
 * B4-3d-10 收口，既有门禁 {@code GuardrailObligationVocabularySingleSourceTest} 钉唯一持有者）；
 * 402／410／430／502 各有单文件属主常量（{@code ClsController}／{@code CryptoKeyRegistryService}
 * 等）但安全域内存在别名／散部门面，且 410×2／402×2／430×1／502×1 共 <b>9 枚非属主字面量
 * 副本</b>（承载位与措辞逐字不变＝源透明归一，本批同批改指本类）；401／411／412／420／440
 * 五枚生产字面量面＝0（只存在于文档表格），落位本类＝「冻结表在册码的在册登记半边」
 * （与 {@link SystemErrorCode} 的 SYS-404／SYS-410 同形——文档在册、出口未建，禁未来
 * 「顺手抄一份」）。本类是 ECOS-SEC 族<b>唯一 Java 单源常量持有者</b>；
 * {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}／{@code SCOPE_UNDECLARED_CODE}（520）
 * 经本类常量<b>授值</b>（不反向依赖），两枚既有单源门（{@code GuardrailObligationVocabularySingleSourceTest}
 * ／{@code RlsExecutionContractGateTest}）恒绿，零新增码表条目（520 系 #142/B4-3e-19 已裁决
 * 的内部裁决段码，不在 §4.3.3 表内、不写本类——避免把在途裁决写成已定档）；
 * 族码字面量的零容差判据面由 {@code NoMachineCodeFactoryFaceRatchetTest#secFamilyLiteralsAreSingleSourced}
 * 在 common-api 门禁面钉死（本批同批扩腿）。本类只含常量，无业务 import（除 501 授值的
 * {@link RlsExecutionContract} 引用——取其值不取其面，javadoc 注明），与 {@link AuthErrorCode}／
 * {@link RateLimitErrorCode}／{@code DataDomainErrorCode} 同包同形。</p>
 */
public final class SecurityErrorCode {

    private SecurityErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 503 — security-engine 不可用，操作 DENY（fail-closed 三通道：页面／导出／AI）。
     *
     * <p>值经 {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE} 授取：该常量是
     * 「不可用码」的既有单源（#142/B4-3e-2 收口四枚副本、#142/B4-3d-10 补本体／AI 两条腿），
     * {@code GuardrailObligationVocabularySingleSourceTest#secUnavailableCodeHasSingleOwner}
     * 钉它为主源唯一字面量持有者；本枚经授值使两枚既有门禁零扰动，而族面
     * {@link NoMachineCodeFactoryFaceRatchetTest} 的逐码对账认本类为族属主。</p>
     */
    public static final String ECOS_SEC_501 = RlsExecutionContract.SEC_UNAVAILABLE_CODE;

    /**
     * 503 — OPA 不可用，fail-close 拒绝（§4.3.3：「同上并说明策略引擎不可用」）。
     *
     * <p>属主出口＝security-engine {@code OpaPolicyService#fallbackDeny} 的
     * {@code eccode} 键（原为裸串字面量，本批改指本枚；应答键面
     * {@code eccode} 本身不属本门判面，只钉值）。</p>
     */
    public static final String ECOS_SEC_502 = "ECOS-SEC-502";

    /**
     * 403 — RLS 无匹配策略且 {@code denyIfEmpty}（默认拒绝，空态卡加提示）。
     *
     * <p>生产字面量面＝0（文档在册半边）：§4.3.3 内核短路 §4.2.2 顺序里该腿由
     * {@code DecisionBundle} 的 {@code denyIfEmpty} 段与谓词面承载，未出现裸码出口；
     * 落位本枚是防「顺手抄一份」，与 401 段出口批属后续批次（出口未建≠码不存在）。</p>
     */
    public static final String ECOS_SEC_401 = "ECOS-SEC-401";

    /**
     * 403 — CLS 允许列为空集（「当前角色无该资产可读列」）。
     *
     * <p>属主出口＝security-engine {@code ClsController}：原该类自持
     * {@code ERROR_CLS_EMPTY_DENY} 常量，本批改指本枚（别名保名，测试断言零改动）；
     * 非属主副本＝ontology {@code SecurityEngineClient}／ai {@code AiSecurityEngineClient}
     * 两处 {@code log.warn(... 策略拒绝全部列(ECOS-SEC-402) ...)} 腿（仅日志面、运行时无对外
     * 行为差异），本批一并改指。判面由族门逐文件钉，除属主外任何带引号形态即红。</p>
     */
    public static final String ECOS_SEC_402 = "ECOS-SEC-402";

    /**
     * 400 — RLS 谓词模板含非白名单字面量或关键字（行内错误并列可绑定变量清单）。
     *
     * <p>属主退出腿＝security-engine {@code RlsPredicateValidator}（{@code TemplateRejectionException}
     * 消息首段）与上抛它的两枚端点（{@code RlsController#upsertPolicy}／
     * {@code SecurityAssetCatalogController#upsert} 的 {@code badRequest} 文案括注）——原 3 处
     * 裸串同文，本批改指本枚（消息文案逐字不变）。</p>
     */
    public static final String ECOS_SEC_410 = "ECOS-SEC-410";

    /**
     * 400 — CLS 作用域或列名非法（表单校验提示）。
     *
     * <p>生产字面量面＝0（文档在册半边）：§4.3.3 表内 {@code upsertClsPolicy} 端点 400 腿的
     * 参数校验出口尚未落位具名码（当前走通用 {@code badRequest}），出口批属后续批次。</p>
     */
    public static final String ECOS_SEC_411 = "ECOS-SEC-411";

    /**
     * 400 — 安全资产 {@code assetKey} 或灵敏度非法（表单校验提示）。
     *
     * <p>生产字面量面＝0（文档在册半边）：{@code upsertSecurityAsset} 的通用 400 腿同 411，
     * 具名码出口批属后续批次。{@code SecurityAssetCatalogController} 现有 400 腿
     * 按文档以 410 括注（{@code 非法资产参数（ECOS-SEC-410）}），与本枚 412 的对应关系
     * 属文档 §4.3.3 表格裁定面，本批只单源化字面量、不改括注码号（改号＝码表变更越权）。</p>
     */
    public static final String ECOS_SEC_412 = "ECOS-SEC-412";

    /**
     * 403 — 导出无脱敏策略的敏感列（拒绝并提示需登记或脱敏）。
     *
     * <p>生产字面量面＝0（文档在册半边）：workspace {@code ExportDecision} 的硬门腿目前
     * 走 503 不可用分态，420 的具名拒绝出口尚不存在——§4.2.2 出口强制掩码 §15 验收面
     * （{@code MaskEnforcementOnExportAndAiTest}）另批处置时复用本枚。</p>
     */
    public static final String ECOS_SEC_420 = "ECOS-SEC-420";

    /**
     * 500 — 加密主密钥缺失，禁静默建钥（平台侧 critical 告警，用户侧通用失败提示）。
     *
     * <p>属主出口＝security-engine crypto 域：{@code CryptoKeyRegistryService#ERROR_MASTER_KEY_MISSING}
     * ／{@code IKeyManagementService.KeyNotFoundException#ERROR_CODE}（两枚均为该枚码的既有
     * 属主常量，报警双复制＝本批收口面），改指本枚后别名保名、测试断言（含
     * {@code CryptoKeyAdminContractTest} 值断言）零改动；副本＝{@code KeyManagementServiceImpl}
     * 启动 {@code log.warn(... 映射 ECOS-SEC-430 ...)}（改指本枚）。</p>
     */
    public static final String ECOS_SEC_430 = "ECOS-SEC-430";

    /**
     * 400 — 审计时间窗过宽或结果逾 10000 条（强制收窄提示）。
     *
     * <p>生产字面量面＝0（文档在册半边）：§4.3.3 审计页边界态行在
     * {@code AuditController} 的收窄参数校验出口尚未具名化，出口批属后续批次。</p>
     */
    public static final String ECOS_SEC_440 = "ECOS-SEC-440";
}
