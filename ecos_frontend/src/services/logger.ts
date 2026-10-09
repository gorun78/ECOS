/**
 * logger — 场景子树统一日志出口（详细设计-07 F07-23-3）。
 *
 * <p>替换散落的 {@code console.*}（X-71 实测 6 处）：dev 走 {@code console}，
 * 生产默认静默 info/debug，仅 warn/error 落盘，便于 CI console-error=0 门禁
 * （REQ-WS-03 §3.3 T6）。禁业务代码直接调 {@code console}。</p>
 *
 * @license Apache-2.0
 */

const isDev = typeof process !== 'undefined' && process.env?.NODE_ENV === 'development';

export const logger = {
  debug(...args: unknown[]): void {
    if (isDev) console.debug(...args);
  },
  info(...args: unknown[]): void {
    if (isDev) console.info(...args);
  },
  warn(...args: unknown[]): void {
    console.warn(...args);
  },
  /** error 保留首个参数为稳定 tag，便于聚合；其余透传。 */
  error(tag: string, ...args: unknown[]): void {
    console.error(`[${tag}]`, ...args);
  },
};

export default logger;
