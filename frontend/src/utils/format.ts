/** "2026-10-13T09:30:00Z" -> local date and time, e.g. "13 Oct 2026, 15:00". */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

/** "1958-04-12" -> "12 Apr 1958" (dates without a time are not shifted by time zone). */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return '—'
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { dateStyle: 'medium' })
}

/** Age in whole years today. */
export function ageInYears(isoDate: string, today = new Date()): number {
  const [year, month, day] = isoDate.split('-').map(Number)
  let age = today.getFullYear() - year
  if (today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day)) age -= 1
  return age
}

/** Value for <input type="datetime-local"> showing the current local time. */
export function nowForDateTimeInput(now = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

/** Value for <input type="date"> showing today's local date (YYYY-MM-DD). */
export function todayForDateInput(now = new Date()): string {
  return nowForDateTimeInput(now).slice(0, 10)
}

/** <input type="datetime-local"> value (local time) -> ISO instant for the backend; null if blank or invalid. */
export function dateTimeInputToIso(value: string): string | null {
  const date = new Date(value)
  return value.trim() === '' || Number.isNaN(date.getTime()) ? null : date.toISOString()
}

/** Text from an <input>, or undefined when blank (so optional fields are omitted). */
export function optionalText(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed === '' ? undefined : trimmed
}

/** Number from an <input type="number">, or undefined when blank. */
export function optionalNumber(value: string): number | undefined {
  return value.trim() === '' ? undefined : Number(value)
}

export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase()
}
