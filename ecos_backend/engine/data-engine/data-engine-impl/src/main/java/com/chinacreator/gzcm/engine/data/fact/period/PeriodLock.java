package com.chinacreator.gzcm.engine.data.fact.period;

/**
 * 期间锁定端口 — 详细设计-02 F02-06 第 5 项（关账期写入拒绝 ECOS-DATA-022）。
 *
 * <p><b>抽取为端口的原因</b>：真实 period lock 服务属 09 册 FC-05（未建）。本端口
 * 允许 09 册落地后替换实现而不改撮。
 *
 * <p>默认实现 {@link OpenPeriodsPeriodLock}：全部期间开放（永不拒绝）。
 * 可被属性 {@code ecos.data.facts.closed-periods}（逗号列表）覆盖为运维/测试态
 * 的硬关账期，如 {@code 2025-01,2025-02}。
 *
 * <p><b>决策</b>：设计规范 C.8 关账不属于依赖不可用降级（不省略），属于拒绝，
 * 故 default 实现选择"全开放"而非 fail-closed，理由：09 册未授权开工前不应
 * 默认所有关账判定为拒绝（会把存量正常写都堵掉）；错误码显式（00 册一致性 D.1）。
 */
public interface PeriodLock {
    /**
     * @param period 'YYYY-MM'
     * @return true = 期间已关账，**拒绝写入**
     */
    boolean isClosed(String period);
}
