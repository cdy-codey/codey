export function normalizeContent(value) {
  return typeof value === 'string' ? value : ''
}

function resolveToolLabel(primaryValue, fallbackValue = '') {
  const primary = normalizeContent(primaryValue).trim()
  if (primary) {
    return primary
  }
  return normalizeContent(fallbackValue).trim()
}

export function parseModelOutputPayload(payload) {
  const rawOutput = payload?.payload?.rawOutput || payload?.message || ''
  if (!rawOutput) {
    return null
  }
  try {
    const root = typeof rawOutput === 'string' ? JSON.parse(rawOutput) : rawOutput
    const choice = Array.isArray(root?.choices) ? root.choices[0] : null
    const message = choice?.message || {}
    // 优先取后端注入的中文 displayName，与 tool_execution_started / tool_call 等事件的展示名保持一致，
    // 避免同一工具因中英文名不同而在工具调用列表中重复展示；无 displayName 时回退真实 function.name。
    // 同时保留英文真实名 rawName 作为稳定去重标识，保证“同一工具”无论哪个事件先到都不会产生中英两条记录。
    const toolCalls = Array.isArray(message?.tool_calls)
      ? message.tool_calls
          .map((item) => {
            const rawName = normalizeContent(item?.function?.name).trim()
            const name =
              normalizeContent(item?.function?.displayName).trim() ||
              rawName ||
              normalizeContent(item?.name).trim()
            return name ? { name, rawName } : null
          })
          .filter(Boolean)
      : []
    return {
      finishReason: choice?.finish_reason || '',
      content: normalizeContent(message?.content),
      reasoning: normalizeContent(message?.reasoning_content),
      toolCalls,
    }
  } catch (error) {
    return null
  }
}

export function extractFinalSummaryText(payload) {
  const finalResult = extractFinalResultPayload(payload)
  const finalContent = extractAssistantContentFromFinalResult(finalResult)
  if (finalContent) {
    return finalContent
  }
  const summaryCandidate = pickFinalSummaryCandidate(payload)
  if (!summaryCandidate) {
    return ''
  }
  return extractSummaryFromCandidate(summaryCandidate)
}

export function normalizeFinalAssistantContent(currentContent, summary) {
  const normalizedSummary = normalizeContent(summary).trim()
  const normalizedContent = normalizeContent(currentContent).trim()
  if (!normalizedContent) {
    return normalizedSummary
  }
  if (looksLikeUiViewPayload(normalizedContent)) {
    return normalizedContent
  }
  if (!normalizedSummary) {
    return normalizedContent
  }
  const extractedSummary = extractSummaryFromCandidate(normalizedContent)
  // 去重替换仅当正文本身就是结构化占位 JSON（去围栏后是 {"status":...} / {"summary":...}）
  // 时生效；正文里还夹着真实回复文本时保留原文，避免摘要冲掉完整的 AI 回复。
  if (extractedSummary && extractedSummary === normalizedSummary && looksLikeStructuredFinalPayload(normalizedContent)) {
    return normalizedSummary
  }
  if (normalizedSummary && looksLikeStructuredFinalPayload(normalizedContent)) {
    return normalizedSummary
  }
  return normalizedContent
}

export function extractFinalResultPayload(payload) {
  const finalResult = payload?.payload?.finalResult
  if (finalResult && typeof finalResult === 'object' && !Array.isArray(finalResult)) {
    return finalResult
  }
  return null
}

export function extractAssistantContentFromFinalResult(finalResult) {
  if (!finalResult || typeof finalResult !== 'object') {
    return ''
  }
  const view = finalResult.view
  const viewContent = extractAssistantContentFromView(view)
  if (viewContent) {
    return viewContent
  }
  if (typeof finalResult.summary === 'string' && finalResult.summary.trim()) {
    return finalResult.summary.trim()
  }
  return ''
}

export function extractAssistantContentFromView(view) {
  if (typeof view === 'string' && view.trim()) {
    return view.trim()
  }
  if (!view || typeof view !== 'object' || Array.isArray(view)) {
    return ''
  }
  if (view._view_type === 'text') {
    return normalizeContent(view.content).trim()
  }
  try {
    return JSON.stringify(view)
  } catch (error) {
    return ''
  }
}

