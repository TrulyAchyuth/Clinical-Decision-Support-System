// A few outline icons (24x24) drawn with inline SVG, so no icon library is needed.
const paths = {
  dashboard: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
  patients: 'M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0zM4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1',
  decision: 'M9 12l2 2 4-4M12 3l7 4v5c0 4.4-3 8.3-7 9-4-0.7-7-4.6-7-9V7l7-4z',
  drug: 'M10.5 20.5l10-10a4.95 4.95 0 0 0-7-7l-10 10a4.95 4.95 0 0 0 7 7zM8.5 8.5l7 7',
  disease: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 5v5l3 3',
  lab: 'M9 3h6M10 3v6L4.5 18.5A2 2 0 0 0 6.2 21.5h11.6a2 2 0 0 0 1.7-3L14 9V3',
  rules: 'M4 6h16M4 12h10M4 18h7M17 15l2 2 3-4',
  menu: 'M4 6h16M4 12h16M4 18h16',
} as const

export type IconName = keyof typeof paths

export function Icon({ name, className = 'h-5 w-5' }: { name: IconName; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d={paths[name]} />
    </svg>
  )
}
