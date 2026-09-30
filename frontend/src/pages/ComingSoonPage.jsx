export default function ComingSoonPage({ title, phase }) {
  return (
    <section>
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="mt-2 text-ink-muted">This screen is built in Phase {phase}.</p>
    </section>
  )
}
