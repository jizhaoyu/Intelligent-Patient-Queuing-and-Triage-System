export interface UserProfile {
  userId: number
  username: string
  nickname: string
  roleCode: string
  deptId?: number | null
  roomId?: number | null
  permissions: string[]
}

export interface LoginResult {
  token: string
  tokenType: string
  expireSeconds: number
  profile: UserProfile
}
