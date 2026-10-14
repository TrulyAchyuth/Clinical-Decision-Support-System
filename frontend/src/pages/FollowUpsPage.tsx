import { followUpsApi } from '../api/followUps'
import type { FollowUp, FollowUpTiming } from '../api/types'
import { FollowUpList } from '../components/FollowUps'
import { Card, EmptyState, ErrorAlert, Loading, PageHeader } from '../components/ui'
import { useAsync } from '../hooks/useAsync'

const groups: { timing: FollowUpTiming; title: string; empty: string }[] = [
  { timing: 'OVERDUE', title: 'Overdue', empty: 'No overdue follow-ups.' },
  { timing: 'UPCOMING', title: 'Upcoming', empty: 'Nothing due in the upcoming window (7 days by default).' },
  { timing: 'LATER', title: 'Scheduled later', empty: 'Nothing scheduled further ahead.' },
  { timing: 'COMPLETED', title: 'Completed', empty: 'No completed follow-ups.' },
  { timing: 'CANCELLED', title: 'Cancelled', empty: 'No cancelled follow-ups.' },
]

/** All patients' follow-ups, grouped by the timing the backend calculated when the page loaded. */
export function FollowUpsPage() {
  const list = useAsync(() => followUpsApi.listAll(), [])
  return (
    <>
      <PageHeader title="Follow-ups"
        description="Overdue and upcoming are calculated from the scheduled time each time this page loads. Schedule follow-ups from a patient's page." />
      {list.loading ? <Loading label="Loading follow-ups…" /> : list.error ? <ErrorAlert error={list.error} onRetry={list.reload} /> : (
        <div className="space-y-6">
          {groups.map((group) => {
            const rows = (list.data ?? []).filter((f: FollowUp) => f.timing === group.timing)
            const ordered = group.timing === 'COMPLETED' || group.timing === 'CANCELLED' ? [...rows].reverse() : rows
            return (
              <Card key={group.timing} title={`${group.title} (${rows.length})`}>
                {rows.length === 0 ? <EmptyState>{group.empty}</EmptyState> : <FollowUpList rows={ordered} showPatient onChanged={list.reload} />}
              </Card>
            )
          })}
        </div>
      )}
    </>
  )
}
