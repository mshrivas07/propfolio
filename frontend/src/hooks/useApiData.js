import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Loads data from the API and exposes loading / error states plus `reload`.
 * `load` must be stable (wrap in useCallback or pass a module-level function).
 * `reload({ silent: true })` refreshes without flipping `loading` (for background polling).
 */
export function useApiData(load) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const [version, setVersion] = useState(0)
  const silentRef = useRef(false)

  useEffect(() => {
    let cancelled = false
    if (!silentRef.current) setState((s) => ({ ...s, loading: true, error: null }))
    silentRef.current = false
    load()
      .then((data) => !cancelled && setState({ data, error: null, loading: false }))
      .catch((error) => !cancelled && setState((s) => ({ ...s, error, loading: false })))
    return () => {
      cancelled = true
    }
  }, [load, version])

  const reload = useCallback((options) => {
    silentRef.current = Boolean(options?.silent)
    setVersion((v) => v + 1)
  }, [])

  // Lets a page replace the data with a fresh copy it already has (e.g. from a save response).
  const setData = useCallback((data) => setState({ data, error: null, loading: false }), [])

  return { ...state, reload, setData }
}

/** Calls `callback` every `ms` while `active` is true. */
export function useInterval(callback, ms, active) {
  const saved = useRef(callback)
  useEffect(() => {
    saved.current = callback
  }, [callback])
  useEffect(() => {
    if (!active) return undefined
    const id = setInterval(() => saved.current(), ms)
    return () => clearInterval(id)
  }, [ms, active])
}
