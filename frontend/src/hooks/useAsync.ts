import { useCallback, useEffect, useState } from 'react'

export interface AsyncState<T> {
  data: T | undefined
  error: unknown
  loading: boolean
  /** Loads the data again (e.g. after a change). */
  reload: () => void
}

/**
 * Runs `load` when the component appears and whenever `deps` change, and
 * tracks loading / data / error. Results of outdated requests are ignored.
 */
export function useAsync<T>(load: () => Promise<T>, deps: readonly unknown[]): AsyncState<T> {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<unknown>()
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)

  const stableLoad = useCallback(load, deps)

  useEffect(() => {
    let current = true
    setLoading(true)
    setError(undefined)
    stableLoad().then(
      (result) => { if (current) { setData(result); setLoading(false) } },
      (err: unknown) => { if (current) { setError(err); setLoading(false) } },
    )
    return () => { current = false }
  }, [stableLoad, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data, error, loading, reload }
}
