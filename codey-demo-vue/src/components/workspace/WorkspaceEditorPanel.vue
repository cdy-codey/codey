<script setup>
import { computed, ref } from 'vue'
import CodeEditor from './CodeEditor.vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  activeFileKey: {
    type: String,
    default: '',
  },
  loadingFile: {
    type: Boolean,
    default: false,
  },
  loadingWorkspace: {
    type: Boolean,
    default: false,
  },
  savingFile: {
    type: Boolean,
    default: false,
  },
  activeTab: {
    type: String,
    default: 'code',
  },
})

const emit = defineEmits(['update:modelValue', 'update:activeTab', 'refresh', 'save'])

const codeEditorRef = ref(null)

const activeTabModel = computed({
  get: () => props.activeTab,
  set: (value) => emit('update:activeTab', value),
})

const isHtmlPreviewFile = computed(() => /\.(html?|svg)$/i.test(props.activeFileKey))
const isVuePreviewFile = computed(() => /\.vue$/i.test(props.activeFileKey))
const supportsSourcePreview = computed(() => isHtmlPreviewFile.value || isVuePreviewFile.value)
const editorLanguage = computed(() => {
  const fileKey = props.activeFileKey || ''
  if (/\.vue$/i.test(fileKey)) {
    return 'vue'
  }
  if (/\.(html?|svg)$/i.test(fileKey)) {
    return 'html'
  }
  if (/\.css$/i.test(fileKey)) {
    return 'css'
  }
  if (/\.json$/i.test(fileKey)) {
    return 'json'
  }
  if (/\.md$/i.test(fileKey)) {
    return 'markdown'
  }
  if (/\.tsx?$/i.test(fileKey)) {
    return 'typescript'
  }
  if (/\.(mjs|cjs|jsx|js)$/i.test(fileKey)) {
    return 'javascript'
  }
  return 'plaintext'
})
const previewDocument = computed(() => {
  if (isHtmlPreviewFile.value) {
    return buildHtmlPreviewDocument(props.modelValue)
  }
  if (isVuePreviewFile.value) {
    return buildVuePreviewDocument(props.modelValue, props.activeFileKey || 'App.vue')
  }
  return ''
})

