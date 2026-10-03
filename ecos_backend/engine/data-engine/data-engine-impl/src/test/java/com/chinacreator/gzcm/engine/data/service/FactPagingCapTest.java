package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * C.9 性能与容量基线「事实分页 默认 20 / 上限 200」护栏（F02-06-2c / BusinessFactService.listFacts）。
 *
 * <p>C.9 行 2（doc 行 408「事实分页 默认 20，上限 200」）已落地为
 * {@code listFacts:372-373 int p = Math.max(1, page); int s = Math.min(200, Math.max(1, size));}
 * （controller {@code @RequestParam(defaultValue="20") size} 起默认 20；服务侧把 size 钳到 [1,200]）。
 * 本护栏钉死该钳制语义，防未来误改为放行原值 / 误缩 / 下限丢失：</p>
 * <ul>
 *   <li>size=500 → pageSize 钳到 200（判据是 min(200) 而非放行原值）；</li>
 *   <li>size=0 / 负 → pageSize 钳到 1（下限 Math.max(1,..) 生效，不出现 LIMIT 0/负）；</li>
 *   <li>恰 200 + page=3 → pageSize=200、page=3（上限不误缩、page 原样透传）；</li>
 *   <li>缺省 20 → pageSize=20（controller 默认面无误改）。</li>
 * </ul>
 * <p>断言全部打在返回 Map 的 {@code pageSize}/{@code page} 上（{@code listFacts} 直接把钳制结果放进响应体）；
 * JdbcTemplate 打桩后未消费调用返回安全默认（COUNT→null→total=0、queryForList→空 list），
 * 不触库/不 Spring/不联网。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("C.9 容量基线「事实分页 默认 20 / 上限 200」护栏（BusinessFactService.listFacts）")
class FactPagingCapTest {

    @Mock
    JdbcTemplate jdbc;

    private BusinessFactService svc() {
        return new BusinessFactService(jdbc);
    }

    private static int pageSize(Map<String, Object> res) {
        return ((Number) res.get("pageSize")).intValue();
    }

    private static int page(Map<String, Object> res) {
        return ((Number) res.get("page")).intValue();
    }

    @Test
    @DisplayName("size=500 → 上限钳到 200（判据是 min(200)，不放行原值）")
    void sizeOver200_clampedTo200() {
        Map<String, Object> res = svc().listFacts("stage", null, 1, 500);

        assertEquals(200, pageSize(res), "size 超上限必须钳到 200");
        assertNotNull(res.get("rows"), "rows 应在响应体（未触库时为空 list 亦合法）");
    }

    @Test
    @DisplayName("size=0 与 size=-1 → 下限钳到 1（Math.max(1,..) 生效，不出现 LIMIT 0/负）")
    void sizeNonPositive_clampedToOne() {
        assertEquals(1, pageSize(svc().listFacts("stage", null, 1, 0)), "size=0 应钳到下限 1");
        assertEquals(1, pageSize(svc().listFacts("stage", null, 1, -5)), "size 负值应钳到下限 1");
    }

    @Test
    @DisplayName("恰 200 + page=3 → pageSize 保持 200（上限不误缩到 <200）且 page 原样透传")
    void sizeExactly200_andPage() {
        Map<String, Object> res = svc().listFacts("stage", null, 3, 200);

        assertEquals(200, pageSize(res), "恰 200 应原样保留，不应误缩");
        assertEquals(3, page(res), "page 应原样保留（page 下限 Math.max(1,..) 对 ≥1 值不变）");
    }

    @Test
    @DisplayName("缺省 size=20（controller @RequestParam default）→ pageSize=20，page=1（下限 1 生效）")
    void defaultSize20() {
        Map<String, Object> res = svc().listFacts("stage", null, 1, 20);

        assertEquals(20, pageSize(res), "缺省 20 应原样保留");
        assertEquals(1, page(res), "page 缺省 1");
    }

    @Test
    @DisplayName("page=0 / page=-2 → page 钳到下限 1（深层分页不回卷为 0/负 OFFSET 负值）")
    void pageNonPositive_clampedToOne() {
        assertEquals(1, page(svc().listFacts("stage", null, 0, 20)), "page=0 应钳到 1（OFFSET (1-1)*20=0，不为负）");
        assertEquals(1, page(svc().listFacts("stage", null, -2, 20)), "page 负值应钳到 1");
    }
}
