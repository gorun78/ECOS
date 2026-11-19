-- ============================================================================
-- V356 — 修正 llm 组两键的播种值（对齐运行时真实缺省）
-- ============================================================================
-- 背景（2026-11-19 G-2 接线时实测发现）：
--   V354 播种 `llm.default-temperature = 0.7` 与 `llm.max-context-tokens = 8192`，
--   依据是 `LLMGatewayProperties` 的字段缺省。但**该二键在运行时的真实消费方是
--   `AgentConfigResolver`**（L1 全局默认层），其 `@Value` 声明为：
--     `${llm.default-temperature:0.3}` / `${llm.max-context-tokens:8000}`
--   ⇒ 播种值与真实缺省不符（0.7≠0.3、8192≠8000）。
--
-- 为什么必须修：
--   接线（V354 头注「模板」段的桥接形态）落地后，AgentConfigResolver 会优先读本表。
--   若不修正，**所有未显式配置 temperature / maxContextTokens 的 agent 行为都会改变**
--   （温度 0.3→0.7 变随机、上下文 8000→8192 变宽）——借"配置化"名义偷偷改行为，
--   违反「播种后语义不变」的保守基线。
--
-- 修法（只改本次播种的值，不动运维后续可能的调整）：
--   仅当当前值**仍等于 V354 的原始播种值**时才改（`config_value = '0.7'` / `'8192'`），
--   运维若已自行改过则保持不动。`default_value` 同步订正（reset 基线）。
--
-- 幂等：两次 UPDATE 各自带值守卫，重跑无副作用。
-- 纪律：IR03「只加不删」——本脚本只改 `config_value`/`default_value`，不改结构。
--      不用 `ON CONFLICT`；不改其它组、不改其它键。
-- ============================================================================

UPDATE sys_config
   SET config_value = '0.3', default_value = '0.3', updated_at = NOW()
 WHERE config_key = 'llm.default-temperature'
   AND config_value = '0.7';

UPDATE sys_config
   SET config_value = '8000', default_value = '8000', updated_at = NOW()
 WHERE config_key = 'llm.max-context-tokens'
   AND config_value = '8192';
