<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { AiChatWorkspace } from 'codey-chat-workspace'
import WorkspaceEditorPanel from '../../components/workspace/WorkspaceEditorPanel.vue'
import WorkspaceFileTree from '../../components/workspace/WorkspaceFileTree.vue'
import { useAiAssistantHandlers } from '../../composables/useAiAssistantHandlers'

const { aiAssistantHandlers, workspaceApi } = useAiAssistantHandlers()

const workspaceSnapshot = ref(null)
const workspaceEditorRef = ref(null)
const activeFileKey = ref('')
const editorContent = ref('')
const loadingWorkspace = ref(false)
const loadingFile = ref(false)
const savingFile = ref(false)
const creatingEntry = ref(false)
const deletingEntry = ref(false)
const activeMainTab = ref('code')

// 直接使用后端返回的原始数据，不做前端加工
const workspaceTree = computed(() => workspaceSnapshot.value?.entries || [])
const workspaceRoot = ref('')
const workspaceFileCount = computed(() => workspaceSnapshot.value?.fileCount || 0)
const effectiveWorkingDirectory = computed(() => {
  return 'code-source'
})
const aiCollapsed = ref(false)
const aiIdentities = ['programming', 'workspace']

function handleAiCollapseChange(collapsed) {
  aiCollapsed.value = collapsed
}

function collectFileNodes(entries = [], bucket = []) {
  entries.forEach((entry) => {
    if (entry.directory) {
      collectFileNodes(entry.children || [], bucket)
      return
    }
    bucket.push(entry)
  })
  return bucket
}

function findFileByPath(path, snapshot = workspaceSnapshot.value) {
  if (!path) {
    return null
  }
  return collectFileNodes(snapshot?.entries || []).find((item) => item.fileKey === path || item.path === path) || null
}

function resolveNextFile(snapshot) {
  const candidates = collectFileNodes(snapshot?.entries || [])
  return candidates[0] || null
}

function applyCurrentFile(file) {
  activeFileKey.value = file?.path || ''
  editorContent.value = file?.content || ''
}

function clearCurrentFile() {
  activeFileKey.value = ''
  editorContent.value = ''
}

function getLatestEditorContent() {
  return typeof workspaceEditorRef.value?.getValue === 'function'
    ? workspaceEditorRef.value.getValue()
    : editorContent.value
}

async function loadWorkspaceRoot(options = {}) {
  const {
    preferredPath = activeFileKey.value,
    successMessage = '',
    errorMessage = '加载工作目录失败',
  } = options
  loadingWorkspace.value = true
  try {
    const snapshot = await workspaceApi.query('code-source')
    await applySnapshot(snapshot, preferredPath)
    if (successMessage) {
      ElMessage.success(successMessage)
    }
  } catch (error) {
    ElMessage.error(error.message || errorMessage)
  } finally {
    loadingWorkspace.value = false
  }
}

async function openWorkspace() {
  await loadWorkspaceRoot({
    successMessage: '工作目录已加载',
    errorMessage: '加载工作目录失败',
  })
}

async function applySnapshot(snapshot, preferredPath = '') {
  workspaceSnapshot.value = snapshot
  workspaceRoot.value = snapshot?.rootPath || ''
  if (snapshot?.currentFile) {
    applyCurrentFile(snapshot.currentFile)
    return
  }
  const nextFile =
    findFileByPath(preferredPath, snapshot) ||
    resolveNextFile(snapshot)
  if (!nextFile) {
    clearCurrentFile()
    return
  }
  await loadWorkspaceFile(nextFile.fileKey || nextFile.path)
}

async function loadWorkspaceFile(fileOrPath) {
  const path = typeof fileOrPath === 'string'
    ? fileOrPath
    : (fileOrPath?.fileKey || fileOrPath?.path || '')
  if (!path) {
    return
  }
  loadingFile.value = true
  try {
    const snapshot = await workspaceApi.query(path)
    await applySnapshot(snapshot, path)
  } catch (error) {
    ElMessage.error(error.message || '读取文件失败')
  } finally {
    loadingFile.value = false
  }
}