// 判断一段文本能否解析为结构化 UI 视图（兼容 status/view 包装与裸视图两种形态）。
function parseViewPayload(value) {
  const content = normalizeContent(value).trim()
  if (!content) {
    return null
  }
  try {
    const parsed = JSON.parse(content)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null
    }
    if (parsed._view_type) {
      return parsed
    }
    if (parsed.view && typeof parsed.view === 'object' && !Array.isArray(parsed.view) && parsed.view._view_type) {
      return parsed.view
    }
    return null
  } catch (error) {
    return null
  }
}

// final_summary 收口时的内容合并策略：
// 纯文本摘要走 normalizeFinalAssistantContent 的兜底逻辑（不覆盖正文）；
// 结构化 UI 视图（form_data / diff_data / user_choice）则“正文保留 + 视图以 fenced json 追加”，
// 让 getMessageBlocks 同时渲染文本段落和格式化卡片，绝不用视图覆盖已生成的 AI 回复。
export function mergeFinalViewContent(currentContent, finalPayload) {
  const normalizedSummary = normalizeContent(finalPayload).trim()
  if (!looksLikeUiViewPayload(normalizedSummary)) {
    return normalizeFinalAssistantContent(currentContent, normalizedSummary)
  }
  const current = normalizeContent(currentContent).trim()
  if (!current) {
    // 气泡里没有正文：直接展示视图 JSON，由 getMessageBlocks 解析成格式化卡片。
    return normalizedSummary
  }
  // 以 { 开头：完整视图 JSON 直接保留；流式中断产生的残缺 JSON 用干净视图修正，
  // 避免 getMessageBlocks 一直把它当成“流式输出中”而卡在进度条上。
  if (current.startsWith('{')) {
    return parseViewPayload(current) ? current : normalizedSummary
  }
  // 正文中已含可渲染的视图 JSON（兼容闭合围栏与“前言 + 未闭合 ```json{...}”混排）时，
  // 直接保留正文，不再追加第二份视图 JSON，避免同一视图被重复渲染成表单卡片和 JSON 字符串。
  if (extractEmbeddedViewPayload(current)) {
    return current
  }
  // 正文是普通文本（如“提取结果概览”）：文本原样保留，末尾追加 fenced 视图 JSON。
  return `${current}\n\n\`\`\`json\n${normalizedSummary}\n\`\`\``
}

// 从正文中提取已内嵌的视图 JSON（兼容闭合围栏与“前言 + 未闭合 ```json{...}”两种形态），
// 存在则说明正文已经包含可渲染的视图，mergeFinalViewContent 无需再追加第二份。
function extractEmbeddedViewPayload(value) {
  const content = normalizeContent(value)
  if (!content.includes('"_view_type"')) {
    return null
  }
  // 标准输出形态：闭合围栏内的完整 JSON。
  const closedFence = /```json\s*([\s\S]*?)```/.exec(content)
  if (closedFence) {
    const closedView = parseViewPayload(closedFence[1])
    if (closedView) {
      return closedView
    }
  }
  // 兼容“前言 + 未闭合 ```json{...}”混排：提取正文中最后一个平衡 JSON 对象再判断。
  const lastJson = extractLastJsonObject(content)
  if (!lastJson) {
    return null
  }
  return parseViewPayload(lastJson)
}

export function buildToolResultPreview(payload) {
  const request = payload?.payload?.request || {}
  const rawResult = payload?.payload?.result || {}
  const structuredResult = parseStructuredToolContent(rawResult?.content)
  const result = structuredResult ? { ...structuredResult, ...rawResult } : rawResult
  const rawToolName =
    request?.toolName ||
    request?.tool_name ||
    payload?.payload?.name ||
    payload?.message ||
    '工具调用'
  return summarizeToolResult(rawToolName, request, result, '')
}

function normalizePath(value) {
  return normalizeContent(value).trim().replace(/\\/g, '/')
}

