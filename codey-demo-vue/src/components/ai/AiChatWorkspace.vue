<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Loading, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { useAiChat } from '../../composables/useAiChat'
import { formatTime, getMessageBlocks } from './chatPresentation'

const props = defineProps({
  title: {
    type: String,
    default: 'CDyra 会话窗口',
  },
  subtitle: {
    type: String,
    default: '保持 console 的连续会话体验，但以可嵌入组件形式提供。',
  },
  placeholder: {
    type: String,
    default: '输入你的目标，例如：请解释核心聊天流程',
  },
  apiBase: {
    type: String,
    default: '/api/chat',
  },
  skillNameValue: {
    type: String,
    default: '',
  },
  systemPromptValue: {
    type: String,
    default: '',
  },
  workingDirectoryValue: {
    type: String,
    default: '',
  },
  identitiesValue: {
    type: Array,
    default: () => [],
  },
  historyBase: {
    type: String,
    default: '/api/chat/history',
  },
  workspaceIdValue: {
    type: String,
    default: '',
  },
  currentFileKey: {
    type: String,
    default: '',
  },
  currentDocumentId: {
    type: String,
    default: '',
  },
  defaultWorkingDirectory: {
    type: String,
    default: '../',
  },
  height: {
    type: String,
    default: '100%',
  },
  minHeight: {
    type: Number,
    default: 520,
  },
  showHeader: {
    type: Boolean,
    default: true,
  },
  compactHeader: {
    type: Boolean,
    default: false,
  },
  showWorkingDirectory: {
    type: Boolean,
    default: false,
  },
  collapsible: {
    type: Boolean,
    default: false,
  },
  defaultCollapsed: {
    type: Boolean,
    default: false,
  },
  loadHistoryOnMounted: {
    type: Boolean,
    default: false,
  },
  onToolCall: {
    type: Function,
    default: null,
  },
  onFinalSummary: {
    type: Function,
    default: null,
  },
  onAssistantFinished: {
    type: Function,
    default: null,
  },
  onBeforeSend: {
    type: Function,
    default: null,
  },
  showThinking: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['session-change', 'message-sent', 'collapse-change'])

const historyDrawerVisible = ref(false)
const messageListRef = ref(null)
const currentSessionScopeKey = ref('')
const collapsed = ref(props.defaultCollapsed)

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  emit('collapse-change', collapsed.value)
}

function resolveCurrentFileDirectory(fileKey) {
  const normalized = typeof fileKey === 'string' ? fileKey.trim().replace(/\\/g, '/') : ''
  if (!normalized || !normalized.includes('/')) {
    return '.'
  }
  return normalized.slice(0, normalized.lastIndexOf('/')) || '.'
}

function normalizeIdentityList(values) {
  if (!Array.isArray(values)) {
    return []
  }
  return values
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter((item) => item.length > 0)
}

function buildChatContext() {
  const workspaceId = typeof props.workspaceIdValue === 'string' ? props.workspaceIdValue.trim() : ''
  const currentFileKey = typeof props.currentFileKey === 'string' ? props.currentFileKey.trim() : ''
  const currentDocumentId = typeof props.currentDocumentId === 'string' ? props.currentDocumentId.trim() : ''
  const systemPrompt = typeof props.systemPromptValue === 'string' ? props.systemPromptValue.trim() : ''
  const currentFileDirectory = resolveCurrentFileDirectory(currentFileKey)
  const currentFilePath = workingDirectory.value && currentFileKey
    ? `${workingDirectory.value.replace(/[\\/]$/, '')}/${currentFileKey.replace(/^\/+/, '')}`
    : ''
  const contextNotes = [
    workspaceId ? `当前项目路径: ${workspaceId}` : '',
    currentDocumentId ? `当前文档ID: ${currentDocumentId}` : '',
    currentFileKey ? `当前打开文件: ${currentFileKey}` : '当前打开文件: 未选择',
    `当前文件目录: ${currentFileDirectory}`,
    workingDirectory.value ? `当前项目工作目录: ${workingDirectory.value}` : '',
    currentFilePath ? `当前文件在项目工作目录中的路径: ${currentFilePath}` : '',
    '所有读取、搜索、修改、写入都必须限制在当前项目工作目录内。',
    '禁止跨出当前项目工作目录访问根目录下其他一级项目。',
    '当用户提到“改哪里”或“当前文件”时，先读取当前打开文件或当前文件目录下相关文件，再决定修改目标。',
    // 业务页面可按场景补充系统级约束，例如限制思考输出风格或口径。
    systemPrompt,
  ].filter(Boolean)
  return {
    contextFiles: currentFileKey ? [currentFileKey] : [],
    contextNotes,
    // 身份跟随页面场景传到底层，让会话自动筛选 skill 和 tool。
    identities: normalizeIdentityList(props.identitiesValue),
  }
}

