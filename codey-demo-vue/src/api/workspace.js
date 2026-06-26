import { fetchJson } from './http'

// 工作目录接口统一使用 POST，便于后续继续扩展批量操作。
export function createWorkspaceApi(baseUrl = '/api/workspace') {
  return {
    query(path = '') {
      return fetchJson(`${baseUrl}/query`, {
        method: 'POST',
        body: JSON.stringify({
          path,
        }),
      })
    },

    // 直接读取 JSON 文件内容，避免前端再从快照里手动提取 currentFile.content。
    queryJson(path = '') {
      return fetchJson(`${baseUrl}/query-json`, {
        method: 'POST',
        body: JSON.stringify({
          path,
        }),
      })
    },

    push(payload = {}) {
      return fetchJson(`${baseUrl}/push`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    createFile(path, content = '') {
      return this.push({
        path,
        directory: false,
        content,
      })
    },

    createDirectory(path) {
      return this.push({
        path,
        directory: true,
      })
    },

    updateFileContent(path, content) {
      return this.push({
        path,
        content,
      })
    },

    rename(path, newName) {
      return this.push({
        path,
        newName,
      })
    },

    remove(path) {
      return fetchJson(`${baseUrl}/delete`, {
        method: 'POST',
        body: JSON.stringify({
          path,
        }),
      })
    },
  }
}