// 摘要里只保留文件名或最后一层目录，避免把长绝对路径直接暴露给用户。
function shortenPathForSummary(value) {
  const normalized = normalizePath(value)
  if (!normalized) {
    return ''
  }
  if (normalized === '.') {
    return '当前目录'
  }
  const segments = normalized.split('/').filter(Boolean)
  if (!segments.length) {
    return normalized
  }
  const lastSegment = segments[segments.length - 1]
  if (lastSegment.includes('.')) {
    return lastSegment
  }
  if (segments.length >= 2 && !/^[A-Za-z]:$/.test(segments[0])) {
    return segments.slice(-2).join('/')
  }
  return lastSegment
}

function appendUniquePart(parts, value) {
  const text = normalizeContent(value).trim()
  if (!text || parts.includes(text)) {
    return
  }
  parts.push(text)
}

function pickPath(...sources) {
  for (const source of sources) {
    const directPath = normalizePath(source?.path || source?.filePath || source?.file_path || source?.targetPath || source?.target_path)
    if (directPath) {
      return directPath
    }
    if (Array.isArray(source?.paths) && source.paths.length) {
      const list = source.paths.map((item) => normalizePath(item)).filter(Boolean)
      if (list.length) {
        return list.join('、')
      }
    }
    if (Array.isArray(source?.filePaths) && source.filePaths.length) {
      const list = source.filePaths.map((item) => normalizePath(item)).filter(Boolean)
      if (list.length) {
        return list.join('、')
      }
    }
    if (Array.isArray(source?.file_paths) && source.file_paths.length) {
      const list = source.file_paths.map((item) => normalizePath(item)).filter(Boolean)
      if (list.length) {
        return list.join('、')
      }
    }
  }
  return ''
}

function buildLineHint(...sources) {
  for (const source of sources) {
    if (!source || typeof source !== 'object') {
      continue
    }
    const firstChangedLine = Number(source.firstChangedLine)
    if (Number.isFinite(firstChangedLine) && firstChangedLine > 0) {
      return `${firstChangedLine} 行`
    }
    const startLine = Number(source.startLine)
    const endLine = Number(source.endLine)
    if (Number.isFinite(startLine) && startLine > 0 && Number.isFinite(endLine) && endLine >= startLine) {
      return startLine === endLine ? `${startLine} 行` : `${startLine}-${endLine} 行`
    }
    if (Number.isFinite(startLine) && startLine > 0) {
      return `${startLine} 行`
    }
  }
  return ''
}

function buildSearchHint(request = {}, result = {}) {
  const parts = []
  appendUniquePart(parts, normalizeContent(request.keyword))
  appendUniquePart(parts, normalizeContent(request.query))
  appendUniquePart(parts, normalizeContent(request.pattern))
  appendUniquePart(parts, normalizeContent(request.searchText))
  appendUniquePart(parts, normalizeContent(request.search_text))
  appendUniquePart(parts, normalizeContent(request.information_request))
  appendUniquePart(parts, normalizeContent(request.pathHint))
  appendUniquePart(parts, normalizeContent(request.path_hint))
  appendUniquePart(parts, normalizeContent(result.keyword))
  appendUniquePart(parts, normalizeContent(result.query))
  appendUniquePart(parts, normalizeContent(result.pattern))
  appendUniquePart(parts, normalizeContent(result.searchText))
  appendUniquePart(parts, normalizeContent(result.search_text))
  appendUniquePart(parts, normalizeContent(result.information_request))
  appendUniquePart(parts, normalizeContent(result.pathHint))
  appendUniquePart(parts, normalizeContent(result.path_hint))
  return parts.join(' / ')
}

function buildWorkspaceHint(request = {}, result = {}) {
  const parts = []
  appendUniquePart(parts, normalizePath(request.workingDirectory))
  appendUniquePart(parts, normalizePath(request.workspace))
  appendUniquePart(parts, normalizePath(request.workspacePath))
  appendUniquePart(parts, normalizePath(request.workspace_path))
  appendUniquePart(parts, normalizePath(request.currentWorkingDirectory))
  appendUniquePart(parts, normalizePath(request.current_working_directory))
  appendUniquePart(parts, normalizePath(result.workingDirectory))
  appendUniquePart(parts, normalizePath(result.workspace))
  appendUniquePart(parts, normalizePath(result.workspacePath))
  appendUniquePart(parts, normalizePath(result.workspace_path))
  appendUniquePart(parts, normalizePath(result.currentWorkingDirectory))
  appendUniquePart(parts, normalizePath(result.current_working_directory))
  return parts.join('、')
}

