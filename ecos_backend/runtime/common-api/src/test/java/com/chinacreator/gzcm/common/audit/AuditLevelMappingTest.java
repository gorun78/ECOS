package com.chinacreator.gzcm.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** DQ-1 / DC-5：审计四级与 clearance_level/audit_mode 映射核对（#147J）。 */
class AuditLevelMappingTest {

    @Test
    void fourLevelsAreMaintainedAndDegradationIsNotDefault() {
        assertEquals(4, AuditLevel.values().length);
        for (AuditLevel level : AuditLevel.values()) {
            assertFalse(level.degradedToThreeLevels(), "降三级只是选项，非默认");
        }
        assertEquals(AuditLevel.L4_CRITICAL, AuditLevel.fromLevel(4));
        assertNull(AuditLevel.fromLevel(5), "越界禁静默默认低等级");
    }

    @Test
    void consistentCombinationsPass() {
        assertTrue(AuditLevelMapping.isConsistent(AuditLevel.L1_REGULAR, 0, "basic", 1));
        assertTrue(AuditLevelMapping.isConsistent(AuditLevel.L2_CONTROLLED, 1, "detailed", 1));
        assertTrue(AuditLevelMapping.isConsistent(AuditLevel.L3_HIGH_RISK, 2, "full", 1));
        assertTrue(AuditLevelMapping.isConsistent(AuditLevel.L4_CRITICAL, 3, "full", 2));
    }

    @Test
    void weakerAuditModeOrClearanceIsRejected() {
        List<String> weakMode = AuditLevelMapping.check(AuditLevel.L3_HIGH_RISK, 3, "basic", 1);
        assertTrue(weakMode.stream().anyMatch(v -> v.contains("弱于")));

        List<String> lowClearance = AuditLevelMapping.check(AuditLevel.L3_HIGH_RISK, 1, "full", 1);
        assertTrue(lowClearance.stream().anyMatch(v -> v.contains("clearance_level=1")));

        List<String> badClearance = AuditLevelMapping.check(AuditLevel.L1_REGULAR, 9, "basic", 1);
        assertTrue(badClearance.stream().anyMatch(v -> v.contains("越界")));
    }

    @Test
    void criticalLevelRequiresDualApproval() {
        List<String> single = AuditLevelMapping.check(AuditLevel.L4_CRITICAL, 4, "full", 1);
        assertTrue(single.stream().anyMatch(v -> v.contains("双人")));
        assertTrue(AuditLevelMapping.isConsistent(AuditLevel.L4_CRITICAL, 4, "full", 2));
    }

    @Test
    void nullLevelIsNotTreatedAsLowRisk() {
        assertFalse(AuditLevelMapping.isConsistent(null, 4, "full", 2));
    }
}
