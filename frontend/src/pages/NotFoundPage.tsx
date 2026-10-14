import { Link } from 'react-router'
import { Card } from '../components/ui'

export function NotFoundPage() {
  return (
    <Card>
      <p className="text-slate-700">This page does not exist.</p>
      <Link to="/" className="mt-2 inline-block font-medium text-teal-800 underline">Back to the dashboard</Link>
    </Card>
  )
}
