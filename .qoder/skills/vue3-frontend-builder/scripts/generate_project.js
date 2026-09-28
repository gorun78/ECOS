#!/usr/bin/env node
/**
 * generate_project.js
 * 基于分析结果生成 Vue3 项目基础结构
 */

import { mkdirSync, writeFileSync, existsSync } from 'fs'
import { join } from 'path'

const projectName = process.argv[2] || 'vue3-frontend'

const structure = {
  'src/components/atoms': ['BaseButton.vue', 'BaseInput.vue', 'BaseIcon.vue', 'BaseBadge.vue', 'BaseTag.vue'],
  'src/components/molecules': ['SearchBar.vue', 'DataCard.vue', 'StatusTag.vue'],
  'src/components/organisms': ['AppHeader.vue', 'DataTable.vue', 'FilterPanel.vue'],
  'src/components/pages': ['HomePage.vue', 'DashboardPage.vue'],
  'src/composables': ['usePagination.ts', 'useDebounce.ts', 'useFetch.ts'],
  'src/stores': ['useUserStore.ts', 'useAppStore.ts'],
  'src/router': ['index.ts'],
  'src/types': ['components.ts', 'api.ts'],
  'src/utils': ['formatDate.ts', 'validation.ts'],
  'src/assets': ['styles/main.css'],
  'public': []
}

const packageJson = {
  name: projectName,
  version: '0.1.0',
  type: 'module',
  scripts: {
    dev: 'vite',
    build: 'vue-tsc && vite build',
    preview: 'vite preview',
    typecheck: 'vue-tsc --noEmit',
    lint: 'eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx --fix',
    test: 'vitest'
  },
  dependencies: {
    vue: '^3.4.0',
    'vue-router': '^4.2.0',
    pinia: '^2.1.0'
  },
  devDependencies: {
    '@vitejs/plugin-vue': '^5.0.0',
    '@vue/tsconfig': '^0.5.0',
    'autoprefixer': '^10.4.0',
    'postcss': '^8.4.0',
    'tailwindcss': '^3.4.0',
    'typescript': '^5.3.0',
    'vite': '^5.0.0',
    'vitest': '^1.0.0',
    'vue-tsc': '^1.8.0'
  }
}

const viteConfig = `import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
})
`

const tailwindConfig = `/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {}
  },
  plugins: []
}
`

const tsConfig = {
  compilerOptions: {
    target: 'ES2020',
    useDefineForClassFields: true,
    module: 'ESNext',
    lib: ['ES2020', 'DOM', 'DOM.Iterable'],
    skipLibCheck: true,
    moduleResolution: 'bundler',
    allowImportingTsExtensions: true,
    resolveJsonModule: true,
    isolatedModules: true,
    noEmit: true,
    jsx: 'preserve',
    strict: true,
    noUnusedLocals: true,
    noUnusedParameters: true,
    noFallthroughCasesInSwitch: true,
    paths: {
      '@/*': ['./src/*']
    }
  },
  include: ['src/**/*.ts', 'src/**/*.tsx', 'src/**/*.vue'],
  references: [{ path: './tsconfig.node.json' }]
}

const mainCss = `@tailwind base;
@tailwind components;
@tailwind utilities;
`

// 生成项目
function generateProject() {
  console.log('Generating Vue3 project structure...')

  // 创建目录结构
  for (const [dir, files] of Object.entries(structure)) {
    const fullDir = join(process.cwd(), dir)
    mkdirSync(fullDir, { recursive: true })
    console.log('Created:', fullDir)

    files.forEach(file => {
      const filePath = join(fullDir, file)
      if (!existsSync(filePath)) {
        if (file.endsWith('.vue')) {
          writeFileSync(filePath, `<template>
  <div class="${file.replace('.vue', '').replace(/([A-Z])/g, '-$1').toLowerCase().slice(1)}">
    <!-- Component content -->
  </div>
</template>

<script setup lang="ts">
// Component logic
</script>

<style scoped>
</style>
`)
        } else if (file.endsWith('.ts')) {
          writeFileSync(filePath, '// TypeScript file\n')
        } else if (file === 'main.css') {
          writeFileSync(filePath, mainCss)
        }
        console.log('  Created:', file)
      }
    })
  }

  // 创建配置文件
  writeFileSync('package.json', JSON.stringify(packageJson, null, 2))
  console.log('Created: package.json')

  writeFileSync('vite.config.ts', viteConfig)
  console.log('Created: vite.config.ts')

  writeFileSync('tailwind.config.js', tailwindConfig)
  console.log('Created: tailwind.config.js')

  writeFileSync('tsconfig.json', JSON.stringify(tsConfig, null, 2))
  console.log('Created: tsconfig.json')

  writeFileSync('tsconfig.node.json', JSON.stringify({
    compilerOptions: {
      composite: true,
      module: 'ESNext',
      moduleResolution: 'bundler',
      allowSyntheticDefaultImports: true
    },
    include: ['vite.config.ts']
  }, null, 2))
  console.log('Created: tsconfig.node.json')

  console.log('\nProject generated successfully!')
  console.log('\nNext steps:')
  console.log('  1. npm install')
  console.log('  2. npm run dev')
}

generateProject()