function sanitizeSummaryText(value) {
  const summary = normalizeContent(value).trim()
  if (!summary) {
    return ''
  }
  return summary
    .replace(/[A-Za-z]:[\\/][^\s，。；：)）]+/g, (match) => shortenPathForSummary(match))
    .replace(/file replacement completed:/gi, '已完成更新：')
    .replace(/file write completed:/gi, '已保存：')
    .replace(/delete completed, total items:/gi, '已完成删除，共处理 ')
    .replace(/patch applied to /gi, '已应用补丁，处理 ')
    .replace(/ files?/gi, ' 个文件')
    .replace(/\s+/g, ' ')
    .trim()
}

function buildValidationSummary(path, result = {}, summary = '') {
  const target = path ? ` ${path}` : ''
  const valid = typeof result?.valid === 'boolean' ? result.valid : null
  const errorCount = Number(result?.errorCount)
  const warningCount = Number(result?.warningCount)
  const normalizedSummary = normalizeContent(summary)

  if (valid === false || normalizedSummary.includes('校验失败')) {
    if (Number.isFinite(errorCount) && errorCount > 0) {
      return `已检查${target}，发现 ${errorCount} 处需要处理`
    }
    return `已检查${target}，发现需要处理的问题`
  }
  if ((valid === true && Number.isFinite(warningCount) && warningCount > 0) || normalizedSummary.includes('风险提醒')) {
    return `已检查${target}，有 ${warningCount || '一些'} 条提醒`
  }
  if (valid === true || normalizedSummary.includes('校验通过')) {
    return `已检查${target}，未发现问题`
  }
  return ''
}

function buildPathSummary(path, lineHint, result = {}) {
  if (!path) {
    return ''
  }
  if (result?.created === true) {
    return `已新建 ${path}`
  }
  if (result?.created === false) {
    return lineHint ? `已保存 ${path}（${lineHint}）` : `已保存 ${path}`
  }
  return lineHint ? `已处理 ${path}（${lineHint}）` : `已处理 ${path}`
}

function buildCommandSummary(command) {
  const normalizedCommand = normalizeContent(command).trim()
  if (!normalizedCommand) {
    return ''
  }
  const compactCommand = normalizedCommand.replace(/\s+/g, ' ')
  if (compactCommand.length <= 32) {
    return `已执行命令：${compactCommand}`
  }
  return `已执行命令：${compactCommand.slice(0, 32)}...`
}

function buildSearchSummary(keyword, path) {
  if (keyword) {
    return path ? `已查找“${keyword}”相关内容（${path}）` : `已查找“${keyword}”相关内容`
  }
  if (path) {
    return `已查看 ${path} 中的相关内容`
  }
  return ''
}

function buildWorkspaceSummary(path, workspaceHint) {
  if (path) {
    return `已查看 ${path} 的内容`
  }
  if (workspaceHint) {
    return '已查看目录内容'
  }
  return ''
}

function looksLikeSearchResult(keyword, result = {}) {
  if (keyword) {
    return true
  }
  const matchCount = Number(result?.matchCount ?? result?.matches ?? result?.totalMatches)
  return Number.isFinite(matchCount) && matchCount >= 0
}

function looksLikeWorkspaceResult(result = {}) {
  return Array.isArray(result?.entries)
    || Array.isArray(result?.children)
    || Array.isArray(result?.items)
    || Array.isArray(result?.directories)
    || Array.isArray(result?.files)
}

function parseStructuredToolContent(rawContent) {
  const content = normalizeContent(rawContent).trim()
  if (!content) {
    return null
  }
  const jsonStartIndex = content.indexOf('{')
  if (jsonStartIndex < 0) {
    return null
  }
  const candidate = content.slice(jsonStartIndex)
  try {
    return JSON.parse(candidate)
  } catch (error) {
    return null
  }
}

