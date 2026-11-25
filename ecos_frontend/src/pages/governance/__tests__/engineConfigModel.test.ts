/**
 * engineConfigModel 单测 — 钉住「引擎配置」页的判定口径。
 *
 * 重点不是覆盖率，而是把 2026-11-25 live 实测暴露过的三类真缺陷固化成回归：
 *   1. 密码**策略**项（BOOLEAN/number）不得被键名启发式误判成密文打码；
 *   2. 真密钥（config_type=password，或 string 型 secret_key/access_key）必须脱敏；
 *   3. 缺 default_value 的行不得被判成「偏离默认」（否则 Δ 徽标全是假警报）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
// @vitest-environment node
// 纯函数测试不需要 DOM；jsdom 在本机 Node 20 下会因 @exodus/bytes 的 ESM-only 依赖启动失败。
import { describe, it, expect } from "vitest";
import {
  boolValue,
  computeStats,
  deltaState,
  groupConsumedCount,
  maskSecret,
  normalizeType,
  searchConfigs,
  sortGroups,
  sortItems,
  valueKind,
  type ConsumptionMap,
  type SysConfigGrouped,
  type SysConfigItem,
} from "../engineConfigModel";

function item(over: Partial<SysConfigItem> = {}): SysConfigItem {
  return {
    key: "k",
    value: "v",
    label: "Label",
    labelZh: "标签",
    description: "",
    descriptionZh: "",
    group: "global",
    type: "string",
    sort_order: 0,
    default_value: "",
    ...over,
  };
}

describe("valueKind — 控件类别判定", () => {
  it("密码策略项按类型定族，不被键名里的 password 误判为密文", () => {
    // live 实测：这三条曾被渲染成 •••••，语义全丢
    expect(valueKind(item({ key: "password_require_digit", type: "BOOLEAN", value: "true" }))).toBe("boolean");
    expect(valueKind(item({ key: "password_require_upper", type: "BOOLEAN", value: "true" }))).toBe("boolean");
    expect(valueKind(item({ key: "password_min_length", type: "number", value: "8" }))).toBe("number");
    expect(valueKind(item({ key: "password_expire_days", type: "number", value: "90" }))).toBe("number");
  });

  it("显式 password 类型一律密文", () => {
    expect(valueKind(item({ key: "a.b", type: "password" }))).toBe("secret");
    expect(valueKind(item({ key: "a.b", type: "PASSWORD" }))).toBe("secret");
  });

  it("类型说不清时靠键名兜底：secret_key / access_key 脱敏，minio 不误伤", () => {
    expect(valueKind(item({ key: "dw.storage.minio.secret_key", type: "string" }))).toBe("secret");
    expect(valueKind(item({ key: "dw.storage.minio.access_key", type: "string" }))).toBe("secret");
    // "min" 若被当成策略词，会把 minio 相关项全判成 text；这里钉住整词匹配
    expect(valueKind(item({ key: "dw.storage.minio.endpoint", type: "string" }))).toBe("text");
  });

  it("枚举/整型/浮点/未知类型归族", () => {
    expect(valueKind(item({ type: "enum" }))).toBe("enum");
    expect(valueKind(item({ type: "INTEGER" }))).toBe("number");
    expect(valueKind(item({ type: "float" }))).toBe("number");
    expect(valueKind(item({ type: "whatever" }))).toBe("text");
  });
});

describe("normalizeType / boolValue", () => {
  it("类型大小写与空白归一", () => {
    expect(normalizeType(" BOOLEAN ")).toBe("boolean");
    expect(normalizeType(undefined)).toBe("");
  });

  it("布尔值只认真值串", () => {
    expect(boolValue("true")).toBe(true);
    expect(boolValue("TRUE")).toBe(true);
    expect(boolValue("1")).toBe(true);
    expect(boolValue("false")).toBe(false);
    expect(boolValue("")).toBe(false);
    expect(boolValue("maybe")).toBe(false);
  });
});

describe("maskSecret", () => {
  it("空值返回 null（调用方显示「未设置」）", () => {
    expect(maskSecret("")).toBeNull();
  });

  it("短值整体打码，长度本身不泄露", () => {
    expect(maskSecret("abc")).toBe("•••");
    expect(maskSecret("abcdef")).toBe("••••••");
  });

  it("长值留首尾各两位，中间打码且总长受限", () => {
    const masked = maskSecret("minioadmin")!;
    expect(masked.startsWith("mi")).toBe(true);
    expect(masked.endsWith("in")).toBe(true);
    expect(masked).toContain("•");
    expect(masked).not.toContain("nioadmi");
  });

  it("密文项的默认值同样要脱敏（live 实测 default_value=minioadmin 曾明文外露）", () => {
    // 页面渲染默认值时复用同一判定：secret 类必须经过 maskSecret 才能展示
    const secretItem = item({ key: "dw.storage.minio.secret_key", type: "password", default_value: "minioadmin" });
    expect(valueKind(secretItem)).toBe("secret");
    expect(maskSecret(secretItem.default_value!)).not.toContain("minioadmin");

    // 非密文项默认值保持原样（不能让脱敏污染普通配置）
    const plainItem = item({ key: "dw.datasource.page_size", type: "int", default_value: "20" });
    expect(valueKind(plainItem)).not.toBe("secret");
  });
});

describe("deltaState — 与后端默认值的偏离", () => {
  it("后端没给默认值 → none，不臆断为已修改", () => {
    expect(deltaState(item({ default_value: "", value: "x" }))).toBe("none");
    expect(deltaState(item({ default_value: undefined, value: "x" }))).toBe("none");
  });

  it("值与默认相同 → none；不同 → override", () => {
    expect(deltaState(item({ default_value: "20", value: "20" }))).toBe("none");
    expect(deltaState(item({ default_value: "deepseek-chat", value: "DeepSeek-V4.1" }))).toBe("override");
  });

  it("有默认值但当前值为空 → empty（缺值优先于偏离）", () => {
    expect(deltaState(item({ default_value: "20", value: "" }))).toBe("empty");
  });
});

describe("排序与搜索", () => {
  const groups: SysConfigGrouped[] = [
    { group: "wagent.budget", items: [item({ key: "wagent.budget.max_steps", sort_order: 2 })] },
    { group: "global", items: [item({ key: "g2", sort_order: 2 }), item({ key: "g1", sort_order: 1 })] },
    { group: "data-engine", items: [item({ key: "dw.a", sort_order: 1 })] },
  ];

  it("已知分组按业务权重在前，其余按字母序", () => {
    expect(sortGroups(groups).map((g) => g.group)).toEqual(["global", "data-engine", "wagent.budget"]);
  });

  it("组内按 sort_order 再按 key 稳定排序", () => {
    expect(sortItems(groups[1].items).map((i) => i.key)).toEqual(["g1", "g2"]);
  });

  it("关键字命中 key/中文标签/说明/值任一，空组剔除", () => {
    const out = searchConfigs(groups, "dw.");
    expect(out.groups).toHaveLength(1);
    expect(out.groups[0].group).toBe("data-engine");
    expect(out.matchedItems).toBe(1);

    const byLabel = searchConfigs([{ group: "g", items: [item({ key: "z", labelZh: "会话 token 上限" })] }], "token");
    expect(byLabel.matchedItems).toBe(1);
  });

  it("空关键字返回原集合与全量计数", () => {
    const out = searchConfigs(groups, "   ");
    expect(out.groups).toBe(groups);
    expect(out.matchedItems).toBe(4);
  });
});

describe("统计", () => {
  const groups: SysConfigGrouped[] = [
    {
      group: "security",
      items: [
        item({ key: "a", default_value: "1", value: "2" }), // override + consumed
        item({ key: "b", default_value: "1", value: "1" }), // 与默认一致
        item({ key: "c" }), // 无默认值
      ],
    },
  ];
  const consumption: ConsumptionMap = { a: { consumed: true, consumedBy: ["AuthServiceImpl"] } };

  it("四数来自真实数据，不猜", () => {
    expect(computeStats(groups, consumption)).toEqual({ items: 3, groups: 1, consumed: 1, overrides: 1 });
  });

  it("组消费数按 key 精确匹配", () => {
    expect(groupConsumedCount(groups[0].items, consumption)).toBe(1);
    expect(groupConsumedCount(groups[0].items, {})).toBe(0);
  });
});