async function saveWorkspaceFile() {
  if (!activeFileKey.value) {
    return
  }
  savingFile.value = true
  try {
    const latestEditorContent = getLatestEditorContent()
    editorContent.value = latestEditorContent
    const snapshot = await workspaceApi.push({
      path: activeFileKey.value,
      content: latestEditorContent,
    })
    await applySnapshot(snapshot, activeFileKey.value)
    ElMessage.success('文件已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存文件失败')
  } finally {
    savingFile.value = false
  }
}

async function refreshWorkspace(showMessage = true) {
  await loadWorkspaceRoot({
    preferredPath: activeFileKey.value,
    successMessage: showMessage ? '工作目录已刷新' : '',
    errorMessage: '刷新工作目录失败',
  })
}

async function createWorkspaceEntry(options = {}) {
  const directory = options.directory === true
  const parentFileKey = typeof options.parentFileKey === 'string'
    ? options.parentFileKey
    : (options.parentFileKey?.fileKey || options.parentFileKey?.path || '')

  let promptResult
  try {
    promptResult = await ElMessageBox.prompt(
      directory ? '请输入目录名称，例如 components' : '请输入文件名称，例如 App.vue',
      directory ? '新建目录' : '新建文件',
      {
        confirmButtonText: '创建',
        cancelButtonText: '取消',
        inputPlaceholder: directory ? 'new-directory' : 'NewFile.vue',
      }
    )
  } catch {
    return
  }

  const entryName = promptResult?.value?.trim()
  if (!entryName) {
    return
  }
  const basePath = parentFileKey || 'code-source'
  const path = `${basePath}/${entryName}`

  creatingEntry.value = true
  try {
    const snapshot = await workspaceApi.push({
      path,
      directory,
      content: directory ? undefined : '',
    })
    await applySnapshot(snapshot, directory ? activeFileKey.value : path)
    ElMessage.success(directory ? '目录已创建' : '文件已创建')
  } catch (error) {
    ElMessage.error(error.message || (directory ? '创建目录失败' : '创建文件失败'))
  } finally {
    creatingEntry.value = false
  }
}

async function createWorkspaceFile(parentPath = '') {
  await createWorkspaceEntry({ parentFileKey: parentPath, directory: false })
}

async function createWorkspaceDirectory(parentPath = '') {
  await createWorkspaceEntry({ parentFileKey: parentPath, directory: true })
}

async function renameWorkspaceEntry(node) {
  const sourcePath = node?.fileKey || node?.path || ''
  if (!sourcePath) {
    return
  }
  const currentName = node?.name || node?.label || ''

  let promptResult
  try {
    promptResult = await ElMessageBox.prompt(
      node.directory ? '请输入新的目录名称' : '请输入新的文件名称',
      node.directory ? '重命名目录' : '重命名文件',
      {
        confirmButtonText: '保存',
        cancelButtonText: '取消',
        inputValue: currentName,
      },
    )
  } catch {
    return
  }

  const nextName = promptResult?.value?.trim()
  if (!nextName || nextName === currentName) {
    return
  }

  creatingEntry.value = true
  try {
    const snapshot = await workspaceApi.push({
      path: sourcePath,
      newName: nextName,
    })
    await applySnapshot(snapshot, activeFileKey.value)
    ElMessage.success(node.directory ? '目录已重命名' : '文件已重命名')
  } catch (error) {
    ElMessage.error(error.message || (node.directory ? '目录重命名失败' : '文件重命名失败'))
  } finally {
    creatingEntry.value = false
  }
}

