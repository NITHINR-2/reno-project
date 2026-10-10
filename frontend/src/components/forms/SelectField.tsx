import type { SelectHTMLAttributes } from 'react'
import { useFormContext } from 'react-hook-form'
import { Field, inputClass, type FieldProps } from './Field'

export type SelectOption = { value: string; label: string }

type SelectFieldProps = FieldProps &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'name' | 'id'> & { options: SelectOption[]; placeholder?: string }

export function SelectField({ name, label, hint, required, options, placeholder, ...select }: SelectFieldProps) {
  const { register } = useFormContext()
  return (
    <Field name={name} label={label} hint={hint} required={required}>
      {({ id, invalid, describedBy }) => (
        <select
          id={id}
          className={inputClass}
          aria-invalid={invalid}
          aria-describedby={describedBy}
          aria-required={required || undefined}
          {...select}
          {...register(name)}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </Field>
  )
}
