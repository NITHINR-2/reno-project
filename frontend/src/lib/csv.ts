import { saveBlob } from './download'

export type CsvColumn<T> = { header: string; value: (row: T) => string | number | null | undefined }

/** Text a spreadsheet would run as a formula gets a leading apostrophe (CSV injection). */
function cell(value: string | number | null | undefined) {
  if (value == null) return ''
  let text = String(value)
  if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) text = `'${text}`
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

export function toCsv<T>(columns: CsvColumn<T>[], rows: T[]) {
  const lines = [columns.map((c) => cell(c.header)).join(','), ...rows.map((r) => columns.map((c) => cell(c.value(r))).join(','))]
  return lines.join('\r\n') + '\r\n'
}

/** Saves rows as a UTF-8 CSV (with a BOM, so Excel shows names and ₹ correctly). */
export function downloadCsv<T>(fileName: string, columns: CsvColumn<T>[], rows: T[]) {
  saveBlob(new Blob(['﻿', toCsv(columns, rows)], { type: 'text/csv;charset=utf-8' }), fileName)
}
