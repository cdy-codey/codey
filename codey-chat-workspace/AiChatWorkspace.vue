<script setup>
// 聊天工作区组件 — 纯原生实现，不依赖任何第三方 UI 组件库。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAiChat } from './useAiChat'
import { formatTime, getMessageBlocks } from './chatPresentation'
import { emitSystemAiAssistantEvent, registerAiAssistantController } from './assistantBridge'

const props = defineProps({
  title: {
    type: String,
    default: 'AI 助手',
  },
  subtitle: {
    type: String,
    default: '面向当前系统页面的智能助手，可直接提问、分析和协助处理业务。',
  },
  placeholder: {
    type: String,
    default: '输入你的目标，例如：请解释核心聊天流程',
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
  pagePayloadValue: {
    type: [Object, Array, String, Number, Boolean],
    default: null,
  },
  currentFileKey: {
    type: String,
    default: '',
  },
  formDisplayName: {
    type: String,
    default: '表单内容',
  },
  defaultWorkingDirectory: {
    type: String,
    default: null,
  },
  height: {
    type: String,
    default: '100%',
  },
  composerRows: {
    type: Number,
    default: 3,
  },
  composerBottomOffset: {
    type: String,
    default: '0px',
  },
  minHeight: {
    type: Number,
    default: 520,
  },
  popupMode: {
    type: Boolean,
    default: false,
  },
  popupWidth: {
    type: String,
    default: '920px',
  },
  popupHeight: {
    type: String,
    default: '76vh',
  },
  popupRight: {
    type: Number,
    default: 24,
  },
  popupLauncherTop: {
    type: String,
    default: '50%',
  },
  popupZIndex: {
    type: Number,
    default: 3000,
  },
  launcherText: {
    type: String,
    default: 'AI助手',
  },
  launcherHint: {
    type: String,
    default: '打开智能助手',
  },
  defaultVisible: {
    type: Boolean,
    default: false,
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
  historyEnabled: {
    type: Boolean,
    default: true,
  },
  syncPayloadOnOpen: {
    type: Boolean,
    default: true,
  },
  taskEndedReadMode: {
    type: String,
    default: 'json',
  },
  onToolCall: {
    type: Function,
    default: null,
  },
  onTaskAccepted: {
    type: Function,
    default: null,
  },
  onTaskCompleted: {
    type: Function,
    default: null,
  },
  onTaskFailed: {
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
  onTaskEnded: {
    type: Function,
    default: null,
  },
  onBeforeSend: {
    type: Function,
    default: null,
  },
  openSession: {
    type: Function,
    default: null,
  },
  resumeSession: {
    type: Function,
    default: null,
  },
  sendMessage: {
    type: Function,
    default: null,
  },
  closeSession: {
    type: Function,
    default: null,
  },
  listSessions: {
    type: Function,
    default: null,
  },
  getSessionDetail: {
    type: Function,
    default: null,
  },
  deleteHistorySession: {
    type: Function,
    default: null,
  },
  clearHistorySessions: {
    type: Function,
    default: null,
  },
  createEventSource: {
    type: Function,
    default: null,
  },
  queryWorkspace: {
    type: Function,
    default: null,
  },
  writeWorkspaceFile: {
    type: Function,
    default: null,
  },
  showThinking: {
    type: Boolean,
    default: true,
  },
  welcomeMessage: {
    type: String,
    default: '直接输入任务即可开始。',
  },
  welcomeSuggestions: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits([
  'session-change',
  'message-sent',
  'collapse-change',
  'visibility-change',
  'payload-synced',
  'task-accepted',
  'task-completed',
  'task-failed',
  'assistant-finished',
  'task-ended',
])

const historyDrawerVisible = ref(false)
const messageListRef = ref(null)
const currentSessionScopeKey = ref('')
const collapsed = ref(props.defaultCollapsed)
const assistantVisible = ref(props.defaultVisible)
const runtimeAssistantProps = ref({})
let unregisterAssistantController = () => {}
const currentFileResolved = ref(false)
let taskEndedPromise = null
let errorMessageTimer = null

// 确认对话框状态，替代 ElMessageBox.confirm
const confirmDialog = ref({
  visible: false,
  title: '',
  message: '',
  confirmText: '确认',
  cancelText: '取消',
  type: 'warning',
  resolve: null,
  reject: null,
})

function showConfirm(message, title, options = {}) {
  return new Promise((resolve, reject) => {
    confirmDialog.value = {
      visible: true,
      title,
      message,
      confirmText: options.confirmButtonText || '确认',
      cancelText: options.cancelButtonText || '取消',
      type: options.type || 'warning',
      resolve,
      reject,
    }
  })
}

function handleConfirmOk() {
  confirmDialog.value.resolve?.()
  confirmDialog.value.visible = false
}

function handleConfirmCancel() {
  confirmDialog.value.reject?.()
  confirmDialog.value.visible = false
}

function resolveAssistantProp(name) {
  if (Object.prototype.hasOwnProperty.call(runtimeAssistantProps.value, name)) {
    return runtimeAssistantProps.value[name]
  }
  return props[name]
}

function resolveAssistantStringProp(name, fallback = '') {
  const value = resolveAssistantProp(name)
  return typeof value === 'string' ? value : fallback
}

function resolveAssistantBooleanProp(name, fallback = false) {
  const value = resolveAssistantProp(name)
  return typeof value === 'boolean' ? value : fallback
}

function resolveAssistantFunctionProp(name) {
  const value = resolveAssistantProp(name)
  return typeof value === 'function' ? value : null
}

function normalizeAssistantOpenProps(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }
  return { ...value }
}

const displayTitle = computed(() => resolveAssistantStringProp('title', 'AI 助手'))
const displaySubtitle = computed(() => resolveAssistantStringProp('subtitle', ''))
const displayPlaceholder = computed(() => resolveAssistantStringProp('placeholder', ''))
const effectiveShowHeader = computed(() => resolveAssistantBooleanProp('showHeader', true))
const effectiveCompactHeader = computed(() => resolveAssistantBooleanProp('compactHeader', false))
const effectiveShowWorkingDirectory = computed(() => resolveAssistantBooleanProp('showWorkingDirectory', false))
const effectiveCollapsible = computed(() => resolveAssistantBooleanProp('collapsible', false))
const effectiveHistoryEnabled = computed(() => resolveAssistantBooleanProp('historyEnabled', true))
const effectiveWorkingDirectoryValue = computed(() => resolveAssistantStringProp('workingDirectoryValue', ''))
const effectiveCurrentFileKey = computed(() => resolveAssistantStringProp('currentFileKey', ''))
const effectiveFormDisplayName = computed(() => resolveAssistantStringProp('formDisplayName', '表单内容'))
const effectiveSystemPromptValue = computed(() => resolveAssistantStringProp('systemPromptValue', ''))
const effectiveComposerBottomOffset = computed(() => resolveAssistantStringProp('composerBottomOffset', '0px'))
const effectivePagePayloadValue = computed(() => resolveAssistantProp('pagePayloadValue'))
const effectiveTaskEndedReadMode = computed(() => {
  const value = resolveAssistantStringProp('taskEndedReadMode', 'json')
  return value === 'snapshot' ? 'snapshot' : 'json'
})
const effectiveIdentitiesValue = computed(() => {
  const value = resolveAssistantProp('identitiesValue')
  return Array.isArray(value) ? value : []
})
const effectiveShowThinking = computed(() => resolveAssistantBooleanProp('showThinking', true))
const effectiveWelcomeMessage = computed(() => resolveAssistantStringProp('welcomeMessage', '直接输入任务即可开始。'))
const effectiveWelcomeSuggestions = computed(() => {
  const value = resolveAssistantProp('welcomeSuggestions')
  return Array.isArray(value) ? value.filter((s) => typeof s === 'string' && s.trim()) : []
})
const effectiveComposerRows = computed(() => {
  const value = Number(resolveAssistantProp('composerRows'))
  return Number.isFinite(value) && value > 0 ? value : 3
})

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  emit('collapse-change', collapsed.value)
}

function normalizeWorkspacePath(value) {
  return typeof value === 'string' ? value.trim().replace(/\\/g, '/').replace(/^\/+|\/+$/g, '') : ''
}

function resolveCurrentWorkspacePath() {
  const fileKey = normalizeWorkspacePath(effectiveCurrentFileKey.value)
  if (!fileKey) {
    return ''
  }
  const baseDirectory = normalizeWorkspacePath(
    effectiveWorkingDirectoryValue.value || workingDirectory.value || '',
  )
  const cleanBase = baseDirectory.replace(/^(?:\.\/|workspace\/)+/, '')
  return cleanBase ? `${cleanBase}/${fileKey}` : fileKey
}

function resolvePayloadWorkspacePath() {
  const fileKey = normalizeWorkspacePath(effectiveCurrentFileKey.value || 'context.json')
  if (!fileKey) {
    return ''
  }
  const baseDirectory = normalizeWorkspacePath(
    effectiveWorkingDirectoryValue.value || workingDirectory.value || '',
  )
  const cleanBase = baseDirectory.replace(/^(?:\.\/|workspace\/)+/, '')
  return cleanBase ? `${cleanBase}/${fileKey}` : fileKey
}

function buildWorkspaceRequestParams(targetPath, extra = {}) {
  return {
    path: normalizeWorkspacePath(targetPath),
    workingDirectory: normalizeWorkspacePath(
      effectiveWorkingDirectoryValue.value || workingDirectory.value || '',
    ),
    currentFileKey: normalizeWorkspacePath(effectiveCurrentFileKey.value),
    ...extra,
  }
}

function resolveSessionScopeKey() {
  const runtimeWorkingDirectory = effectiveWorkingDirectoryValue.value.trim()
  return runtimeWorkingDirectory
}

function resolvePagePayloadContent(payload) {
  if (typeof payload === 'string') {
    return payload
  }
  return JSON.stringify(payload, null, 2)
}

async function writeWorkspaceFile(params = {}) {
  const path = typeof params === 'string' ? params : (params?.path || '')
  const content = typeof params === 'string' ? '' : (params?.content ?? '')
  if (!path) {
    return
  }
  const handler = resolveAssistantFunctionProp('writeWorkspaceFile')
  if (!handler) {
    throw new Error('请传入 writeWorkspaceFile 函数')
  }
  await handler(buildWorkspaceRequestParams(path, { content }))
}

async function syncPagePayloadToWorkspace(payloadOverride, reason = 'manual') {
  const hasOverride = arguments.length > 0
  const payload = hasOverride ? payloadOverride : effectivePagePayloadValue.value
  const targetPath = resolvePayloadWorkspacePath()
  if (payload === null || payload === undefined || !targetPath) {
    return null
  }
  const content = resolvePagePayloadContent(payload)
  await writeWorkspaceFile({ path: targetPath, content })
  const syncResult = {
    path: targetPath,
    payload,
    reason,
  }
  const eventPayload = withAssistantRuntimeMeta(syncResult)
  emit('payload-synced', eventPayload)
  emitSystemAiAssistantEvent('payload-synced', eventPayload)
  return eventPayload
}

async function openAssistant(options = {}) {
  const hasAssistantPropsOverride = Object.prototype.hasOwnProperty.call(options || {}, 'assistantProps')
  if (hasAssistantPropsOverride) {
    runtimeAssistantProps.value = normalizeAssistantOpenProps(options?.assistantProps)
  }
  currentFileResolved.value = false
  taskEndedPromise = null
  const runtimeWorkingDirectory = resolveAssistantStringProp('workingDirectoryValue', '')
  if (runtimeWorkingDirectory) {
    workingDirectory.value = runtimeWorkingDirectory
  }
  const nextScopeKey = resolveSessionScopeKey()
  if (nextScopeKey && nextScopeKey !== currentSessionScopeKey.value) {
    await handleScopeChange(nextScopeKey)
  }
  assistantVisible.value = true
  emit('visibility-change', true)
  const shouldSyncPayload = options?.syncPayload ?? resolveAssistantBooleanProp('syncPayloadOnOpen', true)
  if (!shouldSyncPayload) {
    return
  }
  try {
    const hasPagePayloadOverride = Object.prototype.hasOwnProperty.call(options || {}, 'pagePayload')
    if (hasPagePayloadOverride) {
      await syncPagePayloadToWorkspace(options.pagePayload, 'open')
      return
    }
    await syncPagePayloadToWorkspace(effectivePagePayloadValue.value, 'open')
  } catch (error) {
    // 详细日志：同步页面负载到工作区失败，便于排查网络 / 权限 / 数据格式等问题
    console.error('[openAssistant] syncPagePayloadToWorkspace 失败:', error)
    errorMessage.value = error?.message || '同步页面负载到工作区失败'
  }
}

function closeAssistant() {
  assistantVisible.value = false
  historyDrawerVisible.value = false
  emit('visibility-change', false)
}

function toggleAssistant() {
  if (assistantVisible.value) {
    closeAssistant()
    return
  }
  openAssistant()
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
  const projectPath = effectiveWorkingDirectoryValue.value.trim()
  const currentFileKey = effectiveCurrentFileKey.value.trim()
  const systemPrompt = effectiveSystemPromptValue.value.trim()
  const pagePayloadWorkspacePath = resolvePayloadWorkspacePath()
  const currentFileDirectory = resolveCurrentFileDirectory(currentFileKey)
  const currentFilePath = workingDirectory.value && currentFileKey
    ? `${workingDirectory.value.replace(/[\\/]$/, '')}/${currentFileKey.replace(/^\/+/, '')}`
    : ''
  const contextNotes = [
    projectPath ? `当前项目路径: ${projectPath}` : '',
    currentFileKey ? `当前打开文件: ${currentFileKey}` : '当前打开文件: 未选择',
    `当前文件目录: ${currentFileDirectory}`,
    workingDirectory.value ? `当前项目工作目录: ${workingDirectory.value}` : '',
    currentFilePath ? `当前文件在项目工作目录中的路径: ${currentFilePath}` : '',
    pagePayloadWorkspacePath ? `当前页面负载文件: ${pagePayloadWorkspacePath}` : '',
    '所有读取、搜索、修改、写入都必须限制在当前项目工作目录内。',
    '禁止跨出当前项目工作目录访问根目录下其他一级项目。',
    '当用户提到"改哪里"或"当前文件"时，先读取当前打开文件或当前文件目录下相关文件，再决定修改目标。',
    systemPrompt,
  ].filter(Boolean)
  return {
    contextFiles: currentFileKey ? [currentFileKey] : [],
    contextNotes,
    identities: normalizeIdentityList(effectiveIdentitiesValue.value),
  }
}

function withAssistantRuntimeMeta(payload = {}) {
  return {
    ...payload,
    currentFileKey: effectiveCurrentFileKey.value,
  }
}

async function emitAssistantCallback(name, payload) {
  const handler = resolveAssistantFunctionProp(name)
  if (handler) {
    await handler(payload)
  }
}

async function notifyTaskEnded(reason = 'manual') {
  if (taskEndedPromise) {
    return await taskEndedPromise
  }
  const queryHandler = resolveAssistantFunctionProp('queryWorkspace')
  const targetPath = resolveCurrentWorkspacePath()
  if (!queryHandler || !targetPath) {
    return null
  }
  taskEndedPromise = (async () => {
    const data = await queryHandler(
      buildWorkspaceRequestParams(targetPath, {
        readMode: effectiveTaskEndedReadMode.value,
      }),
    )
    if (!data) {
      return null
    }
    currentFileResolved.value = true
    const eventPayload = withAssistantRuntimeMeta({
      reason,
      path: targetPath,
      readMode: effectiveTaskEndedReadMode.value,
      data,
    })
    emit('task-ended', eventPayload)
    emitSystemAiAssistantEvent('task-ended', eventPayload)
    await emitAssistantCallback('onTaskEnded', eventPayload)
    return eventPayload
  })()
  try {
    return await taskEndedPromise
  } finally {
    taskEndedPromise = null
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
  sessionId,
  selectedSessionId,
  isLoadingSessions,
  isLoadingDetail,
  isSending,
  errorMessage,
  connectionStatus,
  historyEnabled,
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
  historyEnabled: () => effectiveHistoryEnabled.value,
  defaultWorkingDirectory: props.defaultWorkingDirectory,
  openSession: (...args) => resolveAssistantFunctionProp('openSession')?.(...args),
  resumeSession: (...args) => resolveAssistantFunctionProp('resumeSession')?.(...args),
  sendMessage: (...args) => resolveAssistantFunctionProp('sendMessage')?.(...args),
  closeSession: (...args) => resolveAssistantFunctionProp('closeSession')?.(...args),
  listSessions: (...args) => resolveAssistantFunctionProp('listSessions')?.(...args),
  getSessionDetail: (...args) => resolveAssistantFunctionProp('getSessionDetail')?.(...args),
  deleteHistorySession: (...args) => resolveAssistantFunctionProp('deleteHistorySession')?.(...args),
  clearHistorySessions: (...args) => resolveAssistantFunctionProp('clearHistorySessions')?.(...args),
  createEventSource: (...args) => resolveAssistantFunctionProp('createEventSource')?.(...args),
  skillName: () => resolveAssistantStringProp('skillNameValue', ''),
  includeThinking: () => effectiveShowThinking.value,
  welcomeMessage: () => effectiveWelcomeMessage.value,
  welcomeSuggestions: () => effectiveWelcomeSuggestions.value,
  buildRequestContext: buildChatContext,
  filterArchivedSession: matchArchivedSessionScope,
  onBeforeSend: async ({ prompt }) => {
    const beforeSendHandler = resolveAssistantFunctionProp('onBeforeSend')
    if (!beforeSendHandler) {
      return prompt
    }
    return await beforeSendHandler({
      prompt,
      workingDirectory: workingDirectory.value,
      currentFileKey: effectiveCurrentFileKey.value,
      syncPagePayloadToWorkspace: (payload) => syncPagePayloadToWorkspace(payload, 'before-send'),
    })
  },
  onToolCall: async (event) => {
    await emitAssistantCallback('onToolCall', event)
  },
  onFinalSummary: async (event) => {
    await emitAssistantCallback('onFinalSummary', event)
  },
  onTaskAccepted: async (event) => {
    const eventPayload = withAssistantRuntimeMeta(event)
    emit('task-accepted', eventPayload)
    emitSystemAiAssistantEvent('task-accepted', eventPayload)
    await emitAssistantCallback('onTaskAccepted', eventPayload)
  },
  onTaskCompleted: async (event) => {
    const eventPayload = withAssistantRuntimeMeta(event)
    emit('task-completed', eventPayload)
    emitSystemAiAssistantEvent('task-completed', eventPayload)
    await emitAssistantCallback('onTaskCompleted', eventPayload)
    if (effectiveCurrentFileKey.value.trim()) {
      try {
        await notifyTaskEnded('task-completed')
      } catch (error) {
        console.warn('[AiChatWorkspace] notifyTaskEnded (task-completed) 失败:', error)
      }
    }
  },
  onTaskFailed: async (event) => {
    const eventPayload = withAssistantRuntimeMeta(event)
    emit('task-failed', eventPayload)
    emitSystemAiAssistantEvent('task-failed', eventPayload)
    await emitAssistantCallback('onTaskFailed', eventPayload)
  },
  onAssistantFinished: async (event) => {
    const eventPayload = withAssistantRuntimeMeta(event)
    emit('assistant-finished', eventPayload)
    emitSystemAiAssistantEvent('assistant-finished', eventPayload)
    if (effectiveCurrentFileKey.value.trim() && !currentFileResolved.value) {
      try {
        await notifyTaskEnded('assistant-finished')
      } catch (error) {
        console.warn('[AiChatWorkspace] notifyTaskEnded (assistant-finished) 失败:', error)
      }
    }
    await emitAssistantCallback('onAssistantFinished', eventPayload)
  },
})

const messageBlockMap = computed(() =>
  Object.fromEntries(messages.value.map((message) => [message.id, getMessageBlocks(message.content)]))
)
const reasoningBlockMap = computed(() =>
  Object.fromEntries(messages.value.map((message) => [message.id, getMessageBlocks(message.reasoning)]))
)
const shellStyle = computed(() => ({
  height: resolveAssistantStringProp('height', '100%'),
  minHeight: `${resolveAssistantProp('minHeight') ?? 520}px`,
  width: '100%',
  display: 'flex',
  flexDirection: 'column',
  boxSizing: 'border-box',
  padding: '8px 10px 8px',
  background: 'var(--el-bg-color)',
  border: '1px solid var(--el-border-color)',
  boxShadow: 'none',
  position: 'relative',
  top: 'auto',
  right: 'auto',
  transform: 'none',
  zIndex: 'auto',
  maxWidth: '100%',
  maxHeight: 'none',
  overflow: 'hidden',
  borderRadius: '6px',
}))
const compactStatus = computed(() => (isSending.value ? '生成中...' : connectionStatus.value))
const renderShell = computed(() => !(effectiveCollapsible.value && collapsed.value))
const composerContainerStyle = computed(() => ({
  display: 'flex',
  flexDirection: 'column',
  gap: '6px',
  width: '100%',
  paddingTop: '8px',
  paddingBottom: effectiveComposerBottomOffset.value,
  borderTop: '1px solid var(--el-border-color-lighter)',
  boxSizing: 'border-box',
}))

async function scrollMessagesToBottom() {
  await nextTick()
  // 原生 div 滚动容器，ref 直接指向 DOM 元素
  const messageListElement = messageListRef.value
  if (!messageListElement) {
    return
  }
  messageListElement.scrollTop = messageListElement.scrollHeight
}

async function openHistoryDrawer() {
  if (!historyEnabled.value) {
    return
  }
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
  currentFileResolved.value = false
  taskEndedPromise = null
  try {
    await sendPrompt(goal)
    emit('message-sent', goal)
  } catch (error) {
    errorMessage.value = error?.message || '发送前同步上下文失败'
    throw error
  }
}

// 点击建议语句自动发送
function sendSuggestion(suggestionText) {
  if (isSending.value) {
    return
  }
  handleSend(suggestionText)
}

async function handleStartNewSession() {
  if (props.popupMode && !assistantVisible.value) {
    openAssistant()
  }
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
    // 使用原生确认对话框替代 ElMessageBox.confirm
    await showConfirm('确认删除这个会话吗？删除后无法恢复。', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
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
    // 使用原生确认对话框替代 ElMessageBox.confirm
    await showConfirm('确认清空全部历史会话吗？该操作无法恢复。', '清空历史会话', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await clearSessions()
  await loadSessions(selectedSessionId.value)
  historyDrawerVisible.value = false
  emit('session-change', '')
}

function clearErrorMessage() {
  errorMessage.value = ''
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
function hasAssistantContent(message) {
  return !!messageBlockMap.value[message?.id]?.length
}

function shouldShowAssistantThinkingPlaceholder(message) {
  return !!(message?.live && !hasAssistantContent(message) && !shouldShowReasoning(message))
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
    effectiveShowThinking.value
    && message?.live
    && typeof message?.reasoning === 'string'
    && message.reasoning.trim()
  )
}

function isToolMessage(role) {
  return role === 'tool'
}

function getToolSummary(message) {
  const toolName = typeof message?.name === 'string' ? message.name.trim() : ''
  const content = typeof message?.content === 'string' ? message.content.trim() : ''
  if (toolName.includes('文件')) {
    return effectiveFormDisplayName.value || '表单内容'
  }
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

// 对外暴露 API
defineExpose({
  openAssistant,
  closeAssistant,
  toggleAssistant,
  isSending,
  syncPagePayloadToWorkspace,
  notifyTaskEnded,
  sendPrompt: handleSend,
  startNewSession: handleStartNewSession,
  reloadSessions: loadSessions,
  selectSession: handleSelectSession,
})

onMounted(async () => {
  unregisterAssistantController = registerAiAssistantController({
    openAssistant,
    closeAssistant,
    toggleAssistant,
    syncPagePayloadToWorkspace,
  })
  if (props.loadHistoryOnMounted) {
    await hydrateLatestHistory()
  }
  await scrollMessagesToBottom()
})

onBeforeUnmount(() => {
  if (errorMessageTimer) {
    clearTimeout(errorMessageTimer)
    errorMessageTimer = null
  }
  unregisterAssistantController()
})

watch(
  () => errorMessage.value,
  (message) => {
    if (errorMessageTimer) {
      clearTimeout(errorMessageTimer)
      errorMessageTimer = null
    }
    if (!message) {
      return
    }
    errorMessageTimer = setTimeout(() => {
      if (errorMessage.value === message) {
        clearErrorMessage()
      }
      errorMessageTimer = null
    }, 5000)
  }
)

watch(
  () => effectiveWorkingDirectoryValue.value,
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

watch(
  () => sessionId.value,
  (newSessionId) => {
    if (!effectiveWorkingDirectoryValue.value && newSessionId) {
      workingDirectory.value = newSessionId
    }
  },
  {
    immediate: true,
    flush: 'sync',
  }
)

watch(
  () => effectiveWorkingDirectoryValue.value,
  (value) => {
    if (typeof value !== 'string' || !value.trim()) {
      return
    }
    workingDirectory.value = value.trim()
  },
  {
    immediate: true,
  },
)

watch(
  () => assistantVisible.value,
  async (visible) => {
    if (!visible) {
      return
    }
    await scrollMessagesToBottom()
  },
)

</script>
<template>
  <!-- 折叠态 -->
  <div v-if="effectiveCollapsible && collapsed" class="ai-chat-collapsed" @click="toggleCollapsed">
    <!-- 右箭头 SVG 图标 -->
    <svg class="collapsed-icon" viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
      <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
    </svg>
    <span class="collapsed-text">AI</span>
  </div>

  <!-- 主面板 -->
  <div v-if="renderShell" class="ai-chat-shell" :style="shellStyle">
    <div
      style="display: flex; flex-direction: column; width: 100%; height: 100%; min-height: 0; box-sizing: border-box;"
    >
      <!-- 头部 -->
      <div
        v-if="effectiveShowHeader"
        class="chat-header"
        :class="{ compact: effectiveCompactHeader }"
        style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 2px 0 14px; border-bottom: 1px solid var(--el-border-color-lighter);"
      >
        <div
          class="chat-header-body"
          style="display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex: 1; width: 100%; flex-wrap: wrap;"
        >
          <div class="chat-header-title" style="display: flex; flex-direction: column; gap: 4px; justify-content: center;">
            <span class="ai-text-large">{{ displayTitle }}</span>
            <span v-if="displaySubtitle && !effectiveCompactHeader" class="ai-text-info">{{ displaySubtitle }}</span>
          </div>

          <div class="ai-toolbar" style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
            <slot name="toolbar" />
            <button v-if="effectiveCollapsible" class="ai-btn toolbar-secondary-button" @click.stop="toggleCollapsed">
              <!-- 左箭头 SVG 图标 -->
              <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
                <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
              </svg>
            </button>
            <button class="ai-btn ai-btn--primary toolbar-primary-button" @click="handleStartNewSession">新会话</button>
          </div>
        </div>
      </div>

      <!-- 消息区域 -->
      <div style="display: flex; flex: 1; flex-direction: column; gap: 8px; width: 100%; min-height: 0; padding-top: 6px;">
        <!-- 错误提示 -->
        <div
          v-if="errorMessage"
          class="ai-alert ai-alert--error"
          style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; font-size: 13px;"
        >
          <!-- 错误图标 -->
          <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" style="flex-shrink: 0;">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
          <span style="flex: 1;">{{ errorMessage }}</span>
          <button class="ai-alert-close" @click="clearErrorMessage" style="background: none; border: none; cursor: pointer; padding: 0; font-size: 16px; line-height: 1; color: inherit;">&times;</button>
        </div>

        <!-- 消息滚动区 -->
        <div
          ref="messageListRef"
          class="message-scrollbar"
          style="flex: 1; min-height: 0; padding: 2px 0 0; box-sizing: border-box; overflow-y: auto;"
        >
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
              <!-- 头像：AI助手显示机器人图标 -->
              <div
                v-if="showMessageAvatar(message.role)"
                class="ai-avatar"
                :style="{
                  ...getAvatarStyle(message.role),
                  width: '28px',
                  height: '28px',
                  fontSize: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: '6px',
                  fontWeight: 600,
                  userSelect: 'none',
                }"
              >
                <!-- AI机器人头像 SVG -->
                <svg
                  v-if="isAssistantMessage(message.role)"
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <!-- 机器人头部 -->
                  <rect x="3" y="8" width="18" height="13" rx="3" />
                  <!-- 天线 -->
                  <line x1="12" y1="3" x2="12" y2="8" />
                  <circle cx="12" cy="2.5" r="1.2" fill="currentColor" stroke="none" />
                  <!-- 左眼 -->
                  <circle cx="9" cy="14" r="1.5" fill="currentColor" stroke="none" />
                  <!-- 右眼 -->
                  <circle cx="15" cy="14" r="1.5" fill="currentColor" stroke="none" />
                  <!-- 嘴巴 -->
                  <line x1="9.5" y1="18" x2="14.5" y2="18" />
                </svg>
                <!-- 工具消息保留文字标识 -->
                <span v-else>{{ getAvatarLabel(message.role) }}</span>
              </div>

              <!-- 助手消息 -->
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
                            <!-- Loading 旋转 SVG 图标 -->
                            <svg class="reasoning-loading-icon" viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="3">
                              <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-linecap="round" />
                            </svg>
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

                              <!-- 代码卡片 -->
                              <div v-else class="ai-card reasoning-code-card">
                                <div class="ai-card-header" style="padding: 8px 12px; font-size: 12px; font-weight: 600; border-bottom: 1px solid var(--el-border-color-lighter);">
                                  {{ block.language || 'text' }}
                                </div>
                                <div class="ai-card-body" style="padding: 10px 12px; max-height: 200px; overflow-y: auto;">
                                  <pre style="margin: 0;">{{ block.content }}</pre>
                                </div>
                              </div>
                            </template>
                          </div>
                        </template>
                      </div>
                    </transition>

                    <!-- 助手正文内容 -->
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

                        <div v-else class="ai-card" style="border-radius: 6px; border: 1px solid var(--el-border-color-lighter);">
                          <div class="ai-card-header" style="padding: 8px 12px; font-size: 12px; font-weight: 600; border-bottom: 1px solid var(--el-border-color-lighter);">
                            {{ block.language || 'text' }}
                          </div>
                          <div class="ai-card-body" style="padding: 10px 12px; max-height: 240px; overflow-y: auto;">
                            <pre style="margin: 0;">{{ block.content }}</pre>
                          </div>
                        </div>
                      </template>
                    </div>

                    <!-- 欢迎消息建议语句，点击自动发送 -->
                    <div v-if="message.suggestions?.length" style="display: flex; flex-direction: column; gap: 2px; margin-top: 10px;">
                      <template v-for="(suggestion, sIdx) in message.suggestions" :key="`sug-${sIdx}`">
                        <button
                          class="welcome-suggestion-btn"
                          :disabled="isSending"
                          @click="sendSuggestion(suggestion)"
                        >
                          <span class="suggestion-index">{{ sIdx + 1 }}.</span>
                          <span class="suggestion-text">{{ suggestion }}</span>
                          <!-- 箭头图标 -->
                          <svg class="suggestion-arrow" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <line x1="5" y1="12" x2="19" y2="12" />
                            <polyline points="12 5 19 12 12 19" />
                          </svg>
                        </button>
                      </template>
                    </div>
                    </template>

                    <div v-else-if="shouldShowAssistantThinkingPlaceholder(message)" class="assistant-thinking-placeholder">
                      <!-- Loading 旋转 SVG 图标 -->
                      <svg class="reasoning-loading-icon" viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="3">
                        <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-linecap="round" />
                      </svg>
                      <span>正在思考中...</span>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 工具消息 -->
              <template v-else-if="isToolMessage(message.role)">
                <div
                  style="display: flex; align-items: center; gap: 6px; max-width: 88%; padding: 2px 0; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.45; font-weight: 600;"
                >
                  <span style="flex: 0 0 auto; color: var(--el-color-primary); font-size: 13px;">⚙</span>
                  <strong style="flex: 0 0 auto; font-size: 13px; font-weight: 600; color: var(--el-text-color-primary);">{{ message.name || '工具调用' }}</strong>
                  <span style="min-width: 0; color: var(--el-text-color-secondary); font-size: 13px; font-weight: 500;">{{ getToolSummary(message) }}</span>
                </div>
              </template>

              <!-- 用户消息气泡 -->
              <div
                v-else
                class="ai-card message-bubble-card"
                :style="{ ...getMessageCardStyle(message.role), borderRadius: '6px' }"
              >
                <div :style="getMessageBodyStyle(message.role)">
                  <div style="display: flex; flex-direction: column; gap: 6px; width: 100%;">
                    <div
                      v-if="showBubbleHeader(message.role)"
                      style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;"
                      :style="getBubbleHeaderStyle(message.role)"
                    >
                      <span class="ai-tag" :class="`ai-tag--${getRoleTagType(message.role)}`">
                        {{ getRoleLabel(message.role) }}
                      </span>
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

                          <div v-else class="ai-card" style="border-radius: 6px; border: 1px solid var(--el-border-color-lighter);">
                            <div class="ai-card-header" style="padding: 8px 12px; font-size: 12px; font-weight: 600; border-bottom: 1px solid var(--el-border-color-lighter);">
                              {{ block.language || 'text' }}
                            </div>
                            <div class="ai-card-body" style="padding: 10px 12px; max-height: 240px; overflow-y: auto;">
                              <pre style="margin: 0;">{{ block.content }}</pre>
                            </div>
                          </div>
                        </template>
                      </div>
                    </template>

                    <span v-else class="ai-text-info">...</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 加载骨架 -->
            <div v-if="isLoadingDetail" class="ai-skeleton" style="display: flex; flex-direction: column; gap: 8px; padding: 0 6px;">
              <div v-for="i in 4" :key="i" class="ai-skeleton-row" style="height: 14px; border-radius: 4px;" />
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div :style="composerContainerStyle">
          <!-- 工作目录输入 -->
          <div v-if="effectiveShowWorkingDirectory" class="ai-input-group">
            <span class="ai-input-prepend">工作目录</span>
            <input
              v-model="workingDirectory"
              class="ai-input"
              placeholder="请输入工作目录"
            />
          </div>

          <!-- 主输入框 -->
          <textarea
            v-model="inputValue"
            class="chat-input"
            :rows="effectiveComposerRows"
            :placeholder="displayPlaceholder"
            @keydown.ctrl.enter.prevent="handleSend(inputValue)"
          />

          <!-- 状态栏 + 发送按钮 -->
          <div
            style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;"
          >
            <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
              <span class="ai-text-info">{{ compactStatus }}</span>
              <span class="ai-text-info">Ctrl + Enter 发送</span>
            </div>

            <button
              class="ai-btn ai-btn--primary send-button"
              :disabled="!canSend"
              @click="handleSend(inputValue)"
            >
              <!-- Loading 图标 -->
              <svg v-if="isSending" class="ai-btn-spinner" viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="3">
                <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-linecap="round" />
              </svg>
              {{ isSending ? '生成中...' : '发送' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 历史会话抽屉 -->
    <template v-if="historyDrawerVisible">
      <!-- 遮罩层 -->
      <div class="ai-drawer-backdrop" @click="historyDrawerVisible = false" />
      <!-- 抽屉面板 -->
      <div class="ai-drawer-panel" style="width: 42%;">
        <div class="ai-drawer-header">
          <h3 class="ai-drawer-title">历史会话</h3>
          <button class="ai-drawer-close" @click="historyDrawerVisible = false">&times;</button>
        </div>
        <div class="ai-drawer-body">
          <div style="display: flex; flex-direction: column; gap: 16px; width: 100%;">
            <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
              <button class="ai-btn" :disabled="isLoadingSessions" @click="loadSessions(selectedSessionId)">刷新</button>
              <button class="ai-btn" :disabled="!archivedSessions.length" @click="handleClearSessions">清空</button>
            </div>

            <!-- 空态 -->
            <div
              v-if="!archivedSessions.length && !isLoadingSessions"
              class="ai-empty"
            >
              <span class="ai-empty-description">暂无历史会话</span>
            </div>

            <!-- 会话表格 -->
            <table v-else class="ai-table" style="width: 100%;">
              <thead>
                <tr>
                  <th class="ai-table-th" style="min-width: 150px;">会话标题</th>
                  <th class="ai-table-th" style="min-width: 220px;">摘要</th>
                  <th class="ai-table-th" style="width: 180px;">更新时间</th>
                  <th class="ai-table-th" style="width: 170px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in archivedSessions"
                  :key="row.sessionId"
                  class="ai-table-row"
                  :class="{ 'ai-table-row--active': selectedSessionId === row.sessionId }"
                >
                  <td class="ai-table-td" style="min-width: 150px; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    {{ row.title || '未命名会话' }}
                  </td>
                  <td class="ai-table-td" style="min-width: 220px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    {{ row.preview || '暂无摘要' }}
                  </td>
                  <td class="ai-table-td" style="width: 180px;">
                    {{ formatTime(row.updatedAt) }}
                  </td>
                  <td class="ai-table-td" style="width: 170px;">
                    <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                      <button class="ai-btn-link ai-btn-link--primary" @click="handleSelectSession(row.sessionId)">打开</button>
                      <button class="ai-btn-link ai-btn-link--danger" @click="handleDeleteSession(row.sessionId)">删除</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </template>
  </div>

  <!-- 确认对话框（替代 ElMessageBox.confirm） -->
  <template v-if="confirmDialog.visible">
    <div class="ai-confirm-backdrop" />
    <div class="ai-confirm-dialog">
      <div class="ai-confirm-header">
        <h4 class="ai-confirm-title">{{ confirmDialog.title }}</h4>
        <button class="ai-confirm-close" @click="handleConfirmCancel">&times;</button>
      </div>
      <div class="ai-confirm-body">
        <p class="ai-confirm-message">{{ confirmDialog.message }}</p>
      </div>
      <div class="ai-confirm-footer">
        <button class="ai-btn" @click="handleConfirmCancel">{{ confirmDialog.cancelText }}</button>
        <button class="ai-btn ai-btn--danger" @click="handleConfirmOk">{{ confirmDialog.confirmText }}</button>
      </div>
    </div>
  </template>
</template>
<style scoped>
/* ===== 基础变量 ===== */
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

/* ===== 通用按钮 ===== */
.ai-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 4px;
  min-height: 26px;
  padding: 5px 10px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  background: #ffffff;
  color: #303133;
  white-space: nowrap;
  user-select: none;
  transition: all 0.1s ease;
  box-sizing: border-box;
}
.ai-btn:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background-color: #ecf5ff;
}
.ai-btn:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  background-color: #ffffff;
  border-color: #ebeef5;
}
.ai-btn--primary {
  background: #409eff;
  border-color: #409eff;
  color: #ffffff;
}
.ai-btn--primary:hover {
  background: #337ecc;
  border-color: #337ecc;
  color: #ffffff;
}
.ai-btn--danger {
  background: #f56c6c;
  border-color: #f56c6c;
  color: #ffffff;
}
.ai-btn--danger:hover {
  background: #c45656;
  border-color: #c45656;
  color: #ffffff;
}

/* 按钮加载旋转图标 */
.ai-btn-spinner {
  animation: reasoning-spin 1.2s linear infinite;
}

/* 链接按钮 */
.ai-btn-link {
  background: none;
  border: none;
  padding: 2px;
  font-size: 13px;
  cursor: pointer;
  color: inherit;
}
.ai-btn-link:hover {
  text-decoration: underline;
}
.ai-btn-link--primary {
  color: #409eff;
}
.ai-btn-link--danger {
  color: #f56c6c;
}

/* ===== 标签 ===== */
.ai-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  border: 1px solid;
}
.ai-tag--primary {
  color: #409eff;
  background: #ecf5ff;
  border-color: #d9ecff;
}
.ai-tag--success {
  color: #67c23a;
  background: #f0f9eb;
  border-color: #e1f3d8;
}
.ai-tag--info {
  color: #909399;
  background: #f4f4f5;
  border-color: #e9e9eb;
}

/* ===== 文本 ===== */
.ai-text-large {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.ai-text-info {
  font-size: 13px;
  color: #909399;
}

/* ===== 警告提示 ===== */
.ai-alert--error {
  color: #f56c6c;
  background: #fef0f0;
  border: 1px solid #fde2e2;
}
.ai-alert-close {
  opacity: 0.6;
}
.ai-alert-close:hover {
  opacity: 1;
}

/* ===== 卡片 ===== */
.ai-card {
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
}
.ai-card-header {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fafafa;
}
.ai-card-body {
  padding: 10px 12px;
}

/* ===== 输入框组 ===== */
.ai-input-group {
  display: flex;
  align-items: stretch;
  width: 100%;
}
.ai-input-prepend {
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-right: none;
  border-radius: 4px 0 0 4px;
  white-space: nowrap;
}
.ai-input {
  flex: 1;
  padding: 5px 11px;
  font-size: 13px;
  border: 1px solid #dcdfe6;
  border-radius: 0 4px 4px 0;
  outline: none;
  color: #303133;
  background: #ffffff;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.ai-input:focus {
  border-color: #409eff;
}

/* ===== 消息滚动区 ===== */
.message-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.message-scrollbar::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}
.message-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.message-scrollbar {
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc transparent;
}

/* ===== 骨架屏 ===== */
.ai-skeleton-row {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ===== 确认对话框 ===== */
.ai-confirm-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 3000;
}
.ai-confirm-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  z-index: 3001;
  width: 400px;
  max-width: 90vw;
  padding: 20px;
}
.ai-confirm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.ai-confirm-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.ai-confirm-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.ai-confirm-close:hover {
  color: #303133;
}
.ai-confirm-body {
  margin-bottom: 20px;
}
.ai-confirm-message {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}
.ai-confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ===== 抽屉 ===== */
.ai-drawer-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 2000;
}
.ai-drawer-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  box-shadow: -2px 0 12px rgba(0, 0, 0, 0.1);
  z-index: 2001;
  display: flex;
  flex-direction: column;
}
.ai-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}
.ai-drawer-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.ai-drawer-close {
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.ai-drawer-close:hover {
  color: #303133;
}
.ai-drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

/* ===== 表格 ===== */
.ai-table {
  border-collapse: collapse;
  font-size: 13px;
}
.ai-table-th {
  padding: 8px 12px;
  text-align: left;
  font-weight: 600;
  color: #909399;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}
.ai-table-td {
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
}
.ai-table-row {
  cursor: pointer;
  transition: background 0.15s;
}
.ai-table-row:hover {
  background: #f5f7fa;
}
.ai-table-row--active {
  background: #ecf5ff;
}

/* ===== 空态 ===== */
.ai-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  color: #c0c4cc;
}
.ai-empty-description {
  font-size: 13px;
}

/* ===== 聊天框输入 ===== */
.chat-input {
  width: 100%;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  border: 1px solid #dcdfe6;
  outline: none;
  color: #303133;
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.chat-input:focus {
  border-color: #94ceff;
  box-shadow: 0 0 0 1px #94ceff inset;
}

/* ===== 原有样式（保持不变） ===== */
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

.chat-header.compact .ai-text-large {
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
.toolbar-secondary-button:hover {
  background: #ffffff;
  border-color: #c6e2ff;
  color: #409eff;
}

.toolbar-primary-button {
  background: #409eff;
  border-color: #409eff;
  color: #ffffff;
}

.message-bubble-card {
  border-radius: 6px;
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
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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

.send-button {
  background: #409eff;
  border-color: #409eff;
  color: #ffffff;
  font-size: 14px;
  padding: 6px 16px;
  min-height: 32px;
}
.send-button:hover,
.send-button:focus {
  background: #337ecc;
  border-color: #337ecc;
  color: #ffffff;
}

/* 欢迎消息建议链接 */
.welcome-suggestion-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 14px;
  line-height: 1.5;
  color: #6366f1;
  background: none;
  border: none;
  border-bottom: 1px dashed transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
  font-family: inherit;
  width: fit-content;
}
.welcome-suggestion-btn:hover {
  color: #4f46e5;
  border-bottom-color: #4f46e5;
}
.welcome-suggestion-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.suggestion-text {
  min-width: 0;
}

.suggestion-index {
  flex-shrink: 0;
  font-weight: 600;
  color: #a5b4fc;
}
.welcome-suggestion-btn:hover .suggestion-index {
  color: #6366f1;
}

.suggestion-arrow {
  flex-shrink: 0;
  color: #a5b4fc;
  transition: all 0.15s ease;
}
.welcome-suggestion-btn:hover .suggestion-arrow {
  color: #4f46e5;
  transform: translateX(3px);
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

.ai-chat-mask-fade-enter-active,
.ai-chat-mask-fade-leave-active,
.ai-chat-dialog-fade-enter-active,
.ai-chat-dialog-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.ai-chat-mask-fade-enter-from,
.ai-chat-mask-fade-leave-to {
  opacity: 0;
}

.ai-chat-dialog-fade-enter-from,
.ai-chat-dialog-fade-leave-to {
  opacity: 0;
  transform: translateY(calc(-50% + 10px));
}
</style>
