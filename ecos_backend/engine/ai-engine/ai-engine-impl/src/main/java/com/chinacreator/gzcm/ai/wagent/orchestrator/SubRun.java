package com.chinacreator.gzcm.ai.wagent.orchestrator;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 分册10 F10-14 · SubRun（子 run）约束：深度 ≤ 2，{@code on_behalf_of} 继承，
 * 子 run 消耗<b>原子加回</b>父 run（consumption 回滚合并，禁双算）。
 *
 * <p>铁律 Delegation 单层（红线 #5）在 W 侧放宽为深度 ≤ 2：0=根 run，1=一层子，2=二层子，
 * 3 拒绝。on_behalf_of 恒继承根 run 的 initiated_by（子 run 不篡改发起人，审计可穿透到根）。
 * consumption 用 BigDecimal 原子加，禁 double（金额/消耗铁律）。</p>
 */
public final class SubRun {

    private SubRun() {}

    public static final int MAX_DEPTH = 2;

    /** 深度上限判定：currentParentDepth 是父 run 的深度，子 run 深度 = parent+1，超过即拒。 */
    public static boolean canSpawn(int currentParentDepth) {
        return currentParentDepth >= 0 && currentParentDepth < MAX_DEPTH;
    }

    /** on_behalf_of 继承：子 run 恒继承父 run 根发起人，禁回传 null/空（审计穿透）。 */
    public static String inheritOnBehalfOf(String parentInitiatedBy) {
        Objects.requireNonNull(parentInitiatedBy, "parentInitiatedBy");
        if (parentInitiatedBy.isBlank()) throw new IllegalArgumentException("on_behalf_of 不可空白");
        return parentInitiatedBy;
    }

    /**
     * 子 run 完成时把消耗原子加回父 run。返回加回后的父 run 消耗合计（BigDecimal，禁 double）。
     * parentAccum 与 child 同维度相加（此处以单维消耗示例；多维消耗由调用方分字段各调一次）。
     */
    public static BigDecimal addConsumptionBack(BigDecimal parentAccum, BigDecimal childConsumption) {
        Objects.requireNonNull(parentAccum, "parentAccum");
        Objects.requireNonNull(childConsumption, "childConsumption");
        return parentAccum.add(childConsumption);
    }
}
