/**
 * SSE 帧契约跨语言单源钉桩 — 前端 `services/sseFrames.ts` ⇄ 后端登记处。
 *
 * 钉三处源（缺一即漏）：
 * - `runtime/common-api .../common/stream/SseFrameContract.java`：七帧名、`AGENT_LOOP_FRAME_ORDER`、`KEY_*` 键面、`CITATION_REQUIRED_KEYS`；
 * - `runtime/common-api .../common/trace/AgentLoopTrace.java`：`Op` 的 wire 词表、`Decision` 词表、
 *   缺失标记 `UNKNOWN`/`ABSENT`；
 * - `engine/ai-engine/ai-engine-impl .../controller/AgentLoopController.java`：done 帧的**内联字面量**
 *   （`turns`/`success`/`errorClass`/`compressedTurns`/`compressSkipped`/`message` 不走 `KEY_*` 常量面，
 *   只扫 SseFrameContract 会漏掉这一族）。
 *
 * 两侧一旦分叉，表现是前端把「未上报」读成「实测为零」或整帧被当未知帧停消费 —— 静默失真而非报错，
 * 所以禁人工记忆，必须由本门在构建期读源码比对。
 * @license Apache-2.0
 */
// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  AGENT_LOOP_FRAME_ORDER,
  CITATION_REQUIRED_KEYS,
  SSE_KEYS,
  STEP_DECISIONS,
  STEP_OPS,
  TEXT_ABSENT,
  TOKEN_UNKNOWN,
} from "../sseFrames";

const BACKEND = new URL("../../../../ecos_backend/", import.meta.url);
const CONTRACT_SRC = readFileSync(
  fileURLToPath(new URL("runtime/common-api/src/main/java/com/chinacreator/gzcm/common/stream/SseFrameContract.java", BACKEND)),
  "utf8",
);
const TRACE_SRC = readFileSync(
  fileURLToPath(new URL("runtime/common-api/src/main/java/com/chinacreator/gzcm/common/trace/AgentLoopTrace.java", BACKEND)),
  "utf8",
);
const CONTROLLER_SRC = readFileSync(
  fileURLToPath(new URL("engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/AgentLoopController.java", BACKEND)),
  "utf8",
);

/** 剥掉行注释与块注释：javadoc 里出现的帧名/键名不得算数（否则注释即真值面）。 */
function codeOnly(src: string): string {
  return src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
}

/** `public static final String NAME = "value";` → NAME→value（声明序）。 */
function stringConstants(src: string): Map<string, string> {
  const out = new Map<string, string>();
  const re = /public static final String\s+(\w+)\s*=\s*"([^"]*)"\s*;/g;
  for (const m of codeOnly(src).matchAll(re)) out.set(m[1], m[2]);
  return out;
}

/** `Type NAME = List.of(A, B, C);`（可跨行）→ 解析成员后的值序列。 */
function listOfMembers(src: string, varName: string, consts: Map<string, string>): string[] {
  const re = new RegExp(`\\b${varName}\\s*=\\s*List\\.of\\(([\\s\\S]*?)\\)\\s*;`);
  const hit = codeOnly(src).match(re);
  if (!hit) throw new Error(`后端单源里找不到 List.of 声明: ${varName}（改名即断源，必须人工确认两侧口径）`);
  return hit[1]
    .split(",")
    .map((token) => token.trim())
    .filter((token) => token.length > 0)
    .map((token) => {
      const value = consts.get(token);
      if (!value) throw new Error(`${varName} 成员 ${token} 无对应 String 常量（发射侧写了裸字面量）`);
      return value;
    });
}

