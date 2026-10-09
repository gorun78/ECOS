package com.chinacreator.gzcm.common.service;

/**
 * 底座内存治理契约（详细设计-06 F06-10 / W152）。
 *
 * <p>凡在进程内持有会话/轨迹等<b>有界视图</b>的底座件实现本接口，由 runtime-task 的
 * 单一清扫任务周期调用。架构铁律 §2.5-4「长运行任务不走各模块内自建
 * {@code @Scheduled}/线程池」—— 故 {@code expireAfterAccess} 语义的<b>驱动权</b>在
 * runtime-task，视图属主只提供"被清扫"的能力，不自建调度器（X-25）。</p>
 *
 * <p><b>为什么必须有周期清扫</b>：懒淘汰（只在读路径判定超时）会让"已超时但再不被读"
 * 的条目永久驻留 —— 内存门禁（F06-09 的 RSS 基线判定）就永远不可判。</p>
 */
public interface IMemorySweepable {

    /**
     * 清扫一轮：回收空闲超时条目，并按容量上限淘汰最久未访问条目。
     *
     * @return 本轮实际回收的条目数（0 = 无事发生）
     */
    int sweepIdleEntries();

    /**
     * 当前驻留条目数（内存门禁与诊断面读取；禁外部触达内部集合）。
     */
    int residentEntryCount();
}
