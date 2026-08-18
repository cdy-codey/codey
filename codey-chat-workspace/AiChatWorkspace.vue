<script setup>
// 聊天工作区组件 — 纯原生实现，不依赖任何第三方 UI 组件库。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAiChat } from './useAiChat'
import { formatTime, getMessageBlocks } from './chatPresentation'
import { emitSystemAiAssistantEvent, registerAiAssistantController } from './assistantBridge'
import UserChoiceCard from './UserChoiceCard.vue'

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
    type: [String, Array],
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
  tenantIdValue: {
    type: String,
    default: '',
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
  autoSendOnOpen: {
    type: [Object, String],
    default: null,
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
  onToolExecutionStarted: {
    type: Function,
    default: null,
  },
  onCardAction: {
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
  confirmDecision: {
    type: Function,
    default: null,
  },
  showThinking: {
    type: Boolean,
    default: false,
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
const activeTableModal = ref(null)
// 用户选择补充说明输入
const choiceNote = ref('')
// 深度思考开关，本地状态，默认不启用
const thinkingEnabled = ref(false)

function openTableModal(moduleData) {
  activeTableModal.value = moduleData
}

function closeTableModal() {
  activeTableModal.value = null
}

function openCardTableModal(message) {
  const card = message?.card
  const table = card?.table
  if (!table) return
  const headers = table.headers || []
  const data = (table.rows || []).map((row) =>
    Object.fromEntries(headers.map((header, index) => [header, row[index]])),
  )
  openTableModal({ title: card.title || '表格明细', headers, data, fields: card.fields || [] })
}

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

// 判断是否在写入当前页面的上下文文件（如 context.json）。
// 这类写入的确认已下沉到业务卡片（表格预览 + 确认填入），不应再弹网页中间的模态框。
function isContextFileWrite(event) {
  const toolName = event?.toolName || ''
  if (toolName !== 'write_file' && toolName !== 'edit_file') {
    return false
  }
  const args = event?.arguments || {}
  const rawPath = args.path || args.file || ''
  if (!rawPath) {
    return false
  }
  const fileName = String(rawPath).replace(/\\/g, '/').split('/').pop() || ''
  const contextKey = normalizeWorkspacePath(effectiveCurrentFileKey.value || 'context.json')
  const contextFileName = contextKey.split('/').pop() || ''
  return !!contextFileName && fileName === contextFileName
}

async function handleHumanConfirmation(event) {
  const sessionId = event?.sessionId || ''
  const confirmationId = event?.confirmationId || ''
  const toolName = event?.toolName || ''
  const summary = event?.summary || ''
  const uncertaintyReason = event?.uncertaintyReason || ''

  let approved = false
  if (isContextFileWrite(event)) {
    // 上下文文件写入无需在此弹窗，直接批准；确认动作由业务卡片完成。
    approved = true
  } else {
    const lines = [`AI 准备执行「${toolName || '文件修改'}」操作`]
    if (summary) {
      lines.push(`摘要：${summary}`)
    }
    if (uncertaintyReason) {
      lines.push(`说明：${uncertaintyReason}`)
    }
    lines.push('是否确认执行？')

    try {
      await showConfirm(lines.join('\n'), '操作确认', {
        confirmButtonText: '确认执行',
        cancelButtonText: '取消',
        type: 'warning',
      })
      approved = true
    } catch (error) {
      approved = false
    }
  }

  const confirmDecisionHandler = resolveAssistantFunctionProp('confirmDecision')
  if (!confirmDecisionHandler || !confirmationId) {
    return
  }
  try {
    await confirmDecisionHandler(sessionId, confirmationId, { approved, feedback: '' })
  } catch (error) {
    if (isJsonParseLikeError(error)) {
      console.warn('[AiChatWorkspace] 提交人工确认决策时出现 JSON 解析异常，已忽略:', error)
      return
    }
    console.warn('[AiChatWorkspace] 提交人工确认决策失败:', error)
  }
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

function normalizeAutoSendOnOpenConfig(value) {
  if (typeof value === 'string') {
    const prompt = value.trim()
    return {
      enabled: !!prompt,
      prompt,
      onlyWhenEmpty: true,
    }
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {
      enabled: false,
      prompt: '',
      onlyWhenEmpty: true,
    }
  }
  const prompt = typeof value.prompt === 'string' ? value.prompt.trim() : ''
  return {
    enabled: value.enabled !== false && !!prompt,
    prompt,
    onlyWhenEmpty: value.onlyWhenEmpty !== false,
  }
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
// 深度思考由本地开关控制，prop 提供初始值
const effectiveShowThinking = computed(() => {
  // 如果通过 openAssistant 传入了 assistantProps.showThinking，则采用传入值
  const runtimeValue = runtimeAssistantProps.value?.showThinking
  if (typeof runtimeValue === 'boolean') {
    return runtimeValue
  }
  return thinkingEnabled.value
})
const effectiveWelcomeMessage = computed(() => resolveAssistantStringProp('welcomeMessage', '直接输入任务即可开始。'))
const effectiveWelcomeSuggestions = computed(() => {
  const value = resolveAssistantProp('welcomeSuggestions')
  if (!Array.isArray(value)) {
    return []
  }
  return value.map((item) => {
    if (typeof item === 'string' && item.trim()) {
      const content = item.trim()
      return {
        label: content,
        content,
      }
    }
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      return null
    }
    const label = typeof item.label === 'string' ? item.label.trim() : ''
    const content = typeof item.content === 'string' ? item.content.trim() : ''
    if (!label || !content) {
      return null
    }
    return {
      label,
      content,
    }
  }).filter(Boolean)
})
const effectiveAutoSendOnOpen = computed(() => normalizeAutoSendOnOpenConfig(resolveAssistantProp('autoSendOnOpen')))
const effectiveTenantIdValue = computed(() => resolveAssistantStringProp('tenantIdValue', ''))
const effectiveComposerRows = computed(() => {
  const value = Number(resolveAssistantProp('composerRows'))
  return Number.isFinite(value) && value > 0 ? value : 3
})

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  emit('collapse-change', collapsed.value)
}

// 业务侧主动展开助手面板（如点击“AI 自动填写”按钮时确保聊天可见）
function expandAssistant() {
  if (collapsed.value) {
    collapsed.value = false
    emit('collapse-change', false)
  }
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
  if (hasAssistantPropsOverride) {
    refreshWelcomeMessages()
  }
  const shouldAutoSendAfterOpen = shouldAutoSendOnOpen()
  assistantVisible.value = true
  emit('visibility-change', true)
  const shouldSyncPayload = options?.syncPayload ?? resolveAssistantBooleanProp('syncPayloadOnOpen', true)
  if (shouldSyncPayload) {
    try {
      // 打开即同步页面上下文时，先确保工作会话已经建立，
      // 避免工作区 push 抢在新会话创建之前发出。
      await ensureSessionReady()
      const hasPagePayloadOverride = Object.prototype.hasOwnProperty.call(options || {}, 'pagePayload')
      if (hasPagePayloadOverride) {
        await syncPagePayloadToWorkspace(options.pagePayload, 'open')
      } else {
        await syncPagePayloadToWorkspace(effectivePagePayloadValue.value, 'open')
      }
    } catch (error) {
      // 详细日志：同步页面负载到工作区失败，便于排查网络 / 权限 / 数据格式等问题
      console.error('[openAssistant] syncPagePayloadToWorkspace 失败:', error)
      applyUiError(error, '同步页面负载到工作区失败', 'AiChatWorkspace.openAssistant')
    }
  }
  try {
    await tryAutoSendOnOpen(shouldAutoSendAfterOpen)
  } catch (error) {
    console.error('[openAssistant] autoSendOnOpen 执行失败:', error)
    applyUiError(error, '自动发送首条消息失败', 'AiChatWorkspace.openAssistant')
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
    tenantId: effectiveTenantIdValue.value,
    identities: normalizeIdentityList(effectiveIdentitiesValue.value),
  }
}

function withAssistantRuntimeMeta(payload = {}) {
  return {
    ...payload,
    currentFileKey: effectiveCurrentFileKey.value,
  }
}

function isJsonParseLikeError(error) {
  const message = typeof error?.message === 'string' ? error.message.toLowerCase() : ''
  return !!(
    error instanceof SyntaxError
    || message.includes('json')
    && (
      message.includes('parse')
      || message.includes('unexpected token')
      || message.includes('unexpected end')
      || message.includes('unterminated')
    )
  )
}

function applyUiError(error, fallbackMessage, scope = 'AiChatWorkspace') {
  if (isJsonParseLikeError(error)) {
    console.warn(`[${scope}] 捕获到 JSON 解析异常，已忽略界面提示:`, error)
    return
  }
  errorMessage.value = error?.message || fallbackMessage
}

async function emitAssistantCallback(name, payload) {
  const handler = resolveAssistantFunctionProp(name)
  if (!handler) {
    return
  }
  try {
    await handler(payload)
  } catch (error) {
    if (isJsonParseLikeError(error)) {
      console.warn(`[AiChatWorkspace.${name}] 业务回调发生 JSON 解析异常，已忽略界面提示:`, error)
      return
    }
    throw error
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
  isLiveSession,
  isLoadingSessions,
  isLoadingDetail,
  isSending,
  progress,
  progressVisible,
  progressLabel,
  errorMessage,
  connectionStatus,
  historyEnabled,
  archivedSessions,
  messages,
  canSend,
  loadSessions,
  selectSession,
  sendPrompt,
  submitChoice,
  ensureSessionReady,
  startNewSession,
  resetSessionScope,
  deleteSession,
  clearSessions,
  hydrateLatestHistory,
  refreshWelcomeMessages,
  appendAssistantCard,
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
  skillName: () => resolveAssistantProp('skillNameValue'),
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
    try {
      return await beforeSendHandler({
        prompt,
        workingDirectory: workingDirectory.value,
        currentFileKey: effectiveCurrentFileKey.value,
        syncPagePayloadToWorkspace: (payload) => syncPagePayloadToWorkspace(payload, 'before-send'),
      })
    } catch (error) {
      if (isJsonParseLikeError(error)) {
        console.warn('[AiChatWorkspace.onBeforeSend] 业务回调发生 JSON 解析异常，已继续使用原始 prompt:', error)
        return prompt
      }
      throw error
    }
  },
  onHumanConfirmation: async (event) => {
    await handleHumanConfirmation(event)
  },
  onToolCall: async (event) => {
    await emitAssistantCallback('onToolCall', event)
  },
  onToolExecutionStarted: async (event) => {
    await emitAssistantCallback('onToolExecutionStarted', event)
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
  Object.fromEntries(messages.value.map((message) => {
    const isFinished = message.status === 'FINISH' || message.status === 'ERROR' || message.status === 'STOP' || message.status === 'CANCELED'
    return [message.id, getMessageBlocks(message.content, isFinished ? 'FINISH' : message.status)]
  }))
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
const isPopupMode = computed(() => props.popupMode === true)
const shellVisible = computed(() => (isPopupMode.value ? assistantVisible.value : renderShell.value))
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
const launcherStyle = computed(() => ({
  position: 'fixed',
  right: `${props.popupRight}px`,
  top: props.popupLauncherTop,
  transform: 'translateY(-50%)',
  zIndex: props.popupZIndex,
}))
const popupMaskStyle = computed(() => ({
  position: 'fixed',
  top: '0',
  left: '0',
  right: '0',
  bottom: '0',
  background: 'rgba(15, 23, 42, 0.4)',
  zIndex: props.popupZIndex - 1,
}))
const popupShellStyle = computed(() => ({
  position: 'fixed',
  top: '50%',
  right: `${props.popupRight}px`,
  transform: 'translateY(-50%)',
  width: props.popupWidth,
  height: props.popupHeight,
  maxWidth: '95vw',
  maxHeight: '92vh',
  zIndex: props.popupZIndex,
  display: 'flex',
  flexDirection: 'column',
  boxSizing: 'border-box',
  padding: '8px 10px 8px',
  background: '#ffffff',
  border: '1px solid #e4e7ed',
  boxShadow: '0 24px 64px rgba(15, 23, 42, 0.25)',
  borderRadius: '12px',
  overflow: 'hidden',
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
    applyUiError(error, '发送前同步上下文失败', 'AiChatWorkspace.handleSend')
    if (isJsonParseLikeError(error)) {
      return
    }
    throw error
  }
}

function isWelcomeOnlyViewForAutoSend() {
  return (
    messages.value.length === 1
    && messages.value[0]?.role === 'assistant'
    && !sessionId.value
    && !selectedSessionId.value
    && !isLiveSession.value
    && !isSending.value
    && !isLoadingDetail.value
  )
}

function shouldAutoSendOnOpen() {
  const config = effectiveAutoSendOnOpen.value
  if (!config.enabled || !config.prompt || isSending.value) {
    return false
  }
  // 默认仅在欢迎态首开时自动发送，避免反复打开面板或恢复历史会话时重复提交同一句。
  if (config.onlyWhenEmpty && !isWelcomeOnlyViewForAutoSend()) {
    return false
  }
  return true
}

async function tryAutoSendOnOpen(shouldSend = shouldAutoSendOnOpen()) {
  if (!shouldSend) {
    return
  }
  await nextTick()
  await handleSend(effectiveAutoSendOnOpen.value.prompt)
}

// 点击快捷标签时展示 label，但实际发送 content。
function sendSuggestion(suggestion) {
  if (isSending.value) {
    return
  }
  const content = typeof suggestion === 'string'
    ? suggestion.trim()
    : (typeof suggestion?.content === 'string' ? suggestion.content.trim() : '')
  if (!content) {
    return
  }
  handleSend(content)
}

function getSuggestionIconType(suggestion) {
  const explicitIcon = typeof suggestion?.icon === 'string' ? suggestion.icon.trim().toLowerCase() : ''
  if (explicitIcon) {
    return explicitIcon
  }
  const label = typeof suggestion?.label === 'string' ? suggestion.label.trim() : ''
  const content = typeof suggestion?.content === 'string' ? suggestion.content.trim() : ''
  const text = `${label} ${content}`.toLowerCase()

  if (/挂号|预约|排班|schedule/.test(text)) {
    return 'calendar'
  }
  if (/处方|药|开方|prescription/.test(text)) {
    return 'pill'
  }
  if (/缴费|支付|费用|付款|pay/.test(text)) {
    return 'card'
  }
  if (/报告|结果|发报告|report/.test(text)) {
    return 'document'
  }
  if (/校验|检查|合规|verify|check/.test(text)) {
    return 'shield'
  }
  if (/提取|填写|填表|表单|extract|form/.test(text)) {
    return 'edit'
  }
  return 'spark'
}

async function handleStartNewSession() {
  // 如果当前有活跃会话，先弹出确认对话框提示用户关闭旧会话
  if (sessionId.value && isLiveSession.value) {
    try {
      await showConfirm('当前会话正在进行中，开启新会话将关闭当前会话，是否继续？', '新会话', {
        type: 'warning',
        confirmButtonText: '关闭并新建',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
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

// 用户从选项列表中选择一项，直接将选项文本作为用户输入发送给 AI
function handleUserChoice(optionLabel) {
  if (isSending.value || !optionLabel) {
    return
  }
  const note = choiceNote.value.trim()
  choiceNote.value = ''
  submitChoice(optionLabel, note || '')
}

// 自定义卡片（如"确认填入表单"）的操作回调，交由业务层处理
function handleCardAction(message, action) {
  if (!message?.card || message.card.resolved) {
    return
  }
  message.card.resolved = true
  message.card.action = action
  emitAssistantCallback('onCardAction', {
    action,
    card: message.card,
    messageId: message.id,
  })
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
    gap: '8px',
    width: '100%',
    boxSizing: 'border-box',
    paddingRight: role === 'user' ? '2px' : '0',
    padding: '0',
    background: 'transparent',
    borderLeft: 'none',
  }
}

function getMessageCardStyle(role) {
  if (role === 'user') {
    return {
      width: 'fit-content',
      maxWidth: '62%',
      border: 'none',
      background: 'linear-gradient(135deg, #2f6df6 0%, #2b5de7 100%)',
      color: '#ffffff',
      borderRadius: '18px',
      boxShadow: '0 10px 24px rgba(47, 109, 246, 0.18)',
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
      padding: '10px 14px',
      borderRadius: '18px',
    }
  }
  return {
    padding: '4px 10px',
  }
}

function getAssistantBubbleStyle() {
  return {
    flex: '0 1 auto',
    minWidth: '0',
    maxWidth: '92%',
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
    return '我'
  }
  if (role === 'assistant') {
    return 'AI'
  }
  if (role === 'tool') {
    return 'AI'
  }
  return ''
}

function getAvatarStyle(role) {
  if (role === 'user') {
    return {
      order: 2,
      flex: '0 0 auto',
      background: 'linear-gradient(180deg, #81889a 0%, #667085 100%)',
      color: '#ffffff',
      borderRadius: '999px',
      boxShadow: '0 6px 14px rgba(15, 23, 42, 0.14)',
    }
  }
  if (role === 'tool') {
    return {
      flex: '0 0 auto',
      background: 'linear-gradient(180deg, #86d7ff 0%, #4faeff 100%)',
      color: '#ffffff',
      borderRadius: '999px',
      boxShadow: '0 6px 14px rgba(79, 174, 255, 0.24)',
    }
  }
  return {
    flex: '0 0 auto',
    background: 'linear-gradient(180deg, #86d7ff 0%, #4faeff 100%)',
    color: '#ffffff',
    borderRadius: '999px',
    boxShadow: '0 6px 14px rgba(79, 174, 255, 0.24)',
  }
}

function showMessageAvatar(role) {
  return role === 'assistant' || role === 'tool' || role === 'user'
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

function getToolStatus(message) {
  const content = typeof message?.content === 'string' ? message.content.trim() : ''
  if (!content) {
    return '进行中'
  }
  if (/失败|错误|异常|error/i.test(content)) {
    return '失败'
  }
  return '完成'
}

function toolCallStatusText(toolCall) {
  const status = toolCall?.status || 'done'
  if (status === 'pending') {
    return '待执行'
  }
  if (status === 'running') {
    return '进行中'
  }
  if (status === 'failed') {
    return '失败'
  }
  return '完成'
}

function toolCallStatusClass(toolCall) {
  const status = toolCall?.status || 'done'
  if (status === 'running' || status === 'pending') {
    return 'assistant-tool-call-pill--active'
  }
  if (status === 'failed') {
    return 'assistant-tool-call-pill--failed'
  }
  return ''
}

function isConfirmStyledFormModule(module) {
  if (!module || module.type !== 'object') {
    return false
  }
  const variant = typeof module?.variant === 'string' ? module.variant.trim().toLowerCase() : ''
  const theme = typeof module?.theme === 'string' ? module.theme.trim().toLowerCase() : ''
  if (['confirm', 'confirmation', 'warning', 'highlight'].includes(variant)) {
    return true
  }
  if (['confirm', 'confirmation', 'warning', 'highlight'].includes(theme)) {
    return true
  }
  const title = typeof module?.title === 'string' ? module.title.trim() : ''
  return /确认|预览|待执行|待提交|回执|订单|单据|清单/.test(title)
}

function isConfirmAccentField(fieldLabel) {
  const label = typeof fieldLabel === 'string' ? fieldLabel.trim() : ''
  return /金额|费用|合计|总计|应付|支付|实付/.test(label)
}

// 对外暴露 API
defineExpose({
  openAssistant,
  closeAssistant,
  toggleAssistant,
  toggleCollapsed,
  expandAssistant,
  isSending,
  syncPagePayloadToWorkspace,
  notifyTaskEnded,
  sendPrompt: handleSend,
  startNewSession: handleStartNewSession,
  reloadSessions: loadSessions,
  selectSession: handleSelectSession,
  appendAssistantCard,
})

onMounted(async () => {
  unregisterAssistantController = registerAiAssistantController({
    openAssistant,
    closeAssistant,
    toggleAssistant,
    syncPagePayloadToWorkspace,
    appendAssistantCard,
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
  <!-- 折叠态（仅内联模式） -->
  <div v-if="!isPopupMode && effectiveCollapsible && collapsed" class="ai-chat-collapsed" @click="toggleCollapsed">
    <!-- 右箭头 SVG 图标 -->
    <svg class="collapsed-icon" viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
      <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
    </svg>
    <span class="collapsed-text">AI</span>
  </div>

  <!-- 弹窗模式：悬浮启动器 -->
  <Teleport to="body" v-if="isPopupMode">
    <button
      v-if="!assistantVisible"
      class="ai-chat-launcher"
      :style="launcherStyle"
      :title="launcherHint"
      type="button"
      @click="toggleAssistant"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="8" width="18" height="13" rx="3" />
        <line x1="12" y1="3" x2="12" y2="8" />
        <circle cx="12" cy="2.5" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="9" cy="14" r="1.5" fill="currentColor" stroke="none" />
        <circle cx="15" cy="14" r="1.5" fill="currentColor" stroke="none" />
        <line x1="9.5" y1="18" x2="14.5" y2="18" />
      </svg>
      <span class="ai-chat-launcher-text">{{ launcherText }}</span>
    </button>
  </Teleport>

  <!-- 弹窗模式：遮罩 -->
  <Teleport to="body" v-if="isPopupMode && assistantVisible">
    <div class="ai-chat-popup-mask" :style="popupMaskStyle" @click="closeAssistant" />
  </Teleport>

  <!-- 主面板：弹窗模式 teleport 到 body，内联模式原地渲染 -->
  <Teleport to="body" :disabled="!isPopupMode">
    <div
      v-if="shellVisible"
      class="ai-chat-shell"
      :class="{ 'ai-chat-shell--popup': isPopupMode }"
      :style="isPopupMode ? popupShellStyle : shellStyle"
    >
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
            <!-- 深度思考开关 -->
            <button
              class="ai-btn toolbar-secondary-button thinking-toggle-btn"
              :class="{ 'thinking-toggle-btn--active': effectiveShowThinking }"
              :title="effectiveShowThinking ? '关闭深度思考' : '开启深度思考'"
              @click.stop="thinkingEnabled = !thinkingEnabled"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 1 1 7.072 0l-.548.547A3.374 3.374 0 0 0 14 18.469V19a2 2 0 0 1-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
              <span class="thinking-toggle-label">深度思考</span>
            </button>
            <button v-if="effectiveCollapsible" class="ai-btn toolbar-secondary-button" @click.stop="toggleCollapsed">
              <!-- 左箭头 SVG 图标 -->
              <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
                <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
              </svg>
            </button>
            <button class="ai-btn ai-btn--primary toolbar-primary-button" @click="handleStartNewSession">新会话</button>
            <button v-if="isPopupMode" class="ai-btn toolbar-secondary-button ai-chat-close-btn" title="关闭" @click="closeAssistant">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
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
              <!-- 头像：用户与助手都使用更贴近聊天产品的圆形头像 -->
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
                  borderRadius: '999px',
                  fontWeight: 600,
                  userSelect: 'none',
                }"
              >
                <!-- AI机器人头像 SVG -->
                <svg
                  v-if="isAssistantMessage(message.role) || isToolMessage(message.role)"
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
                <div class="assistant-bubble-shell" :style="getAssistantBubbleStyle()">
                  <div class="assistant-bubble-card">
                    <div
                      v-if="message.toolCalls?.length"
                      class="assistant-tool-call-bar"
                    >
                      <div
                        v-for="(toolCall, toolIndex) in message.toolCalls"
                        :key="`${message.id}-tool-${toolIndex}-${toolCall.name || toolCall}`"
                        class="assistant-tool-call-pill"
                        :class="toolCallStatusClass(toolCall)"
                      >
                        <span
                          v-if="toolCall.status === 'running' || toolCall.status === 'pending'"
                          class="ai-progress-indeterminate"
                          role="progressbar"
                          aria-label="工具运行中"
                        ></span>
                        <svg
                          v-else-if="toolCall.status === 'failed'"
                          viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        >
                          <path d="M18 6 6 18M6 6l12 12" />
                        </svg>
                        <svg
                          v-else
                          viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        >
                          <path d="M20 6 9 17l-5-5" />
                        </svg>
                        <span class="assistant-tool-call-name">{{ toolCall.name || toolCall }}</span>
                        <span class="assistant-tool-call-status">{{ toolCallStatusText(toolCall) }}</span>
                      </div>
                    </div>

                    <!-- 自定义卡片（如：确认填入表单） -->
                    <div v-if="message.card" class="ai-fill-confirm-card">
                      <div class="ai-fill-confirm-header">
                        <span>{{ message.card.title || '请确认' }}</span>
                      </div>
                      <div v-if="message.card.summary" class="ai-fill-confirm-summary">
                        {{ message.card.summary }}
                      </div>
                      <div v-if="message.card.fields?.length" class="ai-fill-confirm-fields">
                        <div
                          v-for="(item, idx) in message.card.fields"
                          :key="idx"
                          class="ai-fill-confirm-field"
                        >
                          <span class="ai-fill-confirm-field-label">{{ item.label }}</span>
                          <span class="ai-fill-confirm-field-value">{{ item.value }}</span>
                        </div>
                      </div>
                      <div v-if="message.card.table" class="ai-fill-confirm-table">
                        <div class="ai-fill-confirm-table-bar">
                          <button
                            type="button"
                            class="ai-btn-link ai-btn-link--primary"
                            @click="openCardTableModal(message)"
                          >
                            查看完整表格
                          </button>
                        </div>
                        <table class="ai-form-table ai-markdown-table">
                          <thead>
                            <tr>
                              <th
                                v-for="(header, headerIndex) in message.card.table.headers"
                                :key="headerIndex"
                                class="ai-table-th"
                              >
                                {{ header }}
                              </th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr
                              v-for="(row, rowIndex) in message.card.table.rows"
                              :key="rowIndex"
                              class="ai-table-row"
                            >
                              <td
                                v-for="(header, cellIndex) in message.card.table.headers"
                                :key="cellIndex"
                                class="ai-table-td"
                              >
                                {{ row[cellIndex] }}
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                      <div class="ai-fill-confirm-actions">
                        <button
                          type="button"
                          class="ai-fill-confirm-btn ai-fill-confirm-btn--primary"
                          :disabled="message.card.resolved || isSending"
                          @click="handleCardAction(message, 'confirm')"
                        >
                          {{ message.card.confirmText || '确认填入' }}
                        </button>
                        <button
                          type="button"
                          class="ai-fill-confirm-btn"
                          :disabled="message.card.resolved || isSending"
                          @click="handleCardAction(message, 'cancel')"
                        >
                          {{ message.card.cancelText || '取消' }}
                        </button>
                      </div>
                      <div v-if="message.card.resolved" class="ai-fill-confirm-result">
                        {{ message.card.action === 'confirm' ? '已确认填入' : '已取消' }}
                      </div>
                    </div>

                    <transition name="reasoning-fade">
                      <div
                        v-if="shouldShowReasoning(message)"
                        class="reasoning-panel"
                      >
                        <div class="reasoning-header">
                          <div class="reasoning-title">
                            <span>思考中</span>
                          </div>
                          <span class="reasoning-tip">完成后自动收起</span>
                        </div>
                        <div class="ai-progress">
                          <div class="ai-progress-track">
                            <div class="ai-progress-bar" :style="{ width: `${progress}%` }"></div>
                          </div>
                          <div class="ai-progress-meta">
                            <span class="ai-progress-label">{{ progressLabel || '正在处理...' }}</span>
                            <span class="ai-progress-value">{{ progress }}%</span>
                          </div>
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
                          class="assistant-text-block"
                        >
                          <div
                            v-for="(paragraph, paragraphIndex) in block.paragraphs"
                            :key="`${message.id}-${blockIndex}-${paragraphIndex}`"
                            style="margin-bottom: 6px; white-space: pre-wrap;"
                          >
                            {{ paragraph }}
                          </div>
                        </div>

                        <div
                          v-else-if="block.type === 'heading'"
                          class="ai-markdown-heading"
                          :class="`ai-markdown-heading--${block.level || 2}`"
                        >
                          {{ block.text }}
                        </div>

                        <div
                          v-else-if="block.type === 'divider'"
                          class="ai-markdown-divider"
                        />

                        <div
                          v-else-if="block.type === 'table'"
                          class="ai-markdown-table-card ai-card"
                        >
                          <div class="ai-markdown-table-scroll">
                            <table class="ai-form-table ai-markdown-table">
                              <thead>
                                <tr>
                                  <th
                                    v-for="(header, headerIndex) in block.headers || []"
                                    :key="`${message.id}-${blockIndex}-header-${headerIndex}`"
                                    class="ai-table-th"
                                  >
                                    {{ header }}
                                  </th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr
                                  v-for="(row, rowIndex) in block.rows || []"
                                  :key="`${message.id}-${blockIndex}-row-${rowIndex}`"
                                  class="ai-table-row"
                                >
                                  <td
                                    v-for="(header, cellIndex) in block.headers || []"
                                    :key="`${message.id}-${blockIndex}-row-${rowIndex}-cell-${cellIndex}`"
                                    class="ai-table-td"
                                  >
                                    {{ row[cellIndex] }}
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        </div>

                        <!-- 用户选择交互块 (user_choice) -->
                        <UserChoiceCard
                          v-else-if="block.type === 'user_choice'"
                          v-model="choiceNote"
                          :title="block.title"
                          :description="block.description"
                          :options="block.options"
                          :disabled="isSending"
                          @select="handleUserChoice"
                        />

                        <div v-else-if="block.parsedUiView" class="ai-form-data-container">
                          <div v-if="block.parsedUiView.streaming" class="ai-form-streaming-placeholder" style="padding: 12px 0;">
                            <div class="ai-progress">
                              <div class="ai-progress-track">
                                <div class="ai-progress-bar" :style="{ width: `${progress}%` }"></div>
                              </div>
                              <div class="ai-progress-meta">
                                <span class="ai-progress-label">{{ progressLabel || '正在生成表单...' }}</span>
                                <span class="ai-progress-value">{{ progress }}%</span>
                              </div>
                            </div>
                          </div>
                          <template v-else-if="block.parsedUiView._view_type === 'form_data'">
                            <div
                              v-if="typeof block.parsedUiView.summary === 'string' && block.parsedUiView.summary.trim()"
                              class="ai-form-summary ai-card"
                            >
                              <div class="ai-card-header ai-form-module-header ai-form-summary-header">
                                简要解析
                              </div>
                              <div class="ai-card-body ai-form-module-body ai-form-summary-body">
                                {{ block.parsedUiView.summary }}
                              </div>
                            </div>
                            <template v-for="(module, mIdx) in block.parsedUiView.modules" :key="mIdx">
                            <!-- Object View -->
                            <div
                              v-if="module.type === 'object'"
                              :class="[
                                'ai-form-module',
                                'ai-card',
                                'ai-form-object-module',
                                { 'ai-form-object-module--confirm': isConfirmStyledFormModule(module) },
                              ]"
                            >
                              <div
                                v-if="module.title"
                                :class="[
                                  'ai-card-header',
                                  'ai-form-module-header',
                                  'ai-form-object-header',
                                  { 'ai-form-object-header--confirm': isConfirmStyledFormModule(module) },
                                ]"
                              >
                                <svg
                                  v-if="isConfirmStyledFormModule(module)"
                                  viewBox="0 0 24 24"
                                  width="1em"
                                  height="1em"
                                  fill="currentColor"
                                >
                                  <path d="M12 2l7 3v6c0 5.25-3.438 9.375-7 10.8C8.438 20.375 5 16.25 5 11V5l7-3zm-1 11.586l5.293-5.293 1.414 1.414L11 16.414l-3.707-3.707 1.414-1.414L11 13.586z" />
                                </svg>
                                <svg v-else viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
                                  <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                                </svg>
                                <span class="ai-form-module-title">{{ module.title }}</span>
                              </div>
                              <div
                                :class="[
                                  'ai-card-body',
                                  'ai-form-module-body',
                                  'ai-form-object-body',
                                  { 'ai-form-object-body--confirm': isConfirmStyledFormModule(module) },
                                ]"
                              >
                                <div
                                  v-for="(val, key) in module.data"
                                  :key="key"
                                  :class="[
                                    'ai-form-field',
                                    { 'ai-form-field--accent': isConfirmAccentField(key) },
                                  ]"
                                >
                                  <div class="ai-form-field-label">{{ key }}</div>
                                  <div class="ai-form-field-value">{{ val }}</div>
                                </div>
                              </div>
                            </div>
                            <!-- List View -->
                            <div v-else-if="module.type === 'list'" class="ai-form-module ai-card ai-form-list-module">
                              <div class="ai-card-header ai-form-module-header ai-form-list-header" v-if="module.title">
                                <div style="display: flex; align-items: center; gap: 6px;">
                                  <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor">
                                    <path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z" />
                                  </svg>
                                  <span class="ai-form-module-title">{{ module.title }}</span>
                                </div>
                                <button class="ai-btn-link ai-btn-link--primary" style="font-size: 12px;" @click="openTableModal(module)">查看更多</button>
                              </div>
                              <div class="ai-card-body ai-form-module-body ai-form-list-body">
                                <table class="ai-form-table ai-form-table--compact">
                                  <thead>
                                    <tr>
                                      <th v-for="h in (module.headers || []).slice(0, 6)" :key="h" class="ai-table-th">{{ h }}</th>
                                      <th v-if="(module.headers || []).length > 6" class="ai-table-th ai-table-th--ellipsis">...</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    <tr v-for="(row, rIdx) in (module.data || [])" :key="rIdx" class="ai-table-row">
                                      <td v-for="h in (module.headers || []).slice(0, 6)" :key="h" class="ai-table-td ai-table-td--truncate">{{ row[h] }}</td>
                                      <td v-if="(module.headers || []).length > 6" class="ai-table-td ai-table-td--ellipsis">...</td>
                                    </tr>
                                  </tbody>
                                </table>
                                <div v-if="(module.headers || []).length > 6" class="ai-form-table-more" @click="openTableModal(module)">
                                  共 {{ module.headers.length }} 个字段，点击查看完整表格
                                </div>
                              </div>
                            </div>
                            </template>
                          </template>
                          <div v-else-if="block.parsedUiView._view_type === 'diff_data'" class="ai-form-module ai-card ai-form-diff-module">
                            <div class="ai-card-header ai-form-module-header ai-form-diff-header">
                              {{ block.parsedUiView.title || '变更对比' }}
                            </div>
                            <div class="ai-card-body ai-form-module-body ai-form-diff-body">
                              <div v-for="(change, cIdx) in (block.parsedUiView.changes || [])" :key="cIdx" class="ai-form-diff-row">
                                <div class="ai-form-diff-field">{{ change.field }}</div>
                                <div class="ai-form-diff-old">{{ change.old_value }}</div>
                                <div class="ai-form-diff-arrow">→</div>
                                <div class="ai-form-diff-new">{{ change.new_value }}</div>
                              </div>
                            </div>
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

                    <div v-else-if="shouldShowAssistantThinkingPlaceholder(message)" class="assistant-thinking-placeholder">
                      <div class="ai-progress">
                        <div class="ai-progress-track">
                          <div class="ai-progress-bar" :style="{ width: `${progress}%` }"></div>
                        </div>
                        <div class="ai-progress-meta">
                          <span class="ai-progress-label">{{ progressLabel || '正在处理...' }}</span>
                          <span class="ai-progress-value">{{ progress }}%</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 工具消息 -->
              <template v-else-if="isToolMessage(message.role)">
                <div class="tool-message-card">
                  <div class="tool-message-badge">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14.7 6.3a4 4 0 0 0-5.4 5.4l-5.8 5.8a1 1 0 0 0 0 1.4l1.2 1.2a1 1 0 0 0 1.4 0l5.8-5.8a4 4 0 0 0 5.4-5.4l-2.2 2.2-2-2 2.6-2.8z" />
                    </svg>
                    <span class="tool-message-name">{{ message.name || '工具调用' }}</span>
                    <span class="tool-message-status">{{ getToolStatus(message) }}</span>
                  </div>
                  <div class="tool-message-text">{{ getToolSummary(message) }}</div>
                </div>
              </template>

              <!-- 用户消息气泡 -->
              <div
                v-else
                class="ai-card message-bubble-card"
                :style="getMessageCardStyle(message.role)"
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
                              style="margin-bottom: 6px; white-space: pre-wrap;"
                            >
                              {{ paragraph }}
                            </div>
                          </div>

                          <div
                            v-else-if="block.type === 'heading'"
                            class="ai-markdown-heading"
                            :class="`ai-markdown-heading--${block.level || 2}`"
                          >
                            {{ block.text }}
                          </div>

                          <div
                            v-else-if="block.type === 'divider'"
                            class="ai-markdown-divider"
                          />

                          <div
                            v-else-if="block.type === 'table'"
                            class="ai-markdown-table-card ai-card"
                          >
                            <div class="ai-markdown-table-scroll">
                              <table class="ai-form-table ai-markdown-table">
                                <thead>
                                  <tr>
                                    <th
                                      v-for="(header, headerIndex) in block.headers || []"
                                      :key="`${message.id}-${blockIndex}-user-header-${headerIndex}`"
                                      class="ai-table-th"
                                    >
                                      {{ header }}
                                    </th>
                                  </tr>
                                </thead>
                                <tbody>
                                  <tr
                                    v-for="(row, rowIndex) in block.rows || []"
                                    :key="`${message.id}-${blockIndex}-user-row-${rowIndex}`"
                                    class="ai-table-row"
                                  >
                                    <td
                                      v-for="(header, cellIndex) in block.headers || []"
                                      :key="`${message.id}-${blockIndex}-user-row-${rowIndex}-cell-${cellIndex}`"
                                      class="ai-table-td"
                                    >
                                      {{ row[cellIndex] }}
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
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

          <div v-if="effectiveWelcomeSuggestions.length" class="welcome-suggestion-list composer-suggestion-list">
            <template v-for="(suggestion, sIdx) in effectiveWelcomeSuggestions" :key="`composer-sug-${sIdx}-${suggestion.label || suggestion}`">
              <button
                class="welcome-suggestion-btn"
                :disabled="isSending"
                @click="sendSuggestion(suggestion)"
              >
                <span class="suggestion-icon" :class="`suggestion-icon--${getSuggestionIconType(suggestion)}`">
                  <svg
                    v-if="getSuggestionIconType(suggestion) === 'calendar'"
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <rect x="3" y="5" width="18" height="16" rx="3" />
                    <line x1="16" y1="3" x2="16" y2="7" />
                    <line x1="8" y1="3" x2="8" y2="7" />
                    <line x1="3" y1="11" x2="21" y2="11" />
                  </svg>
                  <svg
                    v-else-if="getSuggestionIconType(suggestion) === 'pill'"
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M10.5 13.5 20 4a4.243 4.243 0 0 0-6-6l-9.5 9.5a4.243 4.243 0 1 0 6 6Z" transform="translate(0 4)" />
                    <line x1="9" y1="15" x2="15" y2="9" />
                  </svg>
                  <svg
                    v-else-if="getSuggestionIconType(suggestion) === 'card'"
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <rect x="3" y="5" width="18" height="14" rx="3" />
                    <line x1="3" y1="10" x2="21" y2="10" />
                    <line x1="7" y1="15" x2="11" y2="15" />
                  </svg>
                  <svg
                    v-else-if="getSuggestionIconType(suggestion) === 'document'"
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 3 14 8 19 8" />
                    <line x1="9" y1="13" x2="15" y2="13" />
                    <line x1="9" y1="17" x2="13" y2="17" />
                  </svg>
                  <svg
                    v-else-if="getSuggestionIconType(suggestion) === 'shield'"
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M12 3l7 3v5c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V6l7-3z" />
                    <path d="m9.5 12 1.7 1.7L14.8 10" />
                  </svg>
                  <svg
                    v-else-if="getSuggestionIconType(suggestion) === 'edit'"
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M12 20h9" />
                    <path d="M16.5 3.5a2.121 2.121 0 1 1 3 3L7 19l-4 1 1-4 12.5-12.5Z" />
                  </svg>
                  <svg
                    v-else
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z" />
                  </svg>
                </span>
                <span class="suggestion-text">{{ suggestion.label || suggestion }}</span>
              </button>
            </template>
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
              {{ isSending ? `生成中 ${progress}%` : '发送' }}
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
  </Teleport>

  <!-- 确认对话框（替代 ElMessageBox.confirm） -->
  <Teleport to="body">
    <template v-if="confirmDialog.visible">
      <div class="ai-confirm-dialog" style="z-index: 9999;">
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
  </Teleport>

  <!-- 表单数据弹窗（查看更多） -->
  <Teleport to="body">
    <template v-if="activeTableModal">
      <div class="ai-confirm-backdrop" @click="closeTableModal" style="z-index: 9998;" />
      <div class="ai-confirm-dialog" style="width: fit-content; min-width: 600px; max-width: 95vw; max-height: 85vh; display: flex; flex-direction: column; z-index: 9999;">
        <div class="ai-confirm-header" style="margin-bottom: 0; padding-bottom: 12px; border-bottom: 1px solid var(--el-border-color-lighter);">
          <h4 class="ai-confirm-title">{{ activeTableModal.title || '数据列表' }}</h4>
          <button class="ai-confirm-close" @click="closeTableModal">&times;</button>
        </div>
        <div class="ai-confirm-body" style="flex: 1; overflow: auto; padding-top: 16px; margin-bottom: 0;">
          <div v-if="activeTableModal.fields?.length" class="ai-table-modal-fields">
            <div v-for="(item, idx) in activeTableModal.fields" :key="idx" class="ai-table-modal-field">
              <span class="ai-table-modal-field-label">{{ item.label }}</span>
              <span class="ai-table-modal-field-value">{{ item.value }}</span>
            </div>
          </div>
          <table class="ai-form-table" style="width: 100%; border-collapse: collapse; font-size: 13px;">
            <thead>
              <tr>
                <th v-for="h in activeTableModal.headers" :key="h" class="ai-table-th" style="padding: 8px 16px; text-align: left; background: #f8fafc; color: #64748b; font-weight: 500; border-bottom: 1px solid #e2e8f0; white-space: nowrap; position: sticky; top: 0; z-index: 1;">{{ h }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rIdx) in activeTableModal.data" :key="rIdx" class="ai-table-row">
                <td v-for="h in activeTableModal.headers" :key="h" class="ai-table-td" style="padding: 10px 16px; border-bottom: 1px solid #e2e8f0; color: #334155; white-space: nowrap;">{{ row[h] }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </Teleport>
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

.ai-markdown-heading {
  margin: 4px 0 2px;
  color: #1e293b;
  font-weight: 700;
  line-height: 1.5;
}

.ai-markdown-heading--1 {
  font-size: 20px;
}

.ai-markdown-heading--2 {
  font-size: 18px;
}

.ai-markdown-heading--3 {
  font-size: 16px;
}

.ai-markdown-heading--4,
.ai-markdown-heading--5,
.ai-markdown-heading--6 {
  font-size: 14px;
}

.ai-markdown-divider {
  width: 100%;
  height: 1px;
  margin: 4px 0 8px;
  background: linear-gradient(90deg, #e2e8f0 0%, #cbd5e1 100%);
}

.ai-markdown-table-card {
  border-radius: 8px;
}

.ai-markdown-table-scroll {
  overflow-x: auto;
}

.ai-markdown-table {
  width: 100%;
  min-width: max-content;
  border-collapse: collapse;
  font-size: 13px;
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
  width: 240px;
  max-width: 100%;
  padding: 4px 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.4;
}

.ai-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-progress-track {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.ai-progress-bar {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #2f6df6 0%, #2b5de7 100%);
  transition: width 0.2s ease;
}

.ai-progress-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-progress-label {
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-progress-value {
  flex-shrink: 0;
  color: #2f6df6;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 不确定进度条：用于无法给出具体百分比、但需要表达“仍在运行”的局部指示。 */
.ai-progress-indeterminate {
  position: relative;
  flex-shrink: 0;
  width: 16px;
  height: 3px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.ai-progress-indeterminate::after {
  content: '';
  position: absolute;
  top: 0;
  left: -50%;
  width: 50%;
  height: 100%;
  border-radius: 999px;
  background: #2563eb;
  animation: ai-progress-slide 1.1s ease-in-out infinite;
}

@keyframes ai-progress-slide {
  from { transform: translateX(0); }
  to { transform: translateX(320%); }
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

/* 深度思考开关按钮 */
.thinking-toggle-btn {
  transition: all 0.2s ease;
}
.thinking-toggle-btn--active {
  color: #e6a23c;
  border-color: #f5dab1;
  background: #fdf6ec;
}
.thinking-toggle-btn--active:hover {
  color: #e6a23c;
  border-color: #e6a23c;
  background: #fdf6ec;
}
.thinking-toggle-label {
  font-size: 12px;
  font-weight: 500;
}

.assistant-bubble-shell {
  width: 100%;
}

.assistant-bubble-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #e7ebf3;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.assistant-tool-call-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 2px;
}

.assistant-tool-call-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 6px 10px;
  color: #2563eb;
  background: linear-gradient(180deg, #eef5ff 0%, #e7f0ff 100%);
  border: 1px solid #c6d8ff;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  font-weight: 600;
}

.assistant-tool-call-pill--active {
  color: #b45309;
  background: linear-gradient(180deg, #fefce8 0%, #fef3c7 100%);
  border-color: #fde68a;
}

.assistant-tool-call-pill--failed {
  color: #dc2626;
  background: linear-gradient(180deg, #fef2f2 0%, #fee2e2 100%);
  border-color: #fecaca;
}

.ai-fill-confirm-card {
  margin: 8px 0;
  background: #ffffff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  overflow: hidden;
}

.ai-fill-confirm-header {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #1d4ed8;
  background: #eff6ff;
  border-bottom: 1px solid #bfdbfe;
}

.ai-fill-confirm-summary {
  padding: 10px 12px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
  border-bottom: 1px solid #e2e8f0;
}

.ai-fill-confirm-fields {
  padding: 6px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.ai-fill-confirm-field {
  display: flex;
  gap: 12px;
  padding: 5px 0;
  font-size: 13px;
  line-height: 1.5;
}

.ai-fill-confirm-field-label {
  flex-shrink: 0;
  min-width: 72px;
  color: #94a3b8;
}

.ai-fill-confirm-field-value {
  color: #1e293b;
  word-break: break-all;
}

.ai-fill-confirm-table {
  padding: 8px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.ai-fill-confirm-table-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-bottom: 4px;
}

.ai-table-modal-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 0 16px 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.ai-table-modal-field {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.ai-table-modal-field-label {
  flex-shrink: 0;
  color: #94a3b8;
}

.ai-table-modal-field-value {
  color: #1e293b;
  font-weight: 500;
}

.ai-fill-confirm-actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
}

.ai-fill-confirm-btn {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s ease;
}

.ai-fill-confirm-btn:hover:not(:disabled) {
  background: #f1f5f9;
}

.ai-fill-confirm-btn--primary {
  color: #ffffff;
  background: #2563eb;
  border-color: #2563eb;
}

.ai-fill-confirm-btn--primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.ai-fill-confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-fill-confirm-result {
  padding: 6px 12px 10px;
  font-size: 12px;
  color: #16a34a;
}

.assistant-tool-call-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-tool-call-status {
  color: #64748b;
  font-weight: 500;
}

.assistant-text-block {
  color: #1f2937;
  line-height: 1.8;
  font-size: 15px;
}

.message-bubble-card {
  border-radius: 18px;
  overflow: visible;
}

.tool-message-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 92%;
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #e7ebf3;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.tool-message-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  padding: 6px 10px;
  color: #2563eb;
  background: linear-gradient(180deg, #eef5ff 0%, #e3eeff 100%);
  border: 1px solid #c9dafd;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
}

.tool-message-name {
  color: #2563eb;
}

.tool-message-status {
  color: #64748b;
  font-weight: 500;
}

.tool-message-text {
  color: #1f2937;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.ai-form-data-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-form-summary {
  margin-bottom: 0;
  border-radius: 14px;
}

.ai-form-summary-header {
  padding: 10px 12px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}

.ai-form-summary-body {
  padding: 10px 12px;
  color: #475569;
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.ai-form-module {
  margin-bottom: 0;
  border-radius: 16px;
}

.ai-form-object-header,
.ai-form-list-header,
.ai-form-diff-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 600;
  background: #f0f4ff;
}

.ai-form-list-header {
  justify-content: space-between;
}

.ai-form-object-header--confirm {
  color: #ffffff;
  background: linear-gradient(180deg, #ff9f18 0%, #f08300 100%);
  border-bottom-color: rgba(255, 255, 255, 0.18);
}

.ai-form-object-body,
.ai-form-diff-body {
  padding: 10px 12px;
}

.ai-form-object-body--confirm {
  padding: 0 12px 10px;
  background: linear-gradient(180deg, #fff7e7 0%, #fffdf7 100%);
}

.ai-form-field {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed #e6edf6;
}

.ai-form-field:last-child {
  border-bottom: none;
}

.ai-form-object-body--confirm .ai-form-field {
  border-bottom-color: #f2d486;
}

.ai-form-field-label {
  width: 140px;
  flex-shrink: 0;
  color: #64748b;
  font-size: 13px;
}

.ai-form-object-body--confirm .ai-form-field-label {
  color: #9a5b00;
}

.ai-form-field-value {
  flex: 1;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  text-align: right;
  word-break: break-word;
}

.ai-form-field--accent .ai-form-field-label,
.ai-form-field--accent .ai-form-field-value {
  color: #d97706;
  font-size: 15px;
  font-weight: 700;
}

.ai-form-list-body {
  padding: 0;
  overflow-x: auto;
}

.ai-form-table--compact {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.ai-table-th--ellipsis,
.ai-table-td--ellipsis {
  text-align: center;
  color: #94a3b8;
}

.ai-table-td--truncate {
  max-width: 150px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-form-table-more {
  padding: 10px;
  text-align: center;
  color: #3b82f6;
  font-size: 12px;
  cursor: pointer;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
}

.ai-form-diff-row {
  display: grid;
  grid-template-columns: 120px 1fr 20px 1fr;
  gap: 8px;
  align-items: start;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.ai-form-diff-row:last-child {
  border-bottom: none;
}

.ai-form-diff-field {
  color: #64748b;
  font-size: 13px;
}

.ai-form-diff-old {
  color: #94a3b8;
  font-size: 13px;
  text-decoration: line-through;
  word-break: break-word;
}

.ai-form-diff-arrow {
  color: #cbd5e1;
  text-align: center;
}

.ai-form-diff-new {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  word-break: break-word;
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

.message-row {
  margin: 1px 0;
}

.assistant-message-row {
  margin-top: 14px;
}

.tool-message-row {
  margin-top: 10px;
  margin-bottom: 2px;
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

.welcome-suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.composer-suggestion-list {
  margin-top: 0;
  margin-bottom: 10px;
}

/* 欢迎消息快捷标签 */
.welcome-suggestion-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  line-height: 1.2;
  color: #4b5563;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  border: 1px solid #d9e0ea;
  border-radius: 999px;
  cursor: pointer;
  text-align: center;
  transition: all 0.15s ease, box-shadow 0.15s ease;
  font-family: inherit;
  width: auto;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
}
.welcome-suggestion-btn:hover {
  color: #334155;
  border-color: #c8d3e1;
  background: linear-gradient(180deg, #ffffff 0%, #f3f6fa 100%);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}
.welcome-suggestion-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.suggestion-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  border-radius: 4px;
}

.suggestion-icon--calendar {
  color: #64748b;
}

.suggestion-icon--pill {
  color: #f59e0b;
}

.suggestion-icon--card {
  color: #eab308;
}

.suggestion-icon--document {
  color: #94a3b8;
}

.suggestion-icon--shield {
  color: #2563eb;
}

.suggestion-icon--edit {
  color: #3b82f6;
}

.suggestion-icon--spark {
  color: #8b5cf6;
}

.suggestion-text {
  min-width: 0;
  font-weight: 500;
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

/* ===== 用户选择按钮 ===== */

/* ===== 弹窗模式 ===== */
.ai-chat-launcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #2f6df6 0%, #2b5de7 100%);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(47, 109, 246, 0.28);
  transition: box-shadow 0.15s ease, filter 0.15s ease;
}
.ai-chat-launcher:hover {
  box-shadow: 0 14px 30px rgba(47, 109, 246, 0.36);
  filter: brightness(1.04);
}
.ai-chat-launcher-text {
  white-space: nowrap;
}

.ai-chat-popup-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.4);
}

.ai-chat-shell--popup {
  border-radius: 12px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.25);
}
</style>
