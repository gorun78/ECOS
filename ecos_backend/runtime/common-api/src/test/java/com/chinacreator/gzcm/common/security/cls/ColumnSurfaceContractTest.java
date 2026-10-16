package com.chinacreator.gzcm.common.security.cls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLS 列面单源读取契约的用例（#142/B4-3d-1，差距 D-B4-25）。
 *
 * <p><b>缺陷本体</b>：生产者 {@code ColumnLevelSecurityServiceImpl#getColumns} 的 {@code data}
 * 段写的是 {@code visibleColumns}，而 workspace / ontology / ai 三个消费者都按 legacy 契约册的
 * {@code allowedColumns} 取数 —— 该键在生产侧从未存在，三处一律取到 {@code null} 后返回空集，
 * 于是三通道的列级安全<b>结构性恒 DENY</b>（任何主体任何资产都拿不到可读列）。
 * 本类把「怎么读列面」的唯一口径钉死，三消费者只做委托，不再各自写字面量。</p>
 *
 * <p>反假绿：{@link #emptyVisibleSurfaceIsPolicyDenyNotDrift()} 与
 * {@link #absentBothKeysIsDriftWithReason()} 覆盖两个都返空集但语义必须分立的态；
 * {@link #authoritativeKeyWinsWhenBothPresent()} 保证「别名反客为主」不可能发生
 * （那等于把缺陷重新引回来）。每例都断到具体键/具体码，禁只断「非空」。</p>
 */
@DisplayName("CLS 列面单源契约：visibleColumns 权威 + 别名兜底 + 漂移分态")
class ColumnSurfaceContractTest {

    // ═══════════════ 1. 权威键（生产者唯一写出形态） ═══════════════

    @Test
    @DisplayName("生产者形态（visibleColumns 非空）→ 可读集原样返回，未走别名")
    void authoritativeKeyIsReadAsVisibleSurface() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", Arrays.asList("id", "amount", "customer"));
        data.put("blockedColumns", List.of("salary"));

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertFalse(reading.drifted(), "生产者形态不得被判漂移");
        assertFalse(reading.legacyKeyUsed(), "权威键在位时禁记为走别名");
        assertEquals(List.of("id", "amount", "customer"), reading.visible());
        assertEquals(List.of("salary"), reading.blocked());
        assertTrue(reading.usable());
        assertNull(reading.reason(), "非漂移态不带理由");
    }

    @Test
    @DisplayName("visibleColumns 为空表＝策略拒绝全部列（ECOS-SEC-402），不是形态漂移")
    void emptyVisibleSurfaceIsPolicyDenyNotDrift() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", List.of());
        data.put("blockedColumns", Arrays.asList("salary", "id_card"));

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertFalse(reading.drifted(), "空可读集是策略裁决结果，禁与「读不到键」混为一态");
        assertTrue(reading.visible().isEmpty());
        assertEquals(2, reading.blocked().size());
        assertFalse(reading.usable(), "usable 是消费者唯一放行判据：空可读集必须为 false");
    }

    // ═══════════════ 2. 漂移态（两键皆不可读） ═══════════════

    @Test
    @DisplayName("两键都不存在 → DRIFT_ABSENT 带理由，可读集恒空")
    void absentBothKeysIsDriftWithReason() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", "ecos_dw.sales_order");
        data.put("policies", List.of());

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertTrue(reading.drifted());
        assertNotNull(reading.reason());
        assertTrue(reading.reason().startsWith(ColumnSurfaceContract.DRIFT_ABSENT),
                "漂移理由必须带可机读前缀，实际=" + reading.reason());
        assertTrue(reading.visible().isEmpty(), "不变量：漂移态可读集恒空");
        assertFalse(reading.usable());
    }

    @Test
    @DisplayName("data 段缺失 / 非 Map → DRIFT_NO_DATA（与列面键缺失分码）")
    void missingOrMalformedDataSegmentIsDistinctDriftCode() {
        assertTrue(ColumnSurfaceContract.read(null).drifted());
        assertEquals(ColumnSurfaceContract.DRIFT_NO_DATA,
                ColumnSurfaceContract.read(null).reason());
        assertEquals(ColumnSurfaceContract.DRIFT_NO_DATA,
                ColumnSurfaceContract.read("visibleColumns").reason(), "data 是字符串也属外层形态漂移");
        assertEquals(ColumnSurfaceContract.DRIFT_NO_DATA,
                ColumnSurfaceContract.read(List.of("id")).reason());
    }

    @Test
    @DisplayName("别名键存在但值为 null → 仍判漂移（禁把 null 折成空可读集冒充策略裁决）")
    void nullValuedAliasIsNotReadable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allowedColumns", null);

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertTrue(reading.drifted());
        assertFalse(reading.legacyKeyUsed());
        assertTrue(reading.reason().startsWith(ColumnSurfaceContract.DRIFT_ABSENT));
    }

    @Test
    @DisplayName("两键都非列表形态（如 Map/数字）→ 漂移，不做类型猜测")
    void nonListKeyValuesAreDrift() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", Map.of("0", "id"));
        data.put("allowedColumns", 3);

        assertTrue(ColumnSurfaceContract.read(data).drifted());
    }

    // ═══════════════ 3. 别名兜底（只增兼容，不得反客为主） ═══════════════

    @Test
    @DisplayName("仅 legacy 别名在位 → 可读但打 legacyKeyUsed 标记")
    void legacyAliasOnlyIsHonouredAndFlagged() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allowedColumns", Arrays.asList("id", "amount"));

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertFalse(reading.drifted());
        assertTrue(reading.legacyKeyUsed(), "走别名必须可观测（消费者据此落 warn 日志）");
        assertEquals(List.of("id", "amount"), reading.visible());
    }

    @Test
    @DisplayName("权威键与别名同时在位 → 权威键胜出，别名不参与")
    void authoritativeKeyWinsWhenBothPresent() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", Arrays.asList("id", "amount"));
        data.put("allowedColumns", Arrays.asList("id", "amount", "salary", "id_card"));

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertFalse(reading.legacyKeyUsed());
        assertEquals(List.of("id", "amount"), reading.visible(),
                "别名不得反客为主——那等于把 D-B4-25 重新引回（读侧权重写错就会超发列）");
    }

    @Test
    @DisplayName("只有 blockedColumns 在位 → 漂移（被隐藏列集永不可当可读集）")
    void blockedColumnsAloneIsNeverReadable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("blockedColumns", Arrays.asList("salary", "id_card"));

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertTrue(reading.drifted());
        assertTrue(reading.visible().isEmpty());
    }

    // ═══════════════ 4. 值面清洗与不可变性 ═══════════════

    @Test
    @DisplayName("列表内 null 元素被跳过且不抛 NPE（JDK 不可变集合 contains(null) 会 NPE）")
    void nullElementsAreSkipped() {
        List<Object> withNulls = new ArrayList<>();
        withNulls.add("id");
        withNulls.add(null);
        withNulls.add("amount");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", withNulls);

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertEquals(List.of("id", "amount"), reading.visible());
    }

    @Test
    @DisplayName("读取结果两个列表均不可变（消费者不得回写契约面）")
    void readingListsAreUnmodifiable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", Arrays.asList("id"));
        data.put("blockedColumns", Arrays.asList("salary"));

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(data);

        assertThrows(UnsupportedOperationException.class, () -> reading.visible().add("salary"));
        assertThrows(UnsupportedOperationException.class, () -> reading.blocked().clear());
    }

    @Test
    @DisplayName("契约键名单源：键字面量与本类常量一致（生产者/消费者改名的唯一对照点）")
    void keyLiteralsAreSingleSourced() {
        assertEquals("visibleColumns", ColumnSurfaceContract.KEY_VISIBLE_COLUMNS);
        assertEquals("blockedColumns", ColumnSurfaceContract.KEY_BLOCKED_COLUMNS);
        assertEquals("allowedColumns", ColumnSurfaceContract.KEY_ALLOWED_COLUMNS_LEGACY);
        // #142/B4-3d-2（D-B4-16）：裁决束列面的三枚证据键同属单源，改名必须先过本类
        assertEquals("columnUniverseSource", ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE);
        assertEquals("columnUniverseResolved", ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED);
        assertEquals("clsDenyReason", ColumnSurfaceContract.KEY_CLS_DENY_REASON);
    }

    @Test
    @DisplayName("理由词表单源：四值互不相同（消费侧靠它们分立 403 / 503 / 缺陷三态）")
    void denyReasonVocabularyIsDistinctAndSingleSourced() {
        List<String> reasons = List.of(
                ColumnSurfaceContract.DENY_POLICY,
                ColumnSurfaceContract.DENY_UNIVERSE_UNRESOLVED,
                ColumnSurfaceContract.DENY_SOURCE_ERROR,
                ColumnSurfaceContract.DRIFT_ABSENT,
                ColumnSurfaceContract.DRIFT_NO_DATA);
        assertEquals(5, reasons.stream().distinct().count(), "理由码不得复用（复用＝两态被折成一支）");
        assertEquals("CLS_POLICY_DENY", ColumnSurfaceContract.DENY_POLICY);
        assertEquals("COLUMN_UNIVERSE_UNRESOLVED", ColumnSurfaceContract.DENY_UNIVERSE_UNRESOLVED);
        assertEquals("CLS_SOURCE_ERROR", ColumnSurfaceContract.DENY_SOURCE_ERROR);
        // universe 源值同为单源：生产侧只可能写这两个值之一
        assertEquals("REQUEST", ColumnSurfaceContract.UNIVERSE_REQUEST);
        assertEquals("NONE", ColumnSurfaceContract.UNIVERSE_NONE);
    }

    // ═══════════════ 5. 裁决束列面的证据读数（#142/B4-3d-2，差距 D-B4-16） ═══════════════

    @Test
    @DisplayName("universeUnresolved 只在显式 false 时为真；键缺失/非布尔/true 均为假")
    void universeUnresolvedRequiresExplicitFalse() {
        assertTrue(ColumnSurfaceContract.universeUnresolved(
                Map.of(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, false)));
        assertFalse(ColumnSurfaceContract.universeUnresolved(
                Map.of(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, true)));
        // 键缺失 ⇒ 假：/cls/columns 的应答从来没有这枚键，凭空判"不可解析"会让
        // 唯一没有证据段的应答面变红（真值由裁决束生产者写键 + 门禁钉住，不靠默认值兜）
        assertFalse(ColumnSurfaceContract.universeUnresolved(Map.of("visibleColumns", List.of())));
        assertFalse(ColumnSurfaceContract.universeUnresolved(
                Map.of(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, "false")),
                "字符串 \"false\" 不是布尔 false（JSON 反序列化形态漂移不做类型猜测）");
        assertFalse(ColumnSurfaceContract.universeUnresolved(null));
        assertFalse(ColumnSurfaceContract.universeUnresolved("columnUniverseResolved"));
    }

    @Test
    @DisplayName("denyReasonOf：可读理由 / 缺失得 null / 空白得 null（禁消费者自造默认理由）")
    void denyReasonOfNeverInventsADefault() {
        assertEquals(ColumnSurfaceContract.DENY_SOURCE_ERROR, ColumnSurfaceContract.denyReasonOf(
                Map.of(ColumnSurfaceContract.KEY_CLS_DENY_REASON, ColumnSurfaceContract.DENY_SOURCE_ERROR)));
        assertNull(ColumnSurfaceContract.denyReasonOf(Map.of("visibleColumns", List.of("id"))));
        assertNull(ColumnSurfaceContract.denyReasonOf(
                Map.of(ColumnSurfaceContract.KEY_CLS_DENY_REASON, "   ")),
                "空白理由当 null：可用态禁写空串冒充「无理由」（与三消费者读取面同纪律）");
        assertNull(ColumnSurfaceContract.denyReasonOf(null));
        assertNull(ColumnSurfaceContract.denyReasonOf(Map.of(
                ColumnSurfaceContract.KEY_CLS_DENY_REASON, 402)));
    }

    @Test
    @DisplayName("正向对照：证据读数与 read() 互不干扰（加键不改变列面可读性判定）")
    void evidenceKeysDoNotDisturbColumnReading() {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("visibleColumns", Arrays.asList("id", "amount"));
        segment.put("blockedColumns", List.of("salary"));
        segment.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE,
                ColumnSurfaceContract.UNIVERSE_REQUEST);
        segment.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, true);

        ColumnSurfaceContract.Reading reading = ColumnSurfaceContract.read(segment);

        assertFalse(reading.drifted());
        assertTrue(reading.usable());
        assertEquals(2, reading.visible().size());
        assertFalse(ColumnSurfaceContract.universeUnresolved(segment));
        assertNull(ColumnSurfaceContract.denyReasonOf(segment));
    }
}
