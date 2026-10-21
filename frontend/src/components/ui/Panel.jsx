/** White box used for forms and lists. */
export default function Panel({ className = '', children }) {
  return <div className={`rounded-lg border border-line bg-panel p-4 ${className}`}>{children}</div>
}
