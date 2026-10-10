import type { InputHTMLAttributes } from 'react'
import { useFormContext } from 'react-hook-form'
import { Field, inputClass, type FieldProps } from './Field'

type TextFieldProps = FieldProps & Omit<InputHTMLAttributes<HTMLInputElement>, 'name' | 'id'>

export function TextField({ name, label, hint, required, ...input }: TextFieldProps) {
  const { register } = useFormContext()
  return (
    <Field name={name} label={label} hint={hint} required={required}>
      {({ id, invalid, describedBy }) => (
        <input
          id={id}
          className={inputClass}
          aria-invalid={invalid}
          aria-describedby={describedBy}
          aria-required={required || undefined}
          {...input}
          {...register(name, input.type === 'number' ? { valueAsNumber: true } : undefined)}
        />
      )}
    </Field>
  )
}
