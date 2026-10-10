import { isAxiosError } from 'axios'
import { z } from 'zod'

/** RFC 7807 ProblemDetail as returned by the backend's single exception handler. */
const problemSchema = z.object({
  title: z.string().optional(),
  detail: z.string().optional(),
  status: z.number().optional(),
  errors: z.record(z.string(), z.string()).optional(),
})

export type Problem = { status: number | null; title: string; detail: string; errors: Record<string, string> }

export function toProblem(error: unknown): Problem {
  if (isAxiosError(error)) {
    const status = error.response?.status ?? null
    const parsed = problemSchema.safeParse(error.response?.data)
    const body = parsed.success ? parsed.data : {}
    if (status === null) {
      return { status, title: 'Network error', detail: 'The server could not be reached.', errors: {} }
    }
    return {
      status,
      title: body.title ?? `Request failed (${status})`,
      detail: body.detail ?? error.message,
      errors: body.errors ?? {},
    }
  }
  const message = error instanceof Error ? error.message : 'Something went wrong.'
  return { status: null, title: 'Error', detail: message, errors: {} }
}
