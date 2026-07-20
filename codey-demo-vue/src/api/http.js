const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
}

export async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...DEFAULT_HEADERS,
      ...(options.headers || {}),
    },
  })

  const body = await readResponseBody(response)
  if (isBusinessFailure(body)) {
    throw buildBusinessError(body)
  }
  if (!response.ok) {
    throw new Error(readErrorMessage(body, response))
  }

  return unwrapResponseBody(body)
}

export function get(url, options = {}) {
  return fetchJson(url, {
    ...options,
    method: 'GET',
  })
}

export function post(url, body, options = {}) {
  return fetchJson(url, {
    ...options,
    method: 'POST',
    body: body == null || typeof body === 'string' ? body : JSON.stringify(body),
  })
}

async function readResponseBody(response) {
  if (response.status === 204) {
    return null
  }
  try {
    return await response.json()
  } catch (error) {
    return null
  }
}

function unwrapResponseBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'success')) {
    return body.data ?? null
  }
  return body
}

function isBusinessFailure(body) {
  return !!(body && typeof body === 'object' && body.success === false)
}

function buildBusinessError(body) {
  const error = new Error(body?.message || '请求失败')
  error.code = body?.code || 'REQUEST_FAILED'
  error.response = body || null
  return error
}

/**
 * 文件上传专用方法，使用 FormData 以 multipart/form-data 方式提交。
 */
export async function uploadFile(url, file) {
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(url, {
    method: 'POST',
    body: formData,
  })

  const body = await readResponseBody(response)
  if (isBusinessFailure(body)) {
    throw buildBusinessError(body)
  }
  if (!response.ok) {
    throw new Error(readErrorMessage(body, response))
  }
  return unwrapResponseBody(body)
}

function readErrorMessage(body, response) {
  if (body?.message) {
    return body.message
  }
  if (body?.error) {
    return body.error
  }
  return response.statusText || `请求失败：${response.status}`
}
