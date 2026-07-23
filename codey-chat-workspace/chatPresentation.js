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
        // 解析失败说明还在流式输出中
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
        // ignore
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
          parsedUiView = { streaming: true }
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
