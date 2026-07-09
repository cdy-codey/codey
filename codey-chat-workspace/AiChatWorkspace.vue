<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Loading, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
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
    default: '../',
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
  // 去掉多余前缀（./ 或 workspace/），只保留实际目录层级
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
  // 去掉多余前缀（./ 或 workspace/），只保留实际目录层级
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
    // 仅在外部显式传入 assistantProps 时覆盖运行时上下文，
    // 避免组件内部重新打开或新建会话时把页面传入的工作目录、工作文件等信息清空。
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
    // 工作目录和上次不同则立即重置会话，后续发送会自动走新建会话流程，避免复用旧 sessionId。
    await handleScopeChange(nextScopeKey)
  }
  assistantVisible.value = true
  emit('visibility-change', true)
  const shouldSyncPayload = options?.syncPayload ?? resolveAssistantBooleanProp('syncPayloadOnOpen', true)
  if (!shouldSyncPayload) {
    return
  }
  try {
    // AI修改：每次打开助手时，把当前页面负载同步到工作区文件，便于模型直接读取最新上下文。
    const hasPagePayloadOverride = Object.prototype.hasOwnProperty.call(options || {}, 'pagePayload')
    if (hasPagePayloadOverride) {
      await syncPagePayloadToWorkspace(options.pagePayload, 'open')
      return
    }
    await syncPagePayloadToWorkspace(effectivePagePayloadValue.value, 'open')
  } catch (error) {
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
    '当用户提到“改哪里”或“当前文件”时，先读取当前打开文件或当前文件目录下相关文件，再决定修改目标。',
    // 业务页面可按场景补充系统级约束，例如限制思考输出风格或口径。
    systemPrompt,
  ].filter(Boolean)
  return {
    contextFiles: currentFileKey ? [currentFileKey] : [],
    contextNotes,
    // 身份跟随页面场景传到底层，让会话自动筛选 skill 和 tool。
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
        // 结束阶段按页面声明的模式读取结果，JSON 页读 query-json，源码页读工作区快照。
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
  welcomeMessage: '直接输入任务即可开始。',
  buildRequestContext: buildChatContext,
  filterArchivedSession: matchArchivedSessionScope,
  onBeforeSend: async ({ prompt }) => {
    const beforeSendHandler = resolveAssistantFunctionProp('onBeforeSend')
    if (!beforeSendHandler) {
      return prompt
    }
    // AI修改：发送前回调下沉到 useAiChat，确保所有发送入口都统一触发。 - 2026-06-29
    return await beforeSendHandler({
      prompt,
      workingDirectory: workingDirectory.value,
      currentFileKey: effectiveCurrentFileKey.value,
      // 对业务层隐藏内部 reason 细节，before-send 场景统一由组件内部补齐。
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
        // 任务完成后优先读取一次结果文件，业务页可直接通过回调接收结构化结果。
        await notifyTaskEnded('task-completed')
      } catch (error) {
        // 结果文件可能尚未落盘，assistant-finished 阶段会再兜底读取一次。
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
        // 保持主流程完成态，业务页可自行决定是否继续兜底处理。
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
  // 作用域变更后强制清空当前选中会话，避免继续携带旧目录下的 sessionId。
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
  // 不展示思考明细且正文尚未返回时，用轻量状态提示替代大块空白区域。
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
  // AI修改：文件类工具优先展示外部传入的表单名称，避免把具体文件摘要直接暴露到工具行。 - 2026-06-29
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

// 思考内容默认只展示摘要，避免长链路推理直接挤占正文区域。
defineExpose({
  openAssistant,
  closeAssistant,
  toggleAssistant,
  // 对外暴露内部发送态，便于页面复用统一 loading，而不再重复维护一份业务状态。
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
    // AI修改：错误提示默认 5 秒后自动消失，避免旧报错长时间停留在聊天面板里。 - 2026-06-29
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
  <div v-if="effectiveCollapsible && collapsed" class="ai-chat-collapsed" @click="toggleCollapsed">
    <el-icon class="collapsed-icon"><DArrowRight /></el-icon>
    <span class="collapsed-text">AI</span>
  </div>

  <div v-if="renderShell" class="ai-chat-shell" :style="shellStyle">
    <div
      style="display: flex; flex-direction: column; width: 100%; height: 100%; min-height: 0; box-sizing: border-box;"
    >
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
          <el-space direction="vertical" size="small" class="chat-header-title">
            <el-text size="large">{{ displayTitle }}</el-text>
            <el-text v-if="displaySubtitle && !effectiveCompactHeader" type="info">{{ displaySubtitle }}</el-text>
          </el-space>

          <el-space wrap size="small" class="ai-toolbar">
            <slot name="toolbar" />
            <el-button v-if="effectiveCollapsible" size="small" class="toolbar-secondary-button" @click.stop="toggleCollapsed">
              <el-icon><DArrowLeft /></el-icon>
            </el-button>
            <!-- 暂时屏蔽历史会话入口，仅隐藏按钮展示，保留底层会话能力便于后续恢复。 -->
            <el-button size="small" type="primary" class="toolbar-primary-button" @click="handleStartNewSession">新会话</el-button>
          </el-space>
        </div>
      </div>

      <div style="display: flex; flex: 1; flex-direction: column; gap: 8px; width: 100%; min-height: 0; padding-top: 6px;">
        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="true"
          show-icon
          @close="clearErrorMessage"
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

        <div :style="composerContainerStyle">
          <el-input
            v-if="effectiveShowWorkingDirectory"
            v-model="workingDirectory"
            placeholder="请输入工作目录"
          >
            <template #prepend>工作目录</template>
          </el-input>

          <el-input
            v-model="inputValue"
            class="chat-input"
            type="textarea"
            :rows="effectiveComposerRows"
            :placeholder="displayPlaceholder"
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
