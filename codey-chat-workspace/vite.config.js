import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// 库模式构建：产出 ESM + CJS，业务方按需引入。
export default defineConfig({
  plugins: [vue()],
  build: {
    lib: {
      entry: resolve(__dirname, 'index.js'),
      name: 'CodeyChatWorkspace',
      formats: ['es', 'cjs'],
      fileName: (format) => `codey-chat-workspace.${format === 'es' ? 'esm' : 'cjs'}.js`,
    },
    rollupOptions: {
      // 不打包 vue / element-plus，由使用者项目提供
      external: ['vue', 'element-plus', '@element-plus/icons-vue', 'vue-router'],
      output: {
        globals: {
          vue: 'Vue',
          'element-plus': 'ElementPlus',
          '@element-plus/icons-vue': 'ElementPlusIconsVue',
          'vue-router': 'VueRouter',
        },
      },
    },
  },
})
