import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

const demoRootPath = resolve(__dirname)
const chatWorkspacePackagePath = resolve(__dirname, '../codey-chat-workspace/index.js')
const chatWorkspaceRootPath = resolve(__dirname, '../codey-chat-workspace')

// https://vite.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      // 开发联调模式：demo 中使用包名，但实际指向本地源码目录，修改后无需重新打包安装。
      'codey-chat-workspace': chatWorkspacePackagePath,
      // 本地联调时，包源码位于 demo 外层目录，需要显式把 peer dependency 指回 demo 自己的 node_modules。
      vue: resolve(__dirname, 'node_modules/vue'),
      'vue-router': resolve(__dirname, 'node_modules/vue-router'),
      'element-plus': resolve(__dirname, 'node_modules/element-plus'),
      '@element-plus/icons-vue': resolve(__dirname, 'node_modules/@element-plus/icons-vue'),
    },
  },
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  server: {
    port: 5173,
    fs: {
      allow: [demoRootPath, chatWorkspaceRootPath],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true,
      },
    },
  },
})