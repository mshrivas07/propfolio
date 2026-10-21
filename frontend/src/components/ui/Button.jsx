const variants = {
  primary: 'bg-action text-white hover:bg-action-strong',
  secondary: 'border border-line bg-panel text-ink hover:bg-surface',
  danger: 'border border-line bg-panel text-danger hover:bg-danger/5',
  link: 'px-0 py-0 text-action underline-offset-4 hover:underline',
}

export default function Button({ variant = 'primary', type = 'button', className = '', children, ...props }) {
  const shape = variant === 'link' ? '' : 'rounded-md px-3 py-2'
  return (
    <button
      type={type}
      className={`${shape} text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
