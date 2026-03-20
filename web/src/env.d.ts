/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_PROXY_TARGET?: string
  readonly VITE_APP_SURFACE?: 'all' | 'admin' | 'workstation' | 'screen'
  readonly VITE_DEFAULT_SCREEN_ROUTE?: string
  readonly VITE_PORT?: string
  readonly VITE_TOKEN_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