function pickFinalSummaryCandidate(payload) {
  if (payload?.payload?.view) {
    return extractAssistantContentFromView(payload.payload.view)
  }
  if (typeof payload?.message === 'string' && payload.message.trim()) {
    return payload.message
  }
  if (typeof payload?.payload?.rawOutput === 'string' && payload.payload.rawOutput.trim()) {
    return payload.payload.rawOutput
  }
  return ''
}

function pickFriendlyPath(...sources) {
  const path = pickPath(...sources)
  if (!path) {
    return ''
  }
  return path
    .split('、')
    .map((item) => shortenPathForSummary(item))
    .filter(Boolean)
    .join('、')
}

function extractSummaryFromCandidate(rawValue) {
  const content = normalizeContent(rawValue).trim()
  if (!content) {
    return ''
  }
  const directSummary = pickSummaryField(parseJsonSafely(content))
  if (directSummary) {
    return directSummary
  }
  const looseDirectSummary = extractSummaryFromLooseJson(content)
  if (looseDirectSummary) {
    return looseDirectSummary
  }
  const unfencedContent = unwrapMarkdownFence(content)
  const unfencedSummary = pickSummaryField(parseJsonSafely(unfencedContent))
  if (unfencedSummary) {
    return unfencedSummary
  }
  const looseUnfencedSummary = extractSummaryFromLooseJson(unfencedContent)
  if (looseUnfencedSummary) {
    return looseUnfencedSummary
  }
  const embeddedJson = extractLastJsonObject(unfencedContent)
  const embeddedSummary = pickSummaryField(parseJsonSafely(embeddedJson))
  if (embeddedSummary) {
    return embeddedSummary
  }
  const looseEmbeddedSummary = extractSummaryFromLooseJson(embeddedJson)
  if (looseEmbeddedSummary) {
    return looseEmbeddedSummary
  }
  return unfencedContent
}

function pickSummaryField(parsedValue) {
  if (!parsedValue || typeof parsedValue !== 'object' || Array.isArray(parsedValue)) {
    return ''
  }
  if (parsedValue.view) {
    return extractAssistantContentFromView(parsedValue.view)
  }
  const summary = normalizeContent(parsedValue.summary).trim()
  return summary
}

function parseJsonSafely(value) {
  const content = normalizeContent(value).trim()
  if (!content) {
    return null
  }
  try {
    return JSON.parse(content)
  } catch (error) {
    return null
  }
}

function looksLikeStructuredFinalPayload(value) {
  const content = unwrapMarkdownFence(value)
  return content.startsWith('{') && /"summary"\s*:|"status"\s*:/.test(content)
}

function looksLikeUiViewPayload(value) {
  const content = unwrapMarkdownFence(value)
  if (!content.startsWith('{')) {
    return false
  }
  try {
    const parsed = JSON.parse(content)
    return !!(parsed && parsed._view_type)
  } catch (error) {
    return /"_view_type"\s*:/.test(content)
  }
}

function extractSummaryFromLooseJson(value) {
  const content = normalizeContent(value).trim()
  if (!content) {
    return ''
  }
  const summaryKeyMatch = /"summary"\s*:\s*"/.exec(content)
  if (!summaryKeyMatch) {
    return ''
  }
  const valueStart = summaryKeyMatch.index + summaryKeyMatch[0].length
  let summary = ''
  let escaping = false

  // AI修改：结束语里常出现未转义的双引号，这里按“遇到字段结束符才收尾”的方式宽松提取 summary - 2026-06-29
  for (let index = valueStart; index < content.length; index += 1) {
    const current = content[index]
    if (escaping) {
      summary += current
      escaping = false
      continue
    }
    if (current === '\\') {
      summary += current
      escaping = true
      continue
    }
    if (current === '"') {
      const nextSignificantChar = findNextSignificantChar(content, index + 1)
      if (!nextSignificantChar || nextSignificantChar === '}' || nextSignificantChar === ',') {
        return normalizeLooseJsonText(summary)
      }
    }
    summary += current
  }
  return normalizeLooseJsonText(summary)
}

