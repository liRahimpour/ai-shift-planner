/// <reference types="vite/client" />

/**
 * Build-time configuration. Only `VITE_`-prefixed variables reach the browser bundle, which
 * is the guard that stops a server-side secret from being baked into a static asset.
 */
interface ImportMetaEnv {
  /**
   * Absolute origin of the backend, e.g. "https://api.example.com". Left empty in
   * development and in the nginx-served container, where /api is proxied on the same origin
   * and there is therefore no CORS configuration to keep in sync.
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
