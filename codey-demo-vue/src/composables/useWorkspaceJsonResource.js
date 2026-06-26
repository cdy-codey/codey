import { createWorkspaceApi } from '../api/workspace'

export function isWorkspaceMissingFileError(error) {
  return /未找到指定文件|404/i.test(error?.message || '')
}

/**
 * 将工作区中的 JSON 文件读写收口成统一资源对象，避免业务页重复处理 query/create/update 细节。
 */
export function createWorkspaceJsonResource(options = {}) {
  const workspacePath = typeof options.workspacePath === 'string' ? options.workspacePath.trim() : ''
  if (!workspacePath) {
    throw new Error('workspacePath 不能为空')
  }
  const workspaceApi = options.workspaceApi || createWorkspaceApi(options.baseUrl)

  async function load(options = {}) {
    const fallback = Object.prototype.hasOwnProperty.call(options, 'fallback') ? options.fallback : null
    const allowMissing = options.allowMissing !== false
    const initializeWithFallbackOnMissing = options.initializeWithFallbackOnMissing === true
    try {
      const payload = await workspaceApi.queryJson(workspacePath)
      if (payload == null) {
        return fallback
      }
      return payload
    } catch (error) {
      if (allowMissing && isWorkspaceMissingFileError(error)) {
        // 低代码场景需要在缺少配置文件时自动初始化一个默认 JSON，避免后续再次 query 继续报 404。
        if (initializeWithFallbackOnMissing && fallback && typeof fallback === 'object') {
          await save(fallback)
        }
        return fallback
      }
      throw error
    }
  }

  async function save(payload) {
    const content = JSON.stringify(payload, null, 2)
    try {
      await workspaceApi.updateFileContent(workspacePath, content)
    } catch (error) {
      if (!isWorkspaceMissingFileError(error)) {
        throw error
      }
      await workspaceApi.createFile(workspacePath, content)
    }
    return payload
  }

  return {
    workspacePath,
    load,
    save,
  }
}