function normalizeDirectory(value) {
  return typeof value === 'string'
    ? value.trim().replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
    : ''
}

function matchArchivedSessionScope(entry) {
  const currentDirectory = normalizeDirectory(workingDirectory.value)
  const archivedDirectory = normalizeDirectory(entry?.workingDirectory)
  if (!currentDirectory) {
    return true
  }
  if (!archivedDirectory) {
    return false
  }
  return archivedDirectory === currentDirectory
}

const {
  workingDirectory,
  inputValue,
  selectedSessionId,
  isLoadingSessions,
  isLoadingDetail,
  isSending,
  errorMessage,
  connectionStatus,
  archivedSessions,
  messages,
  canSend,
  loadSessions,
  selectSession,
  sendPrompt,
  startNewSession,
  resetSessionScope,
  deleteSession,
  clearSessions,
  hydrateLatestHistory,
} = useAiChat({
  apiBase: props.apiBase,
  historyBase: props.historyBase,
  defaultWorkingDirectory: props.defaultWorkingDirectory,
  skillName: props.skillNameValue,
  includeThinking: () => props.showThinking,
  welcomeMessage: '直接输入任务即可开始。',
  buildRequestContext: buildChatContext,
  filterArchivedSession: matchArchivedSessionScope,
  onToolCall: props.onToolCall,
  onFinalSummary: props.onFinalSummary,
  onAssistantFinished: props.onAssistantFinished,
})

const messageBlockMap = computed(() =>
  Object.fromEntries(messages.value.map((message) => [message.id, getMessageBlocks(message.content)]))
)
const reasoningBlockMap = computed(() =>
  Object.fromEntries(messages.value.map((message) => [message.id, getMessageBlocks(message.reasoning)]))
)
// 侧栏会话窗保留独立边界和留白，嵌入业务页面时仍像完整组件。
const shellStyle = computed(() => ({
  height: props.height,
  minHeight: `${props.minHeight}px`,
  display: 'flex',
  flexDirection: 'column',
  boxSizing: 'border-box',
  padding: '8px 10px 8px',
  background: 'var(--el-bg-color)',
  border: '1px solid var(--el-border-color)',
  overflow: 'hidden',
}))
const compactStatus = computed(() => (isSending.value ? '生成中...' : connectionStatus.value))

async function scrollMessagesToBottom() {
  await nextTick()
  const messageListElement =
    messageListRef.value?.wrapRef ||
    messageListRef.value?.$el?.querySelector?.('.el-scrollbar__wrap') ||
    messageListRef.value
  if (!messageListElement) {
    return
  }
  messageListElement.scrollTop = messageListElement.scrollHeight
}

async function openHistoryDrawer() {
  historyDrawerVisible.value = true
  if (!archivedSessions.value.length) {
    await loadSessions(selectedSessionId.value)
  }
}

async function handleSelectSession(sessionId) {
  await selectSession(sessionId)
  historyDrawerVisible.value = false
  emit('session-change', sessionId)
}

