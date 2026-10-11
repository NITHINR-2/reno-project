import { isAxiosError } from 'axios'

/** Reads the file name from a Content-Disposition header (`attachment; filename="QT-2026-00001.pdf"`). */
export function fileNameFrom(disposition: string | undefined): string | null {
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  return match ? decodeURIComponent(match[1]) : null
}

/** Hands a downloaded file to the browser as a save, without navigating away. */
export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Revoke after the click has been handled so the save isn't cancelled.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/**
 * With `responseType: 'blob'` an error's ProblemDetail also arrives as a Blob; decode it so `toProblem` can read it.
 * Returns the (possibly updated) error for rethrowing.
 */
export async function blobErrorToJson(error: unknown): Promise<unknown> {
  if (isAxiosError(error) && error.response && error.response.data instanceof Blob) {
    try {
      error.response.data = JSON.parse(await error.response.data.text()) as unknown
    } catch {
      error.response.data = undefined
    }
  }
  return error
}
