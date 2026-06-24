<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import AiChatWorkspace from '../../components/ai/AiChatWorkspace.vue'
import WorkspaceEditorPanel from '../../components/workspace/WorkspaceEditorPanel.vue'
import WorkspaceFileTree from '../../components/workspace/WorkspaceFileTree.vue'
import { createWorkspaceApi } from '../../api/workspace'

const workspaceApi = createWorkspaceApi()

const workspaceSnapshot = ref(null)
const workspaceEditorRef = ref(null)
const activeDocumentId = ref('')
const activeFileKey = ref('')
const editorContent = ref('')
const loadedFileContent = ref('')
const workspaceStatus = ref('工作目录尚未加载')
const loadingWorkspace = ref(false)
const loadingFile = ref(false)
const savingFile = ref(false)
const creatingEntry = ref(false)
const deletingEntry = ref(false)
const activeMainTab = ref('code')

const workspaceTree = computed(() => workspaceSnapshot.value?.entries || [])
const workspaceRoot = computed(() => workspaceSnapshot.value?.rootPath || '')
const workspaceFileCount = computed(() => workspaceSnapshot.value?.fileCount || 0)
const aiProjectPath = ref('')
const aiWorkingDirectory = ref('')
const aiCurrentFileKey = ref('')
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

function createDocumentId(path) {
  return path ? `workspace:${path}` : ''
}

function findFileByDocumentId(documentId, snapshot = workspaceSnapshot.value) {
  if (!documentId) {
    return null
  }
  return collectFileNodes(snapshot?.entries || []).find((item) => item.documentId === documentId) || null
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
  activeDocumentId.value = file?.documentId || createDocumentId(file?.fileKey || file?.path || '')
  activeFileKey.value = file?.fileKey || file?.path || ''
  editorContent.value = file?.content || ''
  loadedFileContent.value = file?.content || ''
}

function clearCurrentFile() {
  activeDocumentId.value = ''
  activeFileKey.value = ''
  editorContent.value = ''
  loadedFileContent.value = ''
}

async function openWorkspace() {
  loadingWorkspace.value = true
  try {
    const snapshot = await workspaceApi.query('')
    await applySnapshot(snapshot, activeFileKey.value, {
      syncAiContext: true,
    })
    ElMessage.success('工作目录已加载')
  } catch (error) {
    workspaceStatus.value = error.message || '加载工作目录失败'
    ElMessage.error(workspaceStatus.value)
  } finally {
    loadingWorkspace.value = false
  }
}

function syncAiContext(snapshot) {
  aiProjectPath.value = snapshot?.projectPath || ''
  aiWorkingDirectory.value = snapshot?.projectWorkingDirectory || snapshot?.rootPath || ''
  aiCurrentFileKey.value = snapshot?.currentProjectFilePath || ''
}

async function applySnapshot(snapshot, preferredPath = '', options = {}) {
  const shouldSyncAiContext = options.syncAiContext !== false
  workspaceSnapshot.value = snapshot
  workspaceStatus.value = snapshot?.status || '工作目录状态未知'
  if (shouldSyncAiContext) {
    syncAiContext(snapshot)
  }
  // 查询接口返回 currentFile 时，直接以服务端选中的文件为准。
  if (snapshot?.currentFile) {
    applyCurrentFile(snapshot.currentFile)
    return
  }
  const nextFile =
    findFileByPath(preferredPath, snapshot) ||
    findFileByDocumentId(activeDocumentId.value, snapshot) ||
    resolveNextFile(snapshot)
  if (!nextFile) {
    clearCurrentFile()
    return
  }
  await loadWorkspaceFile(nextFile.fileKey || nextFile.path, {
    syncAiContext: shouldSyncAiContext,
  })
}

async function loadWorkspaceFile(fileOrPath, options = {}) {
  const path = typeof fileOrPath === 'string'
    ? fileOrPath
    : (fileOrPath?.fileKey || fileOrPath?.path || '')
  if (!path) {
    return
  }
  loadingFile.value = true
  try {
    const snapshot = await workspaceApi.query(path)
    await applySnapshot(snapshot, path, options)
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
    const latestEditorContent = typeof workspaceEditorRef.value?.getValue === 'function'
      ? workspaceEditorRef.value.getValue()
      : editorContent.value
    editorContent.value = latestEditorContent
    const snapshot = await workspaceApi.updateFileContent(activeFileKey.value, latestEditorContent)
    await applySnapshot(snapshot, activeFileKey.value, {
      syncAiContext: true,
    })
    ElMessage.success('文件已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存文件失败')
  } finally {
    savingFile.value = false
  }
}

async function refreshWorkspace(showMessage = true, options = {}) {
  loadingWorkspace.value = true
  try {
    const snapshot = await workspaceApi.query('')
    await applySnapshot(snapshot, activeFileKey.value, options)
    if (showMessage) {
      ElMessage.success('工作目录已刷新')
    }
  } catch (error) {
    if (showMessage) {
      ElMessage.error(error.message || '刷新工作目录失败')
    }
  } finally {
    loadingWorkspace.value = false
  }
}

