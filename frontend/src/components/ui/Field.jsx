import { useId } from 'react'

const inputClass =
  'mt-1 w-full rounded-md border bg-panel px-3 py-2 text-sm aria-[invalid=true]:border-danger'

function Wrapper({ id, label, hint, error, children, className = '' }) {
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      {children}
      {hint && !error && <div className="mt-1 text-xs text-ink-muted">{hint}</div>}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * Text, email, date or number input with its label and an error under it.
 * Optional: `id` (to focus it from elsewhere), `inputClassName` (extra classes, e.g. a highlight).
 */
export function TextField({ label, hint, error, className, inputClassName = '', id: givenId, type = 'text', ...props }) {
  const generatedId = useId()
  const id = givenId ?? generatedId
  return (
    <Wrapper id={id} label={label} hint={hint} error={error} className={className}>
      <input
        id={id}
        type={type}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`${inputClass} border-line ${inputClassName}`}
        {...props}
      />
    </Wrapper>
  )
}

/** Select with its label and an error under it. `options` is [{ value, label }]. */
export function SelectField({ label, hint, error, className, inputClassName = '', id: givenId, options, placeholder, ...props }) {
  const generatedId = useId()
  const id = givenId ?? generatedId
  return (
    <Wrapper id={id} label={label} hint={hint} error={error} className={className}>
      <select
        id={id}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`${inputClass} border-line ${inputClassName}`}
        {...props}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </Wrapper>
  )
}
