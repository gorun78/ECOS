import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';

/**
 * ECOS 前端 ESLint 配置（治理建议 §三 第 3 件）。
 *
 * 定位：把「写代码时就能收到反馈」的规则前移到编辑器，与 vitest 检测用例并存。
 * vitest 用例（no-hardcoded-colors / i18n-coverage / file-size）是提交时防线，
 * 本配置是编辑期第一道提示——两者语义一致、不互相替代。
 *
 * 基线原则：**只收录当前代码库已满足的规则**，不引入大面积红。
 * 需要新增规则时，先修存量再开规则，禁止用 eslint-disable 换绿。
 */
export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'coverage/**',
      'src/__tests__/**', // 检测用例本身允许字面量与断言字符串
      '*.config.ts',
      '*.config.js',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}', 'server.ts'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
    },
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      // ── 前端开发规范 §四.2 图标系统：图标仅 lucide-react，禁其他图标库 ──
      'no-restricted-imports': [
        'error',
        {
          paths: [
            { name: 'react-icons', message: '图标仅允许 lucide-react（前端开发规范 §四.2）' },
            { name: '@heroicons/react', message: '图标仅允许 lucide-react（前端开发规范 §四.2）' },
            { name: '@ant-design/icons', message: '图标仅允许 lucide-react（前端开发规范 §四.2）' },
            { name: '@mui/icons-material', message: '图标仅允许 lucide-react（前端开发规范 §四.2）' },
          ],
          patterns: [
            {
              group: ['react-icons/*', '@heroicons/*', '@ant-design/icons/*'],
              message: '图标仅允许 lucide-react（前端开发规范 §四.2）',
            },
          ],
        },
      ],
      // ── 基础健壮性（当前代码已满足，防回归） ──
      eqeqeq: ['error', 'smart'],
      'no-var': 'error',
      'prefer-const': ['warn', { destructuring: 'all' }],
      'no-debugger': 'error',
      // TS 侧已由 tsc 把关的项降噪，避免与 tsc --noEmit 重复报错
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/ban-ts-comment': 'off',
      'no-empty': 'off',
      'no-unexpected-multiline': 'off',
      '@typescript-eslint/no-unused-expressions': 'off',
      '@typescript-eslint/no-require-imports': 'off',
      // ── 存量待清项：先关规则保留清单，修完存量再逐条开启（禁止为了换绿去改代码语义） ──
      // no-useless-escape ×12（存量正则转义冗余）、no-useless-catch ×4（存量包装 try/catch）、
      // @typescript-eslint/no-unsafe-declaration-merging ×2（declarations.d.ts 既有声明合并形态）
      'no-useless-escape': 'off',
      'no-useless-catch': 'off',
      '@typescript-eslint/no-unsafe-declaration-merging': 'off',
      // 项目未安装 eslint-plugin-react-hooks，存量代码里的 disable 注释会报"规则不存在"
      // exhaustive-deps：存量 46 处待清，先以 warn 提示不阻断（前端规范只冻结 hooks 语义，未要求零 deps 告警）
      'react-hooks/exhaustive-deps': 'warn',
      'react-hooks/rules-of-hooks': 'error',
      'reportUnusedDisableDirectives': 'off',
    },
  },
);