function serializeForScript(value) {
  return JSON.stringify(value || '').replace(/<\//g, '<\\/')
}

function buildHtmlPreviewDocument(source) {
  if (/^\s*<!doctype/i.test(source) || /^\s*<html[\s>]/i.test(source)) {
    return source
  }
  return `<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <style>
      html, body {
        margin: 0;
        padding: 0;
      }
    </style>
  </head>
  <body>
${source || ''}
  </body>
</html>`
}

// 在浏览器内即时编译 Vue 单文件组件，保证预览反映当前编辑器源码。
function buildVuePreviewDocument(source, filename = 'App.vue') {
  const serializedSource = serializeForScript(source)
  const serializedFileName = serializeForScript(filename)
  return `<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <style>
      html, body, #app {
        margin: 0;
        min-height: 100%;
      }

      body {
        font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        background: #ffffff;
        color: #1f2329;
      }

      .preview-error {
        box-sizing: border-box;
        margin: 0;
        padding: 16px;
        color: #c45656;
        white-space: pre-wrap;
        line-height: 1.6;
        background: #fef0f0;
      }
    </style>
  </head>
  <body>
    <div id="app"></div>
    <script type="module">
      import * as Vue from '/node_modules/vue/dist/vue.esm-browser.js'
      import {
        compileScript,
        compileStyle,
        compileTemplate,
        parse,
      } from '/node_modules/@vue/compiler-sfc/dist/compiler-sfc.esm-browser.js'

      const source = ${serializedSource}
      const filename = ${serializedFileName}
      const scopeToken = 'preview-sfc'
      const appRoot = document.getElementById('app')

      function escapeHtml(value) {
        return String(value || '')
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
      }

      function formatCompilerError(error) {
        if (typeof error === 'string') {
          return error
        }
        return error?.message || JSON.stringify(error, null, 2)
      }

      function renderError(message) {
        appRoot.innerHTML = '<pre class="preview-error">' + escapeHtml(message) + '</pre>'
      }

      function replaceVueImports(code) {
        return code
          .replace(/import\\s+\\*\\s+as\\s+(\\w+)\\s+from\\s+['"]vue['"];?/g, 'const $1 = Vue;')
          .replace(/import\\s+\\{([^}]+)\\}\\s+from\\s+['"]vue['"];?/g, 'const {$1} = Vue;')
          .replace(/import\\s+(\\w+)\\s+from\\s+['"]vue['"];?/g, 'const $1 = Vue.default || Vue;')
      }

      try {
        const parsed = parse(source, { filename })
        if (parsed.errors.length) {
          throw new Error(parsed.errors.map(formatCompilerError).join('\\n\\n'))
        }

        const descriptor = parsed.descriptor
        const hasScopedStyle = descriptor.styles.some((style) => style.scoped)
        descriptor.styles.forEach((styleBlock, index) => {
          const styleResult = compileStyle({
            source: styleBlock.content,
            filename: filename + '?style=' + index,
            id: 'data-v-' + scopeToken,
            scoped: styleBlock.scoped,
          })
          if (styleResult.errors.length) {
            throw new Error(styleResult.errors.map(formatCompilerError).join('\\n\\n'))
          }
          const styleElement = document.createElement('style')
          styleElement.textContent = styleResult.code
          document.head.appendChild(styleElement)
        })

        let bindingMetadata
        let scriptCode = 'const __sfc__ = {}'
        if (descriptor.script || descriptor.scriptSetup) {
          const compiledScript = compileScript(descriptor, {
            id: scopeToken,
            genDefaultAs: '__sfc__',
          })
          bindingMetadata = compiledScript.bindings
          scriptCode = replaceVueImports(compiledScript.content)
          if (/^\\s*import\\s/m.test(scriptCode)) {
            throw new Error('当前 Vue 预览暂不支持解析本地 import 依赖，请先把预览所需内容写入当前单文件组件。')
          }
        }

        if (!descriptor.template?.content) {
          throw new Error('当前 Vue 文件没有 template，无法生成界面预览。')
        }

        const compiledTemplate = compileTemplate({
          source: descriptor.template.content,
          filename,
          id: scopeToken,
          scoped: hasScopedStyle,
          compilerOptions: {
            bindingMetadata,
          },
        })
        if (compiledTemplate.errors.length) {
          throw new Error(compiledTemplate.errors.map(formatCompilerError).join('\\n\\n'))
        }

        const templateCode = replaceVueImports(compiledTemplate.code)
          .replace(/export\\s+function\\s+render/, 'function render')
        if (/^\\s*import\\s/m.test(templateCode)) {
          throw new Error('当前模板预览包含暂不支持的外部依赖。')
        }

        const factory = new Function(
          'Vue',
          scriptCode + '\\n' +
          templateCode + '\\n' +
          (hasScopedStyle ? "__sfc__.__scopeId = 'data-v-preview-sfc';\\n" : '') +
          '__sfc__.render = render;\\n' +
          'return __sfc__;'
        )

        const component = factory(Vue)
        Vue.createApp(component).mount(appRoot)
      } catch (error) {
        renderError(error?.message || String(error))
      }
    <\/script>
  </body>
</html>`
}

function getValue() {
  return typeof codeEditorRef.value?.getValue === 'function'
    ? codeEditorRef.value.getValue()
    : props.modelValue
}

defineExpose({
  getValue,
})
</script>

<template>
  <el-card shadow="never" class="editor-card">
    <div class="editor-card-body">
      <el-tabs v-model="activeTabModel" class="main-tabs">
        <el-tab-pane label="代码编辑" name="code">
          <div class="editor-tab-panel">
            <CodeEditor
              ref="codeEditorRef"
              :model-value="modelValue"
              class="editor-input"
              :language="editorLanguage"
              :readonly="loadingFile"
              placeholder="请选择工作目录中的文件"
              @update:model-value="emit('update:modelValue', $event)"
            />
            <div class="editor-footer">
              <div class="editor-actions">
                <el-button :loading="loadingWorkspace" @click="emit('refresh')">刷新目录</el-button>
                <el-button
                  type="primary"
                  :loading="savingFile"
                  :disabled="!activeFileKey"
                  @click="emit('save')"
                >
                  保存文件
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="页面预览" name="preview">
          <div class="preview-panel">
            <iframe
              v-if="supportsSourcePreview"
              class="preview-frame"
              :srcdoc="previewDocument"
              title="source-preview"
            />
            <el-empty v-else description="当前仅支持预览 HTML、SVG、Vue 单文件组件源码" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-card>
</template>

<style scoped>
.editor-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
}

.editor-card-body {
  display: flex;
  align-items: stretch;
  height: 100%;
  overflow: hidden;
  position: relative;
}

.main-tabs {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

.main-tabs :deep(.el-tabs__header) {
  flex: 0 0 auto;
  margin-bottom: 8px;
}

.main-tabs :deep(.el-tabs__content) {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.main-tabs :deep(.el-tab-pane) {
  display: flex;
  flex: 1;
  min-height: 0;
}

.editor-tab-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 6px;
}

.editor-input {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.editor-footer {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.editor-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.preview-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.preview-frame {
  width: 100%;
  flex: 1;
  min-height: 0;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #ffffff;
}

.editor-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.editor-input :deep(.cm-scroller) {
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc transparent;
}

.editor-input :deep(.cm-scroller)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.editor-input :deep(.cm-scroller)::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 999px;
}

.editor-input :deep(.cm-scroller)::-webkit-scrollbar-track {
  background: transparent;
}
</style>