/** `public enum Op { PLAN("plan"), ... }` → wire 值序列。 */
function enumWires(src: string, enumName: string): string[] {
  const hit = codeOnly(src).match(new RegExp(`public enum ${enumName}\\s*\\{([\\s\\S]*?)\\}`));
  if (!hit) throw new Error(`后端单源里找不到 enum ${enumName}`);
  return [...hit[1].matchAll(/\w+\("([^"]*)"/g)].map((m) => m[1]);
}

/** `public enum Decision { ALLOW, DENY, FAIL_CLOSED, NA }` → 常量名序列。 */
function enumNames(src: string, enumName: string): string[] {
  const hit = codeOnly(src).match(new RegExp(`public enum ${enumName}\\s*\\{([\\s\\S]*?)\\}`));
  if (!hit) throw new Error(`后端单源里找不到 enum ${enumName}`);
  return hit[1]
    .split(",")
    .map((token) => token.trim())
    .filter((token) => /^[A-Z_][A-Z0-9_]*$/.test(token));
}

/** done 帧内联字面量键（`done.put("x", ...)`），KEY_* 常量面覆盖不到这一族。 */
function inlineDoneKeys(src: string): string[] {
  return [...codeOnly(src).matchAll(/\bdone\.put\("([^"]+)"\s*,/g)].map((m) => m[1]);
}

/**
 * `m.put(FRAME_X, List.of(KEY_A, KEY_B));`（可跨行）→ 帧名→键值序列（声明序）。
 *
 * W 八帧键面在后端是 `wagentFrameKeys()` 里逐帧 put 出来的，不是 `List.of` 顶层声明，
 * 所以 `listOfMembers` 吃不到它——另写提取器，并把「一条 put 都没抓到」当失败抛出。
 */
function wagentFrameKeysMap(src: string, consts: Map<string, string>): Map<string, string[]> {
  const body = codeOnly(src);
  const out = new Map<string, string[]>();
  for (const m of body.matchAll(/\bm\.put\((FRAME_\w+),\s*List\.of\(([\s\S]*?)\)\s*\)\s*;/g)) {
    const frame = consts.get(m[1]);
    if (!frame) throw new Error(`W 键面表帧名 ${m[1]} 无对应 String 常量`);
    out.set(
      frame,
      m[2]
        .split(",")
        .map((token) => token.trim())
        .filter((token) => token.length > 0)
        .map((token) => {
          const value = consts.get(token);
          if (!value) throw new Error(`W 键面 ${frame} 成员 ${token} 无对应 KEY_* 常量（发射侧写了裸字面量）`);
          return value;
        }),
    );
  }
  if (out.size === 0) throw new Error("后端 W 键面表里一条 m.put(FRAME_*, List.of(...)) 都没抓到（改名/改形即断源）");
  return out;
}

const contractConstants = stringConstants(CONTRACT_SRC);
const backendFrameOrder = listOfMembers(CONTRACT_SRC, "AGENT_LOOP_FRAME_ORDER", contractConstants);
const backendKeyValues = [...contractConstants.entries()]
  .filter(([name]) => name.startsWith("KEY_"))
  .map(([, value]) => value);
const backendCitationKeys = listOfMembers(CONTRACT_SRC, "CITATION_REQUIRED_KEYS", contractConstants);

/**
 * 后端键面声明按册分段：七帧面（§9.3.3）在前，W 八帧面（§10.3.6）在
 * `── WAgent run 流键面` 分段标记之后。
 *
 * 为什么必须分段而不是整文件扫：`SseFrameContract` 现在一文件两通道，
 * 「后端 KEY_* 必须全在前端 SSE_KEYS 里」这条 2026-10-18 之前为真的前提已失效
 * ——W 面独有键（runId/planSource/…）本就不属于 agent-loop 前端键面。
 * 分段把两条通道各自钉死（七帧面 ⊆ SSE_KEYS、W 面 = WAGENT_SSE_KEYS 同集），
 * 比把 W 键塞进 SSE_KEYS 凑绿、或把断言改成「部分命中」都更严。
 * 标记丢失即抛错，禁「读不到就当过」。
 */
function splitKeySections(src: string): { agentLoop: string; wagent: string } {
  const at = src.indexOf("── WAgent run 流键面");
  if (at < 0) throw new Error("后端 SseFrameContract 里找不到 W 键面分段标记（禁靠位置猜通道）");
  return { agentLoop: src.slice(0, at), wagent: src.slice(at) };
}

const keySections = splitKeySections(CONTRACT_SRC);
/** 只取按名声明在本段的 KEY_*（值可能与他段共用，如 traceId）。 */
function keyValuesIn(section: string): string[] {
  return [...stringConstants(section).entries()]
    .filter(([name]) => name.startsWith("KEY_"))
    .map(([, value]) => value);
}
const backendAgentLoopKeyValues = keyValuesIn(keySections.agentLoop);
const backendWagentDeclaredKeyValues = keyValuesIn(keySections.wagent);

// ── W 通道（§10.3.6 八帧）后端侧提取，供下方四门逐一比对 ──
const backendWagentOrder = listOfMembers(CONTRACT_SRC, "WAGENT_FRAME_ORDER", contractConstants);
const backendWagentFrameKeys = wagentFrameKeysMap(CONTRACT_SRC, contractConstants);
const backendWagentExtraKeys = listOfMembers(CONTRACT_SRC, "WAGENT_CHANNEL_EXTRA_KEYS", contractConstants);
const backendMilestoneKeys = listOfMembers(CONTRACT_SRC, "MILESTONE_FRAME_KEYS", contractConstants);
/** W 通道在册键值全集 = 八帧键面 ∪ 通道自加键 ∪ milestone 键面（前端 WAGENT_SSE_KEYS 必须与此同集）。 */
const backendWagentKeyUniverse = [
  ...new Set([
    ...[...backendWagentFrameKeys.values()].flat(),
    ...backendWagentExtraKeys,
    ...backendMilestoneKeys,
  ]),
];

describe("SSE 帧契约跨语言单源", () => {
  it("七帧名与序逐字相等", () => {
    expect(backendFrameOrder).toHaveLength(7);
    expect([...AGENT_LOOP_FRAME_ORDER]).toEqual(backendFrameOrder);
  });

  it("键面：后端七帧面 KEY_* 全部在前端单源里，前端不得有后端没有的键（done 内联族除外）", () => {
    const frontendValues: readonly string[] = Object.values(SSE_KEYS);
    expect(new Set(frontendValues).size).toBe(frontendValues.length); // 值不得重复（重复＝一个键面两种写法）
    const missing = backendAgentLoopKeyValues.filter((value) => !frontendValues.includes(value));
    expect(missing).toEqual([]);
    const doneExtras = [...inlineDoneKeys(CONTROLLER_SRC), "message"];
    const invented = frontendValues.filter(
      (value) => !backendKeyValues.includes(value) && !doneExtras.includes(value),
    );
    expect(invented).toEqual([]);
  });

  it("done 帧内联字面量键必须在前端键面里逐个在册", () => {
    const frontendValues: readonly string[] = Object.values(SSE_KEYS);
    const unbound = inlineDoneKeys(CONTROLLER_SRC).filter((key) => !frontendValues.includes(key));
    expect(unbound).toEqual([]);
    // 反向：前端登记为 done 专属的键，后端 doneFields 里必须真的在写（禁读一个没人发的键再显示缺失）
    const declaredExtras = ["turns", "success", "errorClass", "compressedTurns", "compressSkipped"];
    expect(inlineDoneKeys(CONTROLLER_SRC).sort()).toEqual(
      [...new Set([...inlineDoneKeys(CONTROLLER_SRC), ...declaredExtras])].sort(),
    );
    for (const key of declaredExtras) expect(inlineDoneKeys(CONTROLLER_SRC)).toContain(key);
  });

  it("引用帧四要素齐备口径两侧一致", () => {
    expect([...CITATION_REQUIRED_KEYS]).toEqual(backendCitationKeys);
  });

  it("step 帧 op / decision 词表与 AgentLoopTrace 枚举一致", () => {
    expect([...STEP_OPS]).toEqual(enumWires(TRACE_SRC, "Op"));
    expect([...STEP_DECISIONS]).toEqual(enumNames(TRACE_SRC, "Decision"));
  });

  it("缺失标记 -1 / \"-\" 与 AgentLoopTrace.UNKNOWN、ABSENT 同值", () => {
    const unknown = codeOnly(TRACE_SRC).match(/public static final long UNKNOWN\s*=\s*(-?\d+)/);
    const absent = codeOnly(TRACE_SRC).match(/public static final String ABSENT\s*=\s*"([^"]*)"/);
    expect(unknown).not.toBeNull();
    expect(absent).not.toBeNull();
    expect(TOKEN_UNKNOWN).toBe(Number(unknown?.[1]));
    expect(TEXT_ABSENT).toBe(absent?.[1]);
  });

  it("钉桩自身能变红：后端多出帧名时提取器必须报出 8 帧而非默认折叠", () => {
    // 单源门禁若只会「读不到就当过」，等于没有门禁。这里喂一份伪 Java 源验证提取器会跟着变。
    const mutated = CONTRACT_SRC.replace(
      'FRAME_META, FRAME_STEP, FRAME_TOKEN, FRAME_DELTA, FRAME_CITATION, FRAME_ERROR, FRAME_DONE',
      'FRAME_META, FRAME_STEP, FRAME_TOKEN, FRAME_DELTA, FRAME_CITATION, FRAME_ERROR, FRAME_PING, FRAME_DONE',
    ).replace('public static final String FRAME_DONE = "done";', 'public static final String FRAME_DONE = "done";\n    public static final String FRAME_PING = "ping";');
    const mutatedConsts = stringConstants(mutated);
    const mutatedOrder = listOfMembers(mutated, "AGENT_LOOP_FRAME_ORDER", mutatedConsts);
    expect(mutatedOrder).toHaveLength(8);
    expect(mutatedOrder).not.toEqual([...AGENT_LOOP_FRAME_ORDER]);
  });
});
