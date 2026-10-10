import type { TextareaHTMLAttributes } from 'react'
import { useFormContext } from 'react-hook-form'
import { Field, inputClass, type FieldProps } from './Field'

type TextareaFieldProps = FieldProps & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'name' | 'id'>

export function TextareaField({ name, label, hint, required, rows = 3, ...textarea }: TextareaFieldProps) {
  const { register } = useFormContext()
  return (
    <Field name={name} label={label} hint={hint} required={required}>
      {({ id, invalid, describedBy }) => (
        <textarea
          id={id}
          rows={rows}
          className={inputClass}
          aria-invalid={invalid}
          aria-describedby={describedBy}
          aria-required={required || undefined}
          {...textarea}
          {...register(name)}
        />
      )}
    </Field>
  )
}
