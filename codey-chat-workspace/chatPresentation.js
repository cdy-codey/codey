// 把普通文本和 fenced code block 拆开渲染，让回复更接近文档阅读体验。
export function getMessageBlocks(content, status) {
  const source = typeof content === 'string' ? content : ''
  if (!source.trim()) {
    return []
  }

  const isFinished = status === 'FINISH' || status === 'ERROR' || status === 'STOP' || status === 'CANCELED'

  // 容错处理：如果大模型直接输出了裸的 JSON 字符串（没有使用 ```json 包裹），进行特殊拦截
  const trimmedSource = source.trim()
  if (trimmedSource.startsWith('{') && (trimmedSource.includes('"_view_type"') || trimmedSource.includes('"view"') || trimmedSource.includes("'_view_type'"))) {
    try {
      const parsedView = resolveUiViewPayload(JSON.parse(trimmedSource))
      if (parsedView) {
        return buildUiViewBlocks(parsedView, trimmedSource)
      }
    } catch (e) {
      if (!isFinished) {
        // 流式输出中完整 JSON 解析失败：先尝试宽容解析残缺 JSON，
        // 能渲染出已到达的结构就直接渲染，避免整个消息卡在“正在生成”进度条上。
        const partialView = parsePartialViewJson(trimmedSource)
        if (partialView) {
          return buildUiViewBlocks(partialView, trimmedSource)
        }
        // 结构尚未完整到达（连 _view_type 都没闭合），仍退化为流式占位
        return [{
          type: 'code',
          language: 'json',
          content: trimmedSource,
          parsedUiView: { streaming: true },
          streaming: true,
        }]
      } else {
        // 已结束但解析失败，当作普通代码块展示错误内容，不再 loading
        return [{
          type: 'code',
          language: 'json',
          content: trimmedSource,
        }]
      }
    }
  }

  const blocks = []
  const pattern = /```([\w-]+)?\n?([\s\S]*?)```/g
  let lastIndex = 0
  let match

  while ((match = pattern.exec(source)) !== null) {
    const textSegment = source.slice(lastIndex, match.index)
    pushTextBlock(blocks, textSegment)
    const language = (match[1] || '').trim()
    const content = (match[2] || '').replace(/\n$/, '')
    let parsedUiView = null
    if (language === 'json') {
      try {
        parsedUiView = resolveUiViewPayload(JSON.parse(content))
      } catch (e) {
        // 完整解析失败：流式输出中的残缺 JSON 尝试宽容解析，增量渲染已到达的部分
        if (!isFinished) {
          parsedUiView = parsePartialViewJson(content)
        }
      }
    }

    // user_choice 视图直接创建交互块，不显示为代码块
    if (parsedUiView && parsedUiView._view_type === 'user_choice') {
      blocks.push(...buildUiViewBlocks(parsedUiView, content))
    } else {
      blocks.push({
        type: 'code',
        language,
        content,
        parsedUiView,
      })
    }
    lastIndex = pattern.lastIndex
  }

  const remaining = source.slice(lastIndex)
  if (remaining) {
    const unclosedPattern = /```([\w-]+)?\n?([\s\S]*)$/
    const unclosedMatch = unclosedPattern.exec(remaining)
    if (unclosedMatch) {
      const textSegment = remaining.slice(0, unclosedMatch.index)
      pushTextBlock(blocks, textSegment)
      
      const language = (unclosedMatch[1] || '').trim()
      const unclosedContent = unclosedMatch[2] || ''
      
      let parsedUiView = null
      if (language === 'json' && unclosedContent.includes('"_view_type"')) {
        if (!isFinished) {
          // 流式输出中：优先尝试宽容解析残缺 JSON 增量渲染；
          // 连关键结构都没闭合时才退化为流式占位（进度条）
          parsedUiView = parsePartialViewJson(unclosedContent) || { streaming: true }
        } else {
          // 如果已结束，尝试容错解析
          try {
            parsedUiView = resolveUiViewPayload(JSON.parse(unclosedContent))
          } catch (e) {
            // 保持 null，展示原码
          }
        }
      }

      blocks.push({
        type: 'code',
        language,
        content: unclosedContent,
        parsedUiView,
        streaming: !isFinished,
      })
    } else {
      pushTextBlock(blocks, remaining)
    }
  }
  return blocks
}

function buildUiViewBlocks(view, rawContent) {
  if (!view || typeof view !== 'object') {
    return []
  }
  if (view._view_type === 'text') {
    return [{
      type: 'text',
      paragraphs: [typeof view.content === 'string' ? view.content : ''].filter(Boolean),
    }]
  }
  // user_choice 渲染为用户选择交互块
  if (view._view_type === 'user_choice') {
    return [{
      type: 'user_choice',
      title: typeof view.title === 'string' ? view.title : '请选择',
      description: typeof view.description === 'string' ? view.description : '',
      options: Array.isArray(view.options) ? view.options.filter(
        (opt) => opt && typeof opt.key === 'string' && typeof opt.label === 'string',
      ) : [],
    }]
  }
  return [{
    type: 'code',
    language: 'json',
    content: rawContent,
    parsedUiView: view,
  }]
}

function resolveUiViewPayload(parsed) {
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
}

// 流式输出中的 JSON 是逐步到达的，直接 JSON.parse 会因为“未闭合”而失败。
// 这里做宽容解析：先把未闭合的引号/括号补全成合法 JSON；整体仍失败（如数字被截断在中间）时
// 从尾部逐段丢弃字符重试，确保只要 _view_type / modules 等关键结构已经闭合就能渲染出卡片。
function parsePartialViewJson(content) {
  const trimmed = typeof content === 'string' ? content.trim() : ''
  if (!trimmed.startsWith('{')) {
    return null
  }
  const parsed = parseJsonStrict(closeJsonStructure(trimmed))
  if (parsed) {
    const view = resolveUiViewPayload(parsed)
    if (view && hasRenderableContent(view)) {
      return view
    }
  }
  // 补全后仍无法解析：尾部可能是“12.”这类被截断的 token，从后往前丢弃 1-8 个字符重试
  for (let drop = 1; drop <= 8; drop += 1) {
    const candidate = trimmed.slice(0, trimmed.length - drop)
    if (!candidate.trim()) {
      break
    }
    const retry = parseJsonStrict(closeJsonStructure(candidate))
    if (retry) {
      const view = resolveUiViewPayload(retry)
      if (view && hasRenderableContent(view)) {
        return view
      }
    }
  }
  return null
}

// 残缺视图是否有可渲染的实质内容：
// 仅 _view_type / summary 到达而模块还没输出时视为“还没准备好”，
// 退化为流式占位，避免渲染出空卡片；模块元素陆续闭合后才开始渲染。
function hasRenderableContent(view) {
  if (!view || typeof view !== 'object') {
    return false
  }
  if (view._view_type === 'text') {
    return !!normalizeString(view.content)
  }
  if (view._view_type === 'user_choice') {
    return Array.isArray(view.options) && view.options.length > 0
  }
  if (view._view_type === 'diff_data') {
    return Array.isArray(view.changes) && view.changes.length > 0
  }
  return Array.isArray(view.modules) && view.modules.length > 0
}

function normalizeString(value) {
  return typeof value === 'string' ? value.trim() : ''
}

// 严格解析：仅当整段文本是合法 JSON 时返回解析结果，否则返回 null。
function parseJsonStrict(value) {
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }
  try {
    return JSON.parse(value)
  } catch (error) {
    return null
  }
}

// 扫描 JSON 文本，把未闭合的字符串引号和 { / [ 括号补全闭合，返回可尝试 JSON.parse 的文本。
function closeJsonStructure(value) {
  let stack = []
  let inString = false
  let escaping = false
  for (let index = 0; index < value.length; index += 1) {
    const current = value[index]
    if (escaping) {
      escaping = false
      continue
    }
    if (inString) {
      if (current === '\\') {
        escaping = true
      } else if (current === '"') {
        inString = false
      }
      continue
    }
    if (current === '"') {
      inString = true
      continue
    }
    if (current === '{' || current === '[') {
      stack.push(current)
      continue
    }
    if (current === '}' || current === ']') {
      if (stack.length) {
        stack.pop()
      }
    }
  }
  let result = value
  if (inString) {
    // 字符串在中间被截断（如 summary 还在输出）：先补一个闭合引号，展示已到达的部分
    result += '"'
  }
  for (let index = stack.length - 1; index >= 0; index -= 1) {
    result += stack[index] === '{' ? '}' : ']'
  }
  return result
}

function pushTextBlock(blocks, content) {
  const normalized = typeof content === 'string' ? content.trim() : ''
  if (!normalized) {
    return
  }
  blocks.push(...parseMarkdownLikeBlocks(normalized))
}

function parseMarkdownLikeBlocks(content) {
  const lines = normalizeLineBreaks(content).split('\n')
  const blocks = []
  let paragraphLines = []

  function flushParagraph() {
    if (!paragraphLines.length) {
      return
    }
    const paragraph = paragraphLines.join('\n').trim()
    paragraphLines = []
    if (!paragraph) {
      return
    }
    blocks.push({
      type: 'text',
      paragraphs: [paragraph],
    })
  }

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    const trimmedLine = line.trim()

    if (!trimmedLine) {
      flushParagraph()
      continue
    }

    const headingMatch = /^(#{1,6})\s*(\S.*)$/.exec(trimmedLine)
    if (headingMatch) {
      flushParagraph()
      blocks.push({
        type: 'heading',
        level: Math.min(headingMatch[1].length, 6),
        text: headingMatch[2].trim(),
      })
      continue
    }

    if (isMarkdownDivider(trimmedLine)) {
      flushParagraph()
      blocks.push({
        type: 'divider',
      })
      continue
    }

    if (looksLikeMarkdownTable(lines, index)) {
      flushParagraph()
      const tableBlock = consumeMarkdownTable(lines, index)
      if (tableBlock) {
        blocks.push(tableBlock.block)
        index = tableBlock.nextIndex
        continue
      }
    }

    paragraphLines.push(line.replace(/\s+$/, ''))
  }

  flushParagraph()
  return blocks
}

function normalizeLineBreaks(content) {
  return typeof content === 'string' ? content.replace(/\r\n?/g, '\n') : ''
}

function isMarkdownDivider(line) {
  return /^([-*_])(?:\s*\1){2,}\s*$/.test(line)
}

function looksLikeMarkdownTable(lines, startIndex) {
  if (startIndex + 1 >= lines.length) {
    return false
  }
  const headerCells = splitMarkdownTableRow(lines[startIndex])
  const separatorCells = splitMarkdownTableRow(lines[startIndex + 1])
  if (headerCells.length < 2 || headerCells.length !== separatorCells.length) {
    return false
  }
  return separatorCells.every((cell) => /^:?-{3,}:?$/.test(cell))
}

function consumeMarkdownTable(lines, startIndex) {
  const headers = splitMarkdownTableRow(lines[startIndex])
  const separatorCells = splitMarkdownTableRow(lines[startIndex + 1])
  if (!headers.length || headers.length !== separatorCells.length) {
    return null
  }

  const rows = []
  let index = startIndex + 2
  while (index < lines.length) {
    const currentLine = lines[index]
    const trimmedLine = currentLine.trim()
    if (!trimmedLine) {
      break
    }
    const cells = splitMarkdownTableRow(currentLine)
    if (cells.length < 2) {
      break
    }
    rows.push(padTableRow(cells, headers.length))
    index += 1
  }

  return {
    block: {
      type: 'table',
      headers,
      rows,
    },
    nextIndex: index - 1,
  }
}

function splitMarkdownTableRow(line) {
  if (typeof line !== 'string' || !line.includes('|')) {
    return []
  }
  let normalized = line.trim()
  if (normalized.startsWith('|')) {
    normalized = normalized.slice(1)
  }
  if (normalized.endsWith('|')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized.split('|').map((cell) => cell.trim())
}

function padTableRow(cells, targetLength) {
  const row = Array.isArray(cells) ? cells.slice(0, targetLength) : []
  while (row.length < targetLength) {
    row.push('')
  }
  return row
}

export function formatTime(value) {
  if (!value) {
    return '--'
  }
  let normalizedValue = value
  if (typeof normalizedValue === 'number' && normalizedValue < 1000000000000) {
    normalizedValue *= 1000
  }
  const date = new Date(normalizedValue)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}
