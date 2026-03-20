export interface ApiResult<T> {
  success: boolean
  code: string
  message: string
  data: T
}
