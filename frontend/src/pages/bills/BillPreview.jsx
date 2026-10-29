import { useEffect, useState } from 'react'
import { fetchBillFile } from '../../lib/api'

/**
 * Shows the original bill. The file needs the sign-in token, so it is fetched
 * and shown from a temporary blob URL (released when the view closes).
 */
export default function BillPreview({ bill }) {
  const [state, setState] = useState({ url: null, error: null })

  useEffect(() => {
    let url = null
    let cancelled = false
    setState({ url: null, error: null })
    fetchBillFile(bill.id)
      .then((blob) => {
        if (cancelled) return
        url = URL.createObjectURL(blob)
        setState({ url, error: null })
      })
      .catch((error) => !cancelled && setState({ url: null, error }))
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [bill.id])

  if (state.error) {
    return (
      <p role="alert" className="rounded-md border border-danger/30 bg-panel p-4 text-sm text-danger">
        Couldn't load the file. {state.error.message}
      </p>
    )
  }
  if (!state.url) {
    return <p className="rounded-md border border-line bg-panel p-8 text-center text-sm text-ink-muted">Loading the file…</p>
  }

  const isPdf = bill.contentType === 'application/pdf'
  return (
    <div>
      <div className="overflow-hidden rounded-md border border-line bg-panel">
        {isPdf ? (
          <iframe src={state.url} title={`Bill: ${bill.originalFilename}`} className="h-[75vh] w-full" />
        ) : (
          <img src={state.url} alt={`Bill: ${bill.originalFilename}`} className="mx-auto max-h-[75vh] w-auto" />
        )}
      </div>
      <p className="mt-2 flex gap-4 text-sm">
        <a href={state.url} target="_blank" rel="noreferrer" className="text-action underline-offset-4 hover:underline">
          Open in a new tab
        </a>
        <a href={state.url} download={bill.originalFilename} className="text-action underline-offset-4 hover:underline">
          Download
        </a>
      </p>
    </div>
  )
}
