import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { clearToken, getToken } from '@/utils/token'

export const AUTH_UNAUTHORIZED_EVENT = 'auth:unauthorized'

type GetRequestBehavior = {
  dedupe?: boolean
  ttl?: number
}

type CachedAxiosRequestConfig<D = any> = AxiosRequestConfig<D> & {
  requestCache?: GetRequestBehavior
}

type CacheEntry = {
  expiresAt: number
  data: unknown
}

const DEFAULT_GET_BEHAVIORS: Array<{ pattern: RegExp; options: GetRequestBehavior }> = [
  { pattern: /^\/patients$/, options: { dedupe: true } },
  { pattern: /^\/queues\/events$/, options: { dedupe: true, ttl: 1000 } },
  { pattern: /^\/queues\/tickets\/[^/]+$/, options: { dedupe: true, ttl: 1500 } },
  { pattern: /^\/queues\/tickets\/[^/]+\/rank$/, options: { dedupe: true, ttl: 1500 } }
]

const inflightGetRequests = new Map<string, Promise<unknown>>()
const responseCache = new Map<string, CacheEntry>()

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

function normalizeValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(normalizeValue)
  }

  if (value && typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((result, key) => {
        const normalized = normalizeValue((value as Record<string, unknown>)[key])
        if (normalized !== undefined) {
          result[key] = normalized
        }
        return result
      }, {})
  }

  return value
}

function buildRequestKey(url: string, config?: CachedAxiosRequestConfig) {
  return JSON.stringify({
    baseURL: config?.baseURL || http.defaults.baseURL || '',
    url,
    params: normalizeValue(config?.params),
    data: normalizeValue(config?.data)
  })
}

function resolveGetRequestBehavior(url: string, config?: CachedAxiosRequestConfig): GetRequestBehavior {
  if (config?.requestCache) {
    return config.requestCache
  }

  return DEFAULT_GET_BEHAVIORS.find((entry) => entry.pattern.test(url))?.options || {}
}

function getCachedResponse(key: string) {
  const entry = responseCache.get(key)
  if (!entry) {
    return undefined
  }

  if (entry.expiresAt <= Date.now()) {
    responseCache.delete(key)
    return undefined
  }

  return entry.data
}

function setCachedResponse(key: string, data: unknown, ttl: number) {
  if (ttl <= 0) {
    return
  }

  responseCache.set(key, {
    data,
    expiresAt: Date.now() + ttl
  })
}

function clearCachedResponses() {
  responseCache.clear()
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result?.success) {
      return result.data
    }
    return Promise.reject(new Error(result?.message || '请求失败'))
  },
  (error: AxiosError<{ message?: string }>) => {
    if (error.response?.status === 401) {
      clearToken()
      window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
    }

    if (!error.response) {
      return Promise.reject(new Error('无法连接后端服务，请确认 Spring Boot 已启动在 http://localhost:8080'))
    }

    if (error.response.status === 404) {
      return Promise.reject(new Error('登录接口未找到，请确认后端已启动且 Vite 代理已转发到 http://localhost:8080'))
    }

    const message = error.response.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

const originalGet = http.get.bind(http)
const originalPost = http.post.bind(http)
const originalPut = http.put.bind(http)
const originalPatch = http.patch.bind(http)
const originalDelete = http.delete.bind(http)

http.get = function get<T = any, R = T, D = any>(url: string, config?: CachedAxiosRequestConfig<D>) {
  const behavior = resolveGetRequestBehavior(url, config)
  const requestKey = buildRequestKey(url, config)
  const cachedResponse = getCachedResponse(requestKey)

  if (cachedResponse !== undefined) {
    return Promise.resolve(cachedResponse as R)
  }

  if (behavior.dedupe) {
    const inflightRequest = inflightGetRequests.get(requestKey)
    if (inflightRequest) {
      return inflightRequest as Promise<R>
    }
  }

  const request = originalGet<T, R, D>(url, config)
    .then((data) => {
      setCachedResponse(requestKey, data, behavior.ttl || 0)
      return data
    })
    .finally(() => {
      inflightGetRequests.delete(requestKey)
    })

  if (behavior.dedupe) {
    inflightGetRequests.set(requestKey, request as Promise<unknown>)
  }

  return request
}

http.post = function post<T = any, R = T, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
  return originalPost<T, R, D>(url, data, config).then((result) => {
    clearCachedResponses()
    return result
  })
}

http.put = function put<T = any, R = T, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
  return originalPut<T, R, D>(url, data, config).then((result) => {
    clearCachedResponses()
    return result
  })
}

http.patch = function patch<T = any, R = T, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
  return originalPatch<T, R, D>(url, data, config).then((result) => {
    clearCachedResponses()
    return result
  })
}

http.delete = function remove<T = any, R = T, D = any>(url: string, config?: AxiosRequestConfig<D>) {
  return originalDelete<T, R, D>(url, config).then((result) => {
    clearCachedResponses()
    return result
  })
}

export default http