async function deleteCurrentEntry() {
  if (!activeFileKey.value) {
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除 ${activeFileKey.value} 吗？`, '删除文件', {
      type: 'warning',
      cancelButtonText: '取消',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }

  deletingEntry.value = true
  try {
    const snapshot = await workspaceApi.remove(activeFileKey.value)
    clearCurrentFile()
    await applySnapshot(snapshot, '')
    ElMessage.success('文件已删除')
  } catch (error) {
    ElMessage.error(error.message || '删除文件失败')
  } finally {
    deletingEntry.value = false
  }
}

async function handleAiTaskEnded(event) {
  const snapshot = event?.data
  if (!snapshot) {
    return
  }
  await applySnapshot(snapshot, activeFileKey.value)
  ElMessage.success('AI 本轮已结束，工作目录已刷新')
}

async function handleBeforeAiSend({ prompt, syncPagePayloadToWorkspace }) {
  if (!activeFileKey.value || typeof syncPagePayloadToWorkspace !== 'function') {
    return prompt
  }
  const latestEditorContent = getLatestEditorContent()
  await syncPagePayloadToWorkspace(latestEditorContent)
  return prompt
}

function handleTreeNodeClick(node) {
  if (!node) {
    return
  }
  if (node.directory) {
    return
  }
  loadWorkspaceFile(node?.fileKey || node?.path || node)
}

onMounted(async () => {
  await openWorkspace()
})
</script>

<template>
  <el-config-provider>
    <div class="workspace-example">
      <div class="workspace-stack">
        <el-card shadow="never" class="panel-card top-summary-card">
          <div class="page-header">
            <div class="page-header-left">
              <h1 class="page-title">源码示例</h1>
            </div>
            <el-space wrap>
              <el-tag v-if="workspaceRoot" type="info" size="small">{{ workspaceRoot }}</el-tag>
              <el-button size="small" :loading="loadingWorkspace" @click="openWorkspace">加载</el-button>
              <el-button size="small" type="danger" :loading="deletingEntry" :disabled="!activeFileKey" @click="deleteCurrentEntry">删除</el-button>
            </el-space>
          </div>
        </el-card>

        <div class="workspace-shell">
          <div class="workspace-sidebar">
            <WorkspaceFileTree
              :entries="workspaceTree"
              :file-count="workspaceFileCount"
              :active-file-key="activeFileKey"
              @select="handleTreeNodeClick"
              @create-file="createWorkspaceFile"
              @create-directory="createWorkspaceDirectory"
              @rename="renameWorkspaceEntry"
            />
          </div>

          <div class="workspace-main">
            <WorkspaceEditorPanel
              ref="workspaceEditorRef"
              v-model="editorContent"
              v-model:active-tab="activeMainTab"
              :active-file-key="activeFileKey"
              :loading-file="loadingFile"
              :loading-workspace="loadingWorkspace"
              :saving-file="savingFile"
              @refresh="refreshWorkspace"
              @save="saveWorkspaceFile"
            />
          </div>

          <div class="workspace-assistant" :class="{ collapsed: aiCollapsed }">
            <AiChatWorkspace
              title="任务"
              subtitle="当前工作目录。"
              :compact-header="true"
              v-bind="aiAssistantHandlers"
              :identities-value="aiIdentities"
              :current-file-key="activeFileKey"
              :working-directory-value="effectiveWorkingDirectory"
              :load-history-on-mounted="true"
              :show-working-directory="false"
              task-ended-read-mode="snapshot"
              :on-before-send="handleBeforeAiSend"
              :on-task-ended="handleAiTaskEnded"
              height="100%"
              :min-height="0"
              :collapsible="true"
              @collapse-change="handleAiCollapseChange"
            />
          </div>
        </div>
      </div>
    </div>
  </el-config-provider>
</template>

<style scoped>
.workspace-example {
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.workspace-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.workspace-shell {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 8px;
}

.workspace-sidebar {
  width: 280px;
  flex-shrink: 0;
  min-height: 0;
}

.workspace-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.workspace-assistant {
  width: 400px;
  flex-shrink: 0;
  min-height: 0;
  transition: width 0.3s ease;
}

.workspace-assistant.collapsed {
  width: 0;
  overflow: hidden;
}
</style>
