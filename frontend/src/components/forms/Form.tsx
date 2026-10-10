import type { ReactNode } from 'react'
import { FormProvider, type FieldValues, type SubmitHandler, type UseFormReturn } from 'react-hook-form'

type FormProps<T extends FieldValues> = {
  form: UseFormReturn<T>
  onSubmit: SubmitHandler<T>
  children: ReactNode
  className?: string
  'aria-label'?: string
}

/** RHF provider + submit wiring. On invalid submit, smoothly scrolls to and focuses the first error. */
export function Form<T extends FieldValues>({ form, onSubmit, children, className, ...rest }: FormProps<T>) {
  return (
    <FormProvider {...form}>
      <form
        noValidate
        className={className}
        aria-label={rest['aria-label']}
        onSubmit={(event) => {
          // currentTarget is cleared once the handler returns, and validation is async.
          const formElement = event.currentTarget
          void form.handleSubmit(onSubmit, () => focusFirstError(formElement))(event)
        }}
      >
        {children}
      </form>
    </FormProvider>
  )
}

function focusFirstError(formElement: HTMLFormElement) {
  // Wait one frame so aria-invalid has been rendered.
  requestAnimationFrame(() => {
    const first = formElement.querySelector<HTMLElement>('[aria-invalid="true"]')
    if (!first) return
    first.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
    first.focus({ preventScroll: true })
  })
}
