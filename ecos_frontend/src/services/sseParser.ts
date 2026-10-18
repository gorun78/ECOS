/**
 * SSE 线路级增量解析器（前端单源）— 详设-06 §1.9.2 + 篇13 §13.4.4/§13.4.5。
 *
 * 只做「字节流 → 事件块」的线路级切分（`event:` / `data:` / `id:` / `retry:` / 注释行），
 * 不认识帧名、不做契约校验 —— 那部分单源在 `services/sseFrames.ts`。
 * 分层理由：页面侧此前各自手写 `indexOf('\n\n')` 切分（`pages/aiworkbench/api.ts`
 * 的 OAG 通道即其一），既不认 `\r\n`，也不处理跨 chunk 断包，属第二实现面（已登记 D-B5-11）。
 *
 * 纯函数、无 DOM/React 依赖，可直接单测。
 * @license Apache-2.0
 */

/** 一个已闭合的 SSE 事件块。 */
export interface SseRawEvent {
  /** `event:` 值；缺省按 SSE 规范为 "message" */
  event: string;
  /** 多行 `data:` 以 \n 连接后的原文（未 JSON.parse） */
  data: string;
  /** `id:` 值；后端未发 id 时为 null（⇒ 无续播凭据，禁伪造） */
  id: string | null;
  /** `retry:` 毫秒值；未下发为 null */
  retry: number | null;
}

interface FieldBag {
  event: string;
  dataLines: string[];
  id: string | null;
  retry: number | null;
}

function newBag(): FieldBag {
  return { event: "message", dataLines: [], id: null, retry: null };
}

export interface SseParser {
  /** 送入一段解码后的文本，返回本次闭合的事件块（可能为空） */
  push(chunk: string): SseRawEvent[];
  /**
   * 流结束。SSE 规范：未以空行闭合的最后一块一律丢弃（禁把半截正文当事件消费）。
   * 丢弃事实由 `discardedIncompleteTail` 暴露给调用方显式上报，不静默吞掉。
   */
  end(): void;
  /** `end()` 后为 true ⇒ 线路尾部有残块被丢弃 */
  readonly discardedIncompleteTail: boolean;
  /** 未闭合块的字节数（正常应为 0） */
  readonly pendingLength: number;
}

/**
 * 创建增量解析器。
 *
 * 换行处理：`\r\n` 与单 `\r` 都是行分隔符；chunk 结尾单个 `\r` 可能是 `\r\n` 的前半，
 * 必须留到下一 chunk 再判，否则会凭空多切一个空行。
 */
export function createSseParser(): SseParser {
  let buffer = "";
  let bag = newBag();
  let heldCR = false;
  let discarded = false;

  const dispatch = (out: SseRawEvent[]) => {
    // 规范：data 为空的事件不派发（含纯 event:/id:/retry: 块与心跳空行）
    if (bag.dataLines.length > 0) {
      out.push({
        event: bag.event,
        data: bag.dataLines.join("\n"),
        id: bag.id,
        retry: bag.retry,
      });
    }
    bag = newBag();
  };

  const consumeLine = (line: string, out: SseRawEvent[]) => {
    if (line === "") {
      dispatch(out);
      return;
    }
    if (line.startsWith(":")) return; // 注释行（心跳/防缓冲填充）
    const colon = line.indexOf(":");
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? "" : line.slice(colon + 1);
    // 规范：冒号后紧跟的单个空格属于分隔符，不属于值
    if (value.startsWith(" ")) value = value.slice(1);
    switch (field) {
      case "event":
        bag.event = value;
        break;
      case "data":
        bag.dataLines.push(value);
        break;
      case "id":
        // 规范：id 含 NUL 时整字段忽略
        if (!value.includes("\u0000")) bag.id = value;
        break;
      case "retry": {
        const ms = Number(value);
        if (Number.isInteger(ms) && ms >= 0) bag.retry = ms;
        break;
      }
      default:
        // 未知字段整行忽略（含后端未来加字段 ⇒ 不炸流、不冒充已知键）
        break;
    }
  };

  return {
    push(chunk: string): SseRawEvent[] {
      const out: SseRawEvent[] = [];
      let text = chunk;
      if (heldCR) {
        // 上一段以 \r 收尾：本段以 \n 开头则合成一个 \r\n 分隔符，否则该 \r 本身是分隔符
        text = (text.startsWith("\n") ? "" : "\r") + text;
        heldCR = false;
      }
      if (text.endsWith("\r")) {
        heldCR = true;
        text = text.slice(0, -1);
      }
      buffer += text;
      let start = 0;
      let i = 0;
      while (i < buffer.length) {
        const ch = buffer[i];
        if (ch !== "\n" && ch !== "\r") {
          i++;
          continue;
        }
        // 行内容 = [start, i)：分隔符本身不得入行，否则 CRLF 流会把帧名读成 "meta\r"
        consumeLine(buffer.slice(start, i), out);
        i += ch === "\r" && buffer[i + 1] === "\n" ? 2 : 1;
        start = i;
      }
      buffer = buffer.slice(start);
      return out;
    },
    end(): void {
      // 残留未闭合内容 ⇒ 按规范丢弃并留痕（残块可能是半截 JSON，绝不当事件消费）
      if (buffer.length > 0 || bag.dataLines.length > 0) discarded = true;
      buffer = "";
      bag = newBag();
    },
    get discardedIncompleteTail() {
      return discarded;
    },
    get pendingLength() {
      return buffer.length;
    },
  };
}

/**
 * `data:` 原文 → 帧载荷对象。
 *
 * 后端恒发 JSON（`MediaType.APPLICATION_JSON`）。解析失败/非对象 ⇒ 返回 null，
 * 由调用方按「契约外载荷」显式上报；禁把原文塞进正文通道冒充 delta。
 */
export function decodeFrameData(rawData: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(rawData);
    return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}