function findNextSignificantChar(value, startIndex) {
  for (let index = startIndex; index < value.length; index += 1) {
    const current = value[index]
    if (!/\s/.test(current)) {
      return current
    }
  }
  return ''
}

function normalizeLooseJsonText(value) {
  return normalizeContent(value)
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\')
    .trim()
}

function unwrapMarkdownFence(value) {
  let content = normalizeContent(value).trim()
  if (content.startsWith('```json')) {
    content = content.slice('```json'.length).trim()
  } else if (content.startsWith('```')) {
    content = content.slice(3).trim()
  }
  if (content.endsWith('```')) {
    content = content.slice(0, -3).trim()
  }
  return content
}

function extractLastJsonObject(value) {
  const content = normalizeContent(value)
  let inString = false
  let escaping = false
  let depth = 0
  let start = -1
  let lastMatch = ''

  for (let index = 0; index < content.length; index += 1) {
    const current = content[index]
    if (escaping) {
      escaping = false
      continue
    }
    if (inString && current === '\\') {
      escaping = true
      continue
    }
    if (current === '"') {
      inString = !inString
      continue
    }
    if (inString) {
      continue
    }
    if (current === '{') {
      if (depth === 0) {
        start = index
      }
      depth += 1
      continue
    }
    if (current === '}' && depth > 0) {
      depth -= 1
      if (depth === 0 && start >= 0) {
        lastMatch = content.slice(start, index + 1)
        start = -1
      }
    }
  }
  return lastMatch.trim()
}

// 工具结果优先展示目标文件、行号、命令等关键信息，避免把整段原始 JSON 直接暴露给用户。
function summarizeToolResult(rawToolName, request = {}, result = {}, fallbackContent = '') {
  const toolName = resolveToolLabel(
    request?.displayName || result?.displayName,
    rawToolName || request?.toolName || request?.tool_name || '工具调用',
  )
  const path = pickFriendlyPath(request, result)
  const lineHint = buildLineHint(request, result)
  const command = normalizeContent(request?.command || result?.command).trim()
  const keyword = buildSearchHint(request, result)
  const workspaceHint = buildWorkspaceHint(request, result)
  const errorMessage = normalizeContent(result?.errorMessage || result?.message).trim()
  const summary = sanitizeSummaryText(result?.summary)

  if (errorMessage) {
    return `${toolName}：${sanitizeSummaryText(errorMessage) || errorMessage}`
  }

  const validationSummary = buildValidationSummary(path, result, summary || fallbackContent)
  if (validationSummary) {
    return validationSummary
  }

  const pathSummary = buildPathSummary(path, lineHint, result)
  if (pathSummary) {
    return pathSummary
  }

  if (looksLikeSearchResult(keyword, result)) {
    const searchSummary = buildSearchSummary(keyword, path)
    if (searchSummary) {
      return searchSummary
    }
  }

  if (looksLikeWorkspaceResult(result)) {
    const workspaceSummary = buildWorkspaceSummary(path, workspaceHint)
    if (workspaceSummary) {
      return workspaceSummary
    }
  }

  const commandSummary = buildCommandSummary(command)
  if (commandSummary) {
    return commandSummary
  }

  if (summary) {
    return summary
  }
  if (keyword) {
    return `已处理与“${keyword}”相关的内容`
  }
  if (workspaceHint) {
    return '已完成目录检查'
  }
  if (result && typeof result === 'object') {
    const keys = Object.keys(result).filter((key) => !['displayName', 'summary'].includes(key))
    if (keys.length > 0) {
      return `${toolName}已完成`
    }
  }
  if (fallbackContent) {
    return `${toolName}已执行`
  }
  return `${toolName}已执行`
}

export function normalizeToolMessageContent(toolName, content) {
  const parsedContent = parseStructuredToolContent(content)
  if (parsedContent) {
    return summarizeToolResult(toolName, {}, parsedContent, content)
  }
  return summarizeToolResult(toolName, {}, {}, content)
}
