/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the backend API; defaults to "/api" (dev-server proxy). Never put secrets in VITE_* variables. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
