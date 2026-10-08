package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import com.chinacreator.gzcm.engine.cognitive2.model.SimulationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设计-05 F05-10 要点2 冻结守卫：{@code ScenarioSimulatorServiceImpl}（RAG+Agent 推演）
 * 所有出口强制 {@code exploratory=true}，响应结构内不得出现金额字段
 * （贡献额/利润差一律由预测运行服务给，UI 显示"非核算口径"）。
 */
class ExploratoryGuardTest {

    private static final Pattern MONETARY_NAME = Pattern.compile(
            "(?i).*(amount|money|profit|revenue|cost|fee|budget|yuan|金额|利润|贡献额).*");

    @Test
    @DisplayName("F05-10②：SimulationResult 无金额类型字段，且推演出口 exploratory 恒 true")
    void ragSimulatorOutputHasNoMonetaryFields() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Field f : SimulationResult.class.getDeclaredFields()) {
            if (f.isSynthetic()) {
                continue;
            }
            if (BigDecimal.class.equals(f.getType()) || MONETARY_NAME.matcher(f.getName()).matches()) {
                offenders.add(f.getName() + ":" + f.getType().getSimpleName());
            }
        }
        assertTrue(offenders.isEmpty(), "探索式推演响应含金额字段（禁作金额来源）: " + offenders);
        assertTrue(new SimulationResult().isExploratory(),
                "F05-10②：exploratory 缺省必须为 true（非核算口径）");

        Path implSrc = CogScope.MAIN.resolve(
                "com/chinacreator/gzcm/engine/cognitive2/service/ScenarioSimulatorServiceImpl.java");
        String src = Files.readString(implSrc);
        assertTrue(src.contains("setExploratory(true)"),
                "推演实现必须显式对所有出口 setExploratory(true)，禁依赖构造缺省漂移");
        assertFalse(src.contains("setExploratory(false)"),
                "RAG 推演通道任何位置不得把 exploratory 置回 false");
    }
}