function joinWorkspacePath(parentPath, name) {
  const normalizedParent = typeof parentPath === 'string'
    ? parentPath.trim().replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')
    : ''
  const normalizedName = typeof name === 'string'
    ? name.trim().replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')
    : ''
  if (!normalizedParent) {
    return normalizedName
  }
  if (!normalizedName) {
    return normalizedParent
  }
  return `${normalizedParent}/${normalizedName}`
}

function remapPathAfterRename(currentPath, sourcePath, nextName) {
  if (!currentPath || !sourcePath || !nextName) {
    return currentPath || ''
  }
  const normalizedSourcePath = sourcePath.trim()
  const sourceSegments = normalizedSourcePath.split('/')
  const renamedPath = [...sourceSegments.slice(0, -1), nextName].filter(Boolean).join('/')
  if (currentPath === normalizedSourcePath) {
    return renamedPath
  }
  const prefix = `${normalizedSourcePath}/`
  if (currentPath.startsWith(prefix)) {
    return `${renamedPath}/${currentPath.slice(prefix.length)}`
  }
  return currentPath
}

async function createWorkspaceEntry(options = {}) {
  const directory = options.directory === true
  const parentPath = typeof options.parentPath === 'string' ? options.parentPath : ''
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
  const path = joinWorkspacePath(parentPath, entryName)
  if (!path) {
    return
  }

  creatingEntry.value = true
  try {
    const snapshot = directory
      ? await workspaceApi.createDirectory(path)
      : await workspaceApi.createFile(path, '')
    await applySnapshot(snapshot, directory ? activeFileKey.value : path, {
      syncAiContext: true,
    })
    ElMessage.success(directory ? '目录已创建' : '文件已创建')
  } catch (error) {
    ElMessage.error(error.message || (directory ? '创建目录失败' : '创建文件失败'))
  } finally {
    creatingEntry.value = false
  }
}

async function createWorkspaceFile(parentPath = '') {
  await createWorkspaceEntry({
    parentPath,
    directory: false,
  })
}

async function createWorkspaceDirectory(parentPath = '') {
  await createWorkspaceEntry({
    parentPath,
    directory: true,
  })
}

async function renameWorkspaceEntry(node) {
  const sourcePath = node?.path || node?.fileKey || ''
  const currentName = node?.name || node?.label || ''
  if (!sourcePath || !currentName) {
    return
  }
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
    const preferredPath = remapPathAfterRename(activeFileKey.value, sourcePath, nextName)
    const snapshot = await workspaceApi.rename(sourcePath, nextName)
    await applySnapshot(snapshot, preferredPath, {
      syncAiContext: true,
    })
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
    await applySnapshot(snapshot, '', {
      syncAiContext: true,
    })
    ElMessage.success('文件已删除')
  } catch (error) {
    ElMessage.error(error.message || '删除文件失败')
  } finally {
    deletingEntry.value = false
  }
}

async function handleAiFinalSummary() {
  // AI 会话结束后只刷新目录和编辑区，不回写 AI 作用域，避免对话面板被误判为作用域切换。
  await refreshWorkspace(false, {
    syncAiContext: false,
  })
  ElMessage.success('AI 本轮已结束，工作目录已刷新')
}

function handleTreeNodeClick(node) {
  if (!node || node.directory) {
    return
  }
  loadWorkspaceFile(node)
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
              :active-document-id="activeDocumentId"
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
              :workspace-id-value="aiProjectPath || workspaceRoot"
              :identities-value="aiIdentities"
              :current-file-key="aiCurrentFileKey"
              :current-document-id="activeDocumentId"
              :working-directory-value="aiWorkingDirectory"
              default-working-directory="./workspace/project"
              :load-history-on-mounted="true"
              :show-working-directory="false"
              :on-final-summary="handleAiFinalSummary"
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
  gap: 8px;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

.panel-card {
  border-radius: 8px;
}

.workspace-shell {
  display: flex;
  align-items: stretch;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.top-summary-card :deep(.el-card__body) {
  padding: 8px 12px;
}

.workspace-sidebar {
  width: 220px;
  flex: 0 0 220px;
  min-height: 0;
}

.workspace-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.workspace-assistant {
  width: 420px;
  flex: 0 0 420px;
  min-height: 0;
  transition: width 0.3s ease, flex-basis 0.3s ease;
}

.workspace-assistant.collapsed {
  width: 48px;
  flex: 0 0 48px;
}

.workspace-example :deep(.el-scrollbar__wrap),
.workspace-example :deep(.cm-scroller) {
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc transparent;
}

.workspace-example :deep(.el-scrollbar__wrap)::-webkit-scrollbar,
.workspace-example :deep(.cm-scroller)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.workspace-example :deep(.el-scrollbar__wrap)::-webkit-scrollbar-thumb,
.workspace-example :deep(.cm-scroller)::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 999px;
}

.workspace-example :deep(.el-scrollbar__wrap)::-webkit-scrollbar-track,
.workspace-example :deep(.cm-scroller)::-webkit-scrollbar-track {
  background: transparent;
}

@media (max-width: 1400px) {
  .workspace-shell {
    flex-direction: column;
  }

  .workspace-sidebar,
  .workspace-assistant {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
