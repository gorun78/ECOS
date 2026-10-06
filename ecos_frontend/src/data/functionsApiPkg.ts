/**
 * 函数模板 import 包名常量（业务代码示例，非真实依赖）。
 *
 * 各域显示模板里出现 `import { Function } from "@xxx/functions-api";`；
 * 限 iki namespace 避免多处 hardcode 之间漂移（见 前端检视报告-2026-10-06.md
 * §2.3 [P2] OntologyWorkbenchLayout:348 vs FunctionTypeView:104）。
 */

export const ONTOLOGY_FUNCTIONS_API_PKG = '@ecos/functions-api';
export const BUSINESS_FUNCTIONS_API_PKG = '@foundry/functions-api';
