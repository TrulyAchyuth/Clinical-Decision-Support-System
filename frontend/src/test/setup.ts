import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { clearCsrfToken, setUnauthorizedHandler } from '../api/client'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  clearCsrfToken()
  setUnauthorizedHandler(undefined)
})
