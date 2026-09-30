import { NavLink, Outlet } from 'react-router'

const navItems = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/properties', label: 'Properties' },
  { to: '/bills', label: 'Bills' },
  { to: '/invoices', label: 'Invoices' },
]

function navClass({ isActive }) {
  const base = 'block rounded-md px-3 py-2 text-sm transition-colors'
  return isActive
    ? `${base} bg-action text-white`
    : `${base} text-ink-muted hover:bg-surface hover:text-ink`
}

export default function AppLayout() {
  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-line bg-panel md:min-h-screen md:w-56 md:border-b-0 md:border-r">
        <div className="px-4 py-4">
          <p className="text-base font-semibold">Propfolio</p>
        </div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-2 pb-3 md:block md:space-y-1 md:pb-0">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <main className="flex-1 px-4 py-6 md:px-10 md:py-8">
        <div className="mx-auto max-w-5xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
