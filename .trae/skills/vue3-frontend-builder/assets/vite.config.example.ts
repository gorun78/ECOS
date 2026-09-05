/**
 * Vite 配置示例
 *
 * 用法：复制到项目根目录为 vite.config.ts
 * 确保根目录有 index.html
 */

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue({
      // Vue 编译器选项
      script: {
        // 开启 defineModel 自动解包
        defineModel: true,
        // 开启 Props 验证
        propsDestructure: true
      }
    })
  ],

  // 路径别名
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  // 开发服务器
  server: {
    port: 5173,
    host: true,
    open: false,

    // 代理配置（开发环境 API 转发）
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },

  // 构建配置
  build: {
    target: 'esnext',
    minify: 'esbuild',
    sourcemap: false,

    // 拆包配置
    rollupOptions: {
      output: {
        // 手动分包策略
        manualChunks: {
          // Vue 运行时
          'vue-runtime': ['vue', 'vue-router', 'pinia'],
          // 图标库（如果使用 Lucide）
          'icons': ['lucide-vue-next']
        }
      }
    }
  },

  // CSS 配置
  css: {
    // CSS Modules 配置
    modules: {
      localsConvention: 'camelCase',
      generateScopedName: '[name]__[local]___[hash:base64:5]'
    },

    // CSS 预处理器配置
    preprocessorOptions: {
      scss: {
        // 全局 SCSS 变量
        additionalData: `@import "@/styles/variables.scss";`
      }
    }
  },

  // JSON 解析
  json: {
    stringify: true
  },

  // 依赖优化
  optimizeDeps: {
    include: ['vue', 'vue-router', 'pinia']
  }
})

/**
 * 备选：使用路径别名的 tsconfig 引用
 *
 * import { defineConfig } from 'vite'
 * import vue from '@vitejs/plugin-vue'
 * import { resolve } from 'path'
 *
 * export default defineConfig({
 *   plugins: [vue()],
 *   resolve: {
 *     alias: {
 *       '@': resolve(__dirname, 'src')
 *     }
 *   }
 * })
 *
 * 备选：使用环境变量
 *
 * import { defineConfig, loadEnv } from 'vite'
 *
 * export default defineConfig(({ mode }) => {
 *   const env = loadEnv(mode, process.cwd(), '')
 *
 *   return {
 *     plugins: [vue()],
 *     define: {
 *       'import.meta.env.VITE_API_BASE': JSON.stringify(env.VITE_API_BASE)
 *     }
 *   }
 * })
 */