async function handleSend(prompt = inputValue.value) {
  const goal = typeof prompt === 'string' ? prompt.trim() : ''
  if (!goal) {
    return
  }
  try {
    // 业务页面可在这里先把最新上下文同步到工作区，再继续发送。
    const promptAfterHook = typeof props.onBeforeSend === 'function'
      ? await props.onBeforeSend({
          prompt: goal,
          workingDirectory: workingDirectory.value,
          currentFileKey: props.currentFileKey,
        })
      : goal
    const finalPrompt = typeof promptAfterHook === 'string' && promptAfterHook.trim()
      ? promptAfterHook.trim()
      : goal
    await sendPrompt(finalPrompt)
    emit('message-sent', finalPrompt)
  } catch (error) {
    errorMessage.value = error?.message || '发送前同步上下文失败'
    throw error
  }
}

async function handleStartNewSession() {
  await startNewSession()
  emit('session-change', '')
}

async function handleScopeChange(scopeKey) {
  if (!scopeKey || currentSessionScopeKey.value === scopeKey) {
    currentSessionScopeKey.value = scopeKey
    return
  }
  currentSessionScopeKey.value = scopeKey
  await resetSessionScope()
  emit('session-change', '')
}

async function handleDeleteSession(targetSessionId) {
  if (!targetSessionId) {
    return
  }
  try {
    await ElMessageBox.confirm('确认删除这个会话吗？删除后无法恢复。', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  await deleteSession(targetSessionId)
  await loadSessions(selectedSessionId.value)
  emit('session-change', selectedSessionId.value || '')
}

async function handleClearSessions() {
  if (!archivedSessions.value.length) {
    return
  }
  try {
    await ElMessageBox.confirm('确认清空全部历史会话吗？该操作无法恢复。', '清空历史会话', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  await clearSessions()
  await loadSessions(selectedSessionId.value)
  historyDrawerVisible.value = false
  emit('session-change', '')
}

function getRoleLabel(role) {
  if (role === 'user') {
    return '你'
  }
  if (role === 'assistant') {
    return '助手'
  }
  return '工具'
}

function getRoleTagType(role) {
  if (role === 'user') {
    return 'primary'
  }
  if (role === 'assistant') {
    return 'success'
  }
  return 'info'
}

function getMessageContainerStyle(role) {
  return {
    display: 'flex',
    justifyContent: role === 'user' ? 'flex-end' : 'flex-start',
    alignItems: 'flex-start',
    gap: '6px',
    width: '100%',
    boxSizing: 'border-box',
    paddingRight: role === 'user' ? '8px' : '0',
    padding: '0',
    background: 'transparent',
    borderLeft: 'none',
  }
}

function getMessageCardStyle(role) {
  if (role === 'user') {
    return {
      width: 'fit-content',
      maxWidth: '56%',
      border: 'none',
      background: '#eef1f5',
      color: '#303133',
      borderRadius: '8px',
    }
  }
  return {
    maxWidth: '100%',
    border: 'none',
    background: 'transparent',
  }
}

function getMessageShadow(role) {
  return 'never'
}

function getMessageBodyStyle(role) {
  if (role === 'user') {
    return {
      padding: '4px 10px',
      borderRadius: '6px',
    }
  }
  return {
    padding: '4px 10px',
  }
}

function getAssistantBubbleStyle() {
  return {
    flex: '1 1 auto',
    minWidth: '0',
  }
}

function getBubbleHeaderStyle(role) {
  if (role === 'user') {
    return {
      justifyContent: 'flex-end',
    }
  }
  return {
    justifyContent: 'flex-start',
  }
}

function getAvatarLabel(role) {
  if (role === 'user') {
    return '你'
  }
  if (role === 'assistant') {
    return 'AI'
  }
  return ''
}

function getAvatarStyle(role) {
  if (role === 'tool') {
    return {
      flex: '0 0 auto',
      opacity: 0,
      pointerEvents: 'none',
    }
  }
  return {
    flex: '0 0 auto',
    background: 'var(--el-color-primary-light-8)',
    color: 'var(--el-color-primary)',
  }
}

function showMessageAvatar(role) {
  return role !== 'user'
}

function showBubbleHeader(role) {
  return role !== 'user'
}

function isAssistantMessage(role) {
  return role === 'assistant'
}

function shouldShowReasoning(message) {
  return !!(
    props.showThinking
    && message?.live
    && typeof message?.reasoning === 'string'
    && message.reasoning.trim()
  )
}

function hasAssistantContent(message) {
  return !!messageBlockMap.value[message?.id]?.length
}

function shouldShowAssistantThinkingPlaceholder(message) {
  // 不展示思考明细且正文尚未返回时，用轻量状态提示替代大块空白区域。
  return !!(message?.live && !hasAssistantContent(message) && !shouldShowReasoning(message))
}

function isToolMessage(role) {
  return role === 'tool'
}

function getToolSummary(message) {
  const toolName = typeof message?.name === 'string' ? message.name.trim() : ''
  const content = typeof message?.content === 'string' ? message.content.trim() : ''
  if (!content) {
    return '正在整理本次工具操作的内容。'
  }
  if (!toolName) {
    return content
  }
  if (content.startsWith(`${toolName}：`)) {
    return content.slice(toolName.length + 1).trim()
  }
  if (content.startsWith(`${toolName}:`)) {
    return content.slice(toolName.length + 1).trim()
  }
  return content
}

// 思考内容默认只展示摘要，避免长链路推理直接挤占正文区域。
defineExpose({
  sendPrompt: handleSend,
  startNewSession: handleStartNewSession,
  reloadSessions: loadSessions,
  selectSession: handleSelectSession,
})

onMounted(async () => {
  if (props.loadHistoryOnMounted) {
    await hydrateLatestHistory()
  }
  await scrollMessagesToBottom()
})

watch(
  () => props.workingDirectoryValue,
  (value) => {
    if (typeof value !== 'string' || !value.trim()) {
      return
    }
    // 外部工作区切换后，聊天请求始终指向最新工作目录。
    workingDirectory.value = value.trim()
  },
  {
    immediate: true,
  }
)

watch(
  () => [props.workspaceIdValue, props.workingDirectoryValue].join('|'),
  async (scopeKey) => {
    await handleScopeChange(scopeKey)
  },
  {
    immediate: true,
  }
)

watch(
  messages,
  async () => {
    await scrollMessagesToBottom()
  },
  {
    deep: true,
    flush: 'post',
  }
)

watch(
  () => isLoadingDetail.value,
  async () => {
    await scrollMessagesToBottom()
  },
  {
    flush: 'post',
  }
)
</script>

<template>
  <div v-if="collapsible && collapsed" class="ai-chat-collapsed" @click="toggleCollapsed">
    <el-icon class="collapsed-icon"><DArrowRight /></el-icon>
    <span class="collapsed-text">AI</span>
  </div>

  <div v-else class="ai-chat-shell" :style="shellStyle">
    <div
      style="display: flex; flex-direction: column; width: 100%; height: 100%; min-height: 0; box-sizing: border-box;"
    >
      <div
        v-if="showHeader"
        class="chat-header"
        :class="{ compact: compactHeader }"
        style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 2px 0 14px; border-bottom: 1px solid var(--el-border-color-lighter);"
      >
        <div
          class="chat-header-body"
          style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex: 1; width: 100%; flex-wrap: wrap;"
        >
          <el-space direction="vertical" size="small" class="chat-header-title">
            <el-text size="large">{{ title }}</el-text>
            <el-text v-if="subtitle && !compactHeader" type="info">{{ subtitle }}</el-text>
          </el-space>

          <el-space wrap size="small" class="ai-toolbar">
            <slot name="toolbar" />
            <el-button v-if="collapsible" size="small" class="toolbar-secondary-button" @click.stop="toggleCollapsed">
              <el-icon><DArrowLeft /></el-icon>
            </el-button>
            <el-button size="small" class="toolbar-secondary-button" @click="openHistoryDrawer">历史会话</el-button>
            <el-button size="small" type="primary" class="toolbar-primary-button" @click="handleStartNewSession">新会话</el-button>
          </el-space>
        </div>
      </div>

      <div style="display: flex; flex: 1; flex-direction: column; gap: 8px; width: 100%; min-height: 0; padding-top: 6px;">
        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
        />

        <div
          style="flex: 1; min-height: 0; padding: 2px 0 0; box-sizing: border-box;"
        >
          <el-scrollbar ref="messageListRef" class="message-scrollbar" height="100%">
            <div style="display: flex; flex-direction: column; gap: 0; width: 100%; padding: 0 6px 10px 0; box-sizing: border-box;">
              <div
                v-for="message in messages"
                :key="message.id"
                class="message-row"
                :class="{
                  'assistant-message-row': isAssistantMessage(message.role),
                  'tool-message-row': isToolMessage(message.role),
                }"
                :style="getMessageContainerStyle(message.role)"
              >
                <el-avatar
                  v-if="showMessageAvatar(message.role)"
                  shape="square"
                  :size="24"
                  :style="getAvatarStyle(message.role)"
                >
                  {{ getAvatarLabel(message.role) }}
                </el-avatar>

                <template v-if="isAssistantMessage(message.role)">
                  <div :style="getAssistantBubbleStyle()">
                    <div style="display: flex; flex-direction: column; gap: 6px; width: 100%;">
                      <transition name="reasoning-fade">
                        <div
                          v-if="shouldShowReasoning(message)"
                          class="reasoning-panel"
                        >
                          <div class="reasoning-header">
                            <div class="reasoning-title">
                              <el-icon class="reasoning-loading-icon"><Loading /></el-icon>
                              <span>思考中</span>
                            </div>
                            <span class="reasoning-tip">完成后自动收起</span>
                          </div>
                          <template v-if="reasoningBlockMap[message.id]?.length">
                            <div class="reasoning-body">
                              <template
                                v-for="(block, blockIndex) in reasoningBlockMap[message.id]"
                                :key="`reasoning-${message.id}-${blockIndex}`"
                              >
                                <div
                                  v-if="block.type === 'text'"
                                  class="reasoning-text-block"
                                >
                                  <div
                                    v-for="(paragraph, paragraphIndex) in block.paragraphs"
                                    :key="`reasoning-${message.id}-${blockIndex}-${paragraphIndex}`"
                                    class="reasoning-paragraph"
                                  >
                                    {{ paragraph }}
                                  </div>
                                </div>

                                <el-card v-else shadow="never" class="reasoning-code-card">
                                  <template #header>{{ block.language || 'text' }}</template>
                                  <el-scrollbar max-height="200px">
                                    <pre style="margin: 0;">{{ block.content }}</pre>
                                  </el-scrollbar>
                                </el-card>
                              </template>
                            </div>
                          </template>
                        </div>
                      </transition>
                      <template v-if="hasAssistantContent(message)">
                      <div style="display: flex; flex-direction: column; gap: 6px; width: 100%;">
                        <template
                          v-for="(block, blockIndex) in messageBlockMap[message.id]"
                          :key="`${message.id}-${blockIndex}`"
                        >
                          <div
                            v-if="block.type === 'text'"
                            style="color: #4b5563; line-height: 1.65; font-size: 14px;"
                          >
                            <div
                              v-for="(paragraph, paragraphIndex) in block.paragraphs"
                              :key="`${message.id}-${blockIndex}-${paragraphIndex}`"
                              style="margin-bottom: 6px;"
                            >
                              {{ paragraph }}
                            </div>
                          </div>

                          <el-card v-else shadow="never">
                            <template #header>{{ block.language || 'text' }}</template>
                            <el-scrollbar max-height="240px">
                              <pre style="margin: 0;">{{ block.content }}</pre>
                            </el-scrollbar>
                          </el-card>
                        </template>
                      </div>
                      </template>

                      <div v-else-if="shouldShowAssistantThinkingPlaceholder(message)" class="assistant-thinking-placeholder">
                        <el-icon class="reasoning-loading-icon"><Loading /></el-icon>
                        <span>正在思考中...</span>
                      </div>
                    </div>
                  </div>
                </template>

                <template v-else-if="isToolMessage(message.role)">
                  <div
                    style="display: flex; align-items: center; gap: 6px; max-width: 88%; padding: 2px 0; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.45; font-weight: 600;"
                  >
                    <span style="flex: 0 0 auto; color: var(--el-color-primary); font-size: 13px;">⚙</span>
                    <strong style="flex: 0 0 auto; font-size: 13px; font-weight: 600; color: var(--el-text-color-primary);">{{ message.name || '工具调用' }}</strong>
                    <span style="min-width: 0; color: var(--el-text-color-secondary); font-size: 13px; font-weight: 500;">{{ getToolSummary(message) }}</span>
                  </div>
                </template>

                <el-card
                  v-else
                  class="message-bubble-card"
                  :shadow="getMessageShadow(message.role)"
                  :style="getMessageCardStyle(message.role)"
                  :body-style="getMessageBodyStyle(message.role)"
                >
                  <div style="display: flex; flex-direction: column; gap: 6px; width: 100%;">
                    <div
                      v-if="showBubbleHeader(message.role)"
                      style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;"
                      :style="getBubbleHeaderStyle(message.role)"
                    >
                      <el-tag :type="getRoleTagType(message.role)" size="small">
                        {{ getRoleLabel(message.role) }}
                      </el-tag>
                    </div>

                    <template v-if="messageBlockMap[message.id]?.length">
                      <div style="display: flex; flex-direction: column; gap: 6px; width: 100%;">
                        <template
                          v-for="(block, blockIndex) in messageBlockMap[message.id]"
                          :key="`${message.id}-${blockIndex}`"
                        >
                          <div v-if="block.type === 'text'" style="line-height: 1.65; font-size: 14px;">
                            <div
                              v-for="(paragraph, paragraphIndex) in block.paragraphs"
                              :key="`${message.id}-${blockIndex}-${paragraphIndex}`"
                              style="margin-bottom: 6px;"
                            >
                              {{ paragraph }}
                            </div>
                          </div>

                          <el-card v-else shadow="never">
                            <template #header>{{ block.language || 'text' }}</template>
                            <el-scrollbar max-height="240px">
                              <pre style="margin: 0;">{{ block.content }}</pre>
                            </el-scrollbar>
                          </el-card>
                        </template>
                      </div>
                    </template>

                    <el-text v-else type="info">...</el-text>
                  </div>
                </el-card>
              </div>

              <el-skeleton v-if="isLoadingDetail" :rows="4" animated />
            </div>
          </el-scrollbar>
        </div>

        <div
          style="display: flex; flex-direction: column; gap: 6px; width: 100%; padding-top: 8px; border-top: 1px solid var(--el-border-color-lighter);"
        >
          <el-input
            v-if="showWorkingDirectory"
            v-model="workingDirectory"
            placeholder="请输入工作目录"
          >
            <template #prepend>工作目录</template>
          </el-input>

          <el-input
            v-model="inputValue"
            class="chat-input"
            type="textarea"
            :rows="3"
            :placeholder="placeholder"
            resize="none"
            @keydown.ctrl.enter.prevent="handleSend(inputValue)"
          />

          <div
            style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;"
          >
            <el-space wrap size="small">
              <el-text type="info">{{ compactStatus }}</el-text>
              <el-text type="info">Ctrl + Enter 发送</el-text>
            </el-space>

            <el-button
              type="primary"
              class="send-button"
              :loading="isSending"
              :disabled="!canSend"
              @click="handleSend(inputValue)"
            >
              {{ isSending ? '生成中...' : '发送' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="historyDrawerVisible"
      title="历史会话"
      size="42%"
    >
      <el-space direction="vertical" fill size="large" style="width: 100%;">
        <el-space wrap>
          <el-button :loading="isLoadingSessions" @click="loadSessions(selectedSessionId)">刷新</el-button>
          <el-button :disabled="!archivedSessions.length" @click="handleClearSessions">清空</el-button>
        </el-space>

        <el-empty
          v-if="!archivedSessions.length && !isLoadingSessions"
          description="暂无历史会话"
        />

        <el-table
          v-else
          :data="archivedSessions"
          row-key="sessionId"
          highlight-current-row
          :current-row-key="selectedSessionId"
          style="width: 100%;"
        >
          <el-table-column prop="title" label="会话标题" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.title || '未命名会话' }}
            </template>
          </el-table-column>
          <el-table-column prop="preview" label="摘要" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.preview || '暂无摘要' }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-space wrap>
                <el-button link type="primary" @click="handleSelectSession(row.sessionId)">打开</el-button>
                <el-button link type="danger" @click="handleDeleteSession(row.sessionId)">删除</el-button>
              </el-space>
            </template>
          </el-table-column>
        </el-table>
      </el-space>
    </el-drawer>
  </div>
</template>

<style scoped>
.ai-chat-shell {
  border-radius: 6px;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --el-bg-color: #ffffff;
  --el-fill-color-blank: #ffffff;
  --el-color-primary: #409eff;
  --el-color-primary-light-3: #79bbff;
  --el-color-primary-light-5: #a0cfff;
  --el-color-primary-light-7: #c6e2ff;
  --el-color-primary-light-8: #d9ecff;
  --el-color-primary-light-9: #ecf5ff;
  --el-border-color: #e4e7ed;
  --el-border-color-light: #e4e7ed;
  --el-border-color-lighter: #ebeef5;
  --el-text-color-primary: #303133;
  --el-text-color-regular: #303133;
  --el-text-color-secondary: #6e7681;
}

.ai-chat-shell :deep(.el-button) {
  border-radius: 4px;
  min-height: 26px;
  padding: 5px 10px;
  font-size: 13px;
}

.ai-chat-shell :deep(.el-card) {
  border-radius: 6px;
}

.ai-chat-shell :deep(.el-text) {
  font-size: 13px;
}

.ai-toolbar {
  padding: 5px 6px;
  background: #fafbfc;
  border-radius: 4px;
}

.chat-header {
  min-height: 42px;
  padding: 6px 0 !important;
  align-items: center !important;
}

.chat-header-body {
  align-items: center !important;
}

.chat-header-title {
  justify-content: center;
  min-height: 32px;
}

.chat-header.compact {
  gap: 8px !important;
}

.chat-header.compact :deep(.el-space) {
  gap: 4px !important;
}

.chat-header.compact :deep(.el-text.is-large) {
  font-size: 14px;
  line-height: 1.2;
}

.chat-header.compact .ai-toolbar {
  padding: 2px 4px;
}

.toolbar-secondary-button {
  background: #ffffff;
  border-color: #dcdfe6;
  color: #303133;
}

.toolbar-primary-button {
  background: #409eff;
  border-color: #409eff;
  color: #ffffff;
}

.message-bubble-card :deep(.el-card__body) {
  border-radius: 0;
}

.message-scrollbar :deep(.el-scrollbar__wrap) {
  padding-right: 4px;
  box-sizing: border-box;
}

.reasoning-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
}

.reasoning-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.reasoning-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.reasoning-loading-icon {
  color: #2563eb;
  animation: reasoning-spin 1.2s linear infinite;
}

.reasoning-tip {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 500;
}

.reasoning-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.reasoning-text-block {
  color: #475569;
  line-height: 1.65;
  font-size: 13px;
}

.reasoning-paragraph {
  margin-bottom: 6px;
}

.reasoning-code-card :deep(.el-card__header) {
  padding: 8px 12px;
}

.reasoning-code-card :deep(.el-card__body) {
  padding: 10px 12px;
}

.assistant-thinking-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  padding: 2px 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.4;
}

.reasoning-fade-enter-active,
.reasoning-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.reasoning-fade-enter-from,
.reasoning-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes reasoning-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.message-row {
  margin: 1px 0;
}

.assistant-message-row {
  margin-top: 16px;
}

.tool-message-row {
  margin-top: 1px;
  margin-bottom: 1px;
}

.chat-input :deep(.el-textarea__inner) {
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.5;
}

.chat-input :deep(.el-textarea__inner:focus) {
  border-color: #94ceff;
  box-shadow: 0 0 0 1px #94ceff inset;
}

.send-button:hover,
.send-button:focus {
  background: #337ecc;
  border-color: #337ecc;
}

.ai-chat-collapsed {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  min-height: 60px;
  padding: 12px 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.ai-chat-collapsed:hover {
  background: #f5f7fa;
}

.collapsed-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.collapsed-text {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  max-height: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
