/**
 * PostCSS 配置示例
 *
 * 用法：复制到项目根目录为 postcss.config.js
 *
 * Tailwind CSS v3 + Autoprefixer 标准配置
 * Vite 项目通常开箱即用，此文件仅作为配置参考
 */

export default {
  plugins: {
    // Tailwind CSS 处理器
    // 必须在 Autoprefixer 之前
    tailwindcss: {},

    // 自动添加浏览器前缀
    // 根据 browserslist 配置自动添加 -webkit-, -moz- 等前缀
    autoprefixer: {}
  }
}

/**
 * 备选：使用 CSS @import 时的配置
 *
 * import postcss from 'postcss'
 * import tailwindcss from 'tailwindcss'
 * import autoprefixer from 'autoprefixer'
 * import nesting from 'tailwindcss/nesting'
 *
 * export default {
 *   plugins: [
 *     nesting,
 *     tailwindcss,
 *     autoprefixer
 *   ]
 * }
 *
 * 备选：使用 PostCSS 预设配置
 *
 * import preact from '@preact/preset-vite'
 *
 * export default {
 *   plugins: [
 *     preact()
 *   ]
 * }
 */