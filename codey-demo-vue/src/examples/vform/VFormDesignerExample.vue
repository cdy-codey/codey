<script setup>
import { nextTick, onActivated, onDeactivated, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { AiChatWorkspace } from 'codey-chat-workspace'
import { useAiAssistantHandlers } from '../../composables/useAiAssistantHandlers'
import { createWorkspaceJsonResource } from '../../composables/useWorkspaceJsonResource'

const { aiAssistantHandlers } = useAiAssistantHandlers()
// 低代码示例暂时没有独立文件树，这里将 AI 的工作范围固定到当前一级项目目录。
const aiProjectPath = 'vform'
const aiWorkingDirectory = './workspace/vform'
const aiIdentities = ['vform', 'programming']
const formConfigFileKey = 'table/vform-designer.form.json'
const formConfigWorkspacePath = `${aiProjectPath}/${formConfigFileKey}`
const formConfigDocumentId = `workspace:${formConfigFileKey}`

const formConfigResource = createWorkspaceJsonResource({
  workspacePath: formConfigWorkspacePath,
})
const designerRef = ref(null)
const loadingSchema = ref(false)
const savingSchema = ref(false)
const designerRenderKey = ref(0)
const designerVisible = ref(false)
const aiCollapsed = ref(false)

function handleAiCollapseChange(collapsed) {
  aiCollapsed.value = collapsed
}

const designerConfig = {
  languageMenu: true,
  externalLink: true,
  formTemplates: true,
  eventCollapse: true,
  widgetNameReadonly: false,
  clearDesignerButton: true,
  previewFormButton: true,
  importJsonButton: true,
  exportJsonButton: true,
  exportCodeButton: true,
  generateSFCButton: true,
  toolbarMaxWidth: 450,
  toolbarMinWidth: 300,
  presetCssCode: '',
  // 强制设计器初始化时跳过浏览器本地备份恢复。
  resetFormJson: true,
}
const emptyDesignerSchema = {
  widgetList: [],
  formConfig: {
    modelName: 'formData',
    refName: 'vForm',
    rulesName: 'rules',
    labelWidth: 80,
    labelPosition: 'left',
    size: '',
    labelAlign: 'label-left-align',
    cssCode: '',
    customClass: '',
    functions: '',
    layoutType: 'PC',
    jsonVersion: 3,
    onFormCreated: '',
    onFormMounted: '',
    onFormDataChange: '',
  },
}

function clearDesignerBrowserBackup() {
  if (typeof window === 'undefined') {
    return
  }
  // v-form-designer 内部固定使用这两个 key 备份设计内容。
  window.localStorage.removeItem('widget__list__backup')
  window.localStorage.removeItem('form__config__backup')
}

// v-form-designer 在不同版本里可能返回 JSON 字符串，也可能直接返回对象。
function normalizeDesignerSchema(schemaValue) {
  if (!schemaValue) {
    throw new Error('未获取到表单设计器配置')
  }
  if (typeof schemaValue === 'string') {
    return JSON.parse(schemaValue)
  }
  if (typeof schemaValue === 'object') {
    return schemaValue
  }
  throw new Error('表单设计器配置格式不支持')
}

function readDesignerSchemaObject() {
  const schemaValue = typeof designerRef.value?.getFormJson === 'function'
    ? designerRef.value.getFormJson()
    : ''
  return normalizeDesignerSchema(schemaValue)
}

function applyDesignerSchemaObject(schemaObject) {
  if (typeof designerRef.value?.setFormJson !== 'function') {
    throw new Error('当前表单设计器不支持配置回填')
  }
  designerRef.value.setFormJson(schemaObject)
}

function resetDesignerSchemaToEmpty() {
  // 后端返回空文件或未返回 currentFile 时，统一回退到最小可用 schema，避免界面保留旧状态。
  applyDesignerSchemaObject(emptyDesignerSchema)
}

async function loadDesignerSchema() {
  return loadDesignerSchemaWithOptions()
}

async function loadDesignerSchemaWithOptions(options = {}) {
  const showSuccessMessage = options.showSuccessMessage !== false
  const showEmptyMessage = options.showEmptyMessage !== false
  loadingSchema.value = true
  try {
    const schemaObject = await formConfigResource.load({
      fallback: emptyDesignerSchema,
      initializeWithFallbackOnMissing: true,
    })
    if (!schemaObject) {
      resetDesignerSchemaToEmpty()
      if (showEmptyMessage) {
        ElMessage.info('未发现已保存的表单配置，已使用空白设计器')
      }
      return
    }
    applyDesignerSchemaObject(schemaObject)
    if (showSuccessMessage) {
      ElMessage.success('表单配置已加载')
    }
  } catch (error) {
    ElMessage.error(error.message || '加载表单配置失败')
  } finally {
    loadingSchema.value = false
  }
}

async function saveDesignerSchema() {
  savingSchema.value = true
  try {
    const schemaObject = readDesignerSchemaObject()
    await formConfigResource.save(schemaObject)
    ElMessage.success('表单配置已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存表单配置失败')
  } finally {
    savingSchema.value = false
  }
}

async function reloadDesignerSchemaFromServer() {
  // 每次进入页面都先卸载旧设计器，再创建新实例，确保以后端状态为准。
  designerVisible.value = false
  clearDesignerBrowserBackup()
  designerRenderKey.value += 1
  await nextTick()
  designerVisible.value = true
  await nextTick()
  await loadDesignerSchema()
}

async function handleAiFinalSummary() {
  // 结束摘要只负责补充文本阶段联动，JSON 回读交给 onTaskEnded 统一处理。
}

async function handleBeforeAiSend({ prompt, syncPagePayloadToWorkspace }) {
  if (typeof syncPagePayloadToWorkspace !== 'function') {
    return prompt
  }
  // 发送前通过组件内置同步能力把设计器当前 JSON 写回工作区目标文件。
  const schemaObject = readDesignerSchemaObject()
  await syncPagePayloadToWorkspace(schemaObject)
  return prompt
}

async function handleAiTaskEnded(event) {
  const schemaObject = event?.data
    ? normalizeDesignerSchema(event.data)
    : null
  if (!schemaObject) {
    return
  }
  // 任务结束后直接消费 queryWorkspace 返回的 JSON 内容，避免再从快照 currentFile 提取。
  applyDesignerSchemaObject(schemaObject)
}

onActivated(async () => {
  await reloadDesignerSchemaFromServer()
})

onDeactivated(() => {
  // 离开页面时也清理一次，避免下次进入被本地缓存干扰。
  designerVisible.value = false
  clearDesignerBrowserBackup()
})

</script>

<template>
  <div class="designer-example">
    <div class="designer-shell">
      <el-card shadow="never" class="panel-card designer-card">
        <div class="designer-toolbar">
          <el-space wrap size="small">
            <el-button size="small" :loading="loadingSchema" @click="loadDesignerSchema">加载</el-button>
            <el-button size="small" type="primary" :loading="savingSchema" @click="saveDesignerSchema">保存</el-button>
          </el-space>
        </div>
        <div class="designer-stage">
          <v-form-designer
            v-if="designerVisible"
            :key="designerRenderKey"
            ref="designerRef"
            :designer-config="designerConfig"
            class="designer-host"
          />
        </div>
      </el-card>

      <div class="designer-assistant" :class="{ collapsed: aiCollapsed }">
        <AiChatWorkspace
          title="低代码 AI 助手"
          subtitle="对话范围限制在当前一级项目目录，可用于表单配置、页面说明和落地代码建议。"
          placeholder="例如：帮我设计一个包含姓名、手机号、部门、审批意见的审批表单"
          v-bind="aiAssistantHandlers"
          skill-name-value="vform-json-agent"
          :identities-value="aiIdentities"
          :workspace-id-value="aiProjectPath"
          :page-payload-file-key="formConfigFileKey"
          :current-file-key="formConfigFileKey"
          :current-document-id="formConfigDocumentId"
          :working-directory-value="aiWorkingDirectory"
          default-working-directory="./workspace/project"
          :compact-header="true"
          :load-history-on-mounted="true"
          :show-working-directory="false"
          :on-before-send="handleBeforeAiSend"
          :on-assistant-finished="handleAiFinalSummary"
          :on-task-ended="handleAiTaskEnded"
          :collapsible="true"
          height="100%"
          :min-height="0"
          @collapse-change="handleAiCollapseChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.designer-example {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.panel-card {
  border-radius: 8px;
}

.designer-shell {
  display: flex;
  align-items: stretch;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.designer-card {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.designer-card :deep(.el-card__body) {
  height: 100%;
  min-height: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.designer-stage {
  height: 100%;
  min-height: 0;
  background: #fff;
}

.designer-host {
  display: block;
  width: 100%;
  height: 100%;
}

.designer-assistant {
  width: 420px;
  flex: 0 0 420px;
  min-height: 0;
  transition: width 0.3s ease, flex-basis 0.3s ease;
}

.designer-assistant.collapsed {
  width: 48px;
  flex: 0 0 48px;
}

.designer-stage :deep(.v-form-designer-container) {
  height: 100%;
}

@media (max-width: 1400px) {
  .designer-shell {
    flex-direction: column;
  }

  .designer-assistant {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
