package com.chinacreator.gzcm.engine.data.fact.dq;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.RowRejection;

import java.util.List;
import java.util.Map;

/**
 * DQ 行级门禁门面 — 详细设计-02 §F02-07 / C.5（11 规则 DQ-F01..F11）。
 *
 * <p><b>实现方约定（跨 Agent 契约）</b>：
 * 11 条规则的 {@code DqRowGateImpl} 由<b>并行 Agent</b> 交付，本包<b>不</b>实现规则逻辑。
 * 真实实现 bean 必须以 bean 名 {@code dqRowGate} 注册（{@code @Component("dqRowGate")}
 * 或等价显式命名），本撮（sibling 撮）的 {@code FactImportService} 走
 * {@code @Autowired DqRowGate} 常规注入；单测注 mock/fake，跑不依赖真实规则。
 *
 * <p>批级不变量 <b>DQ-F05（同批单币种）</b> 由本撮 {@code FactImportService} 内建
 * pre-check 实现（批级不变量且触发整批 400 {@code ECOS-DATA-023}），不流经本接口。
 *
 * <p><b>接口最小化是刻意的</b>：若并行 Agent 命中的签名/参数与本文档不一致，
 * 由产品侧事后裁决（目前预期一致）。
 *
 * @see com.chinacreator.gzcm.engine.data.fact.model.RowRejection
 */
public interface DqRowGate {

    /**
     * 对一批行做 DQ-F01..F11 行级校验（不含 DQ-F05）。
     *
     * @param type     事实类型（对应五表其一）
     * @param rows     行数据（键 = camelCase 业务列名，对应
     *                 {@link FactType#columnWhitelist()}）
     * @param batchCtx 批次上下文：已知键
     *                 {@code batchId / userId / createTime(ISO) / declaredCurrency(可选) /
     *                 factType(FactType) / targetPeriod(可选)}，实现方可按需自取。
     * @return 被拒行定位清单（0 行 = 全部通过）
     */
    List<RowRejection> checkRows(FactType type, List<Map<String, Object>> rows,
                                 Map<String, Object> batchCtx);
}
