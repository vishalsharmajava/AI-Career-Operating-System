import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from 'react-query'
import { format, formatDistanceToNow } from 'date-fns'
import { api } from '../api/client'

const STATUSES = ['applied', 'interviewing', 'offer', 'rejected', 'withdrawn']

const STATUS_COLORS = {
  applied: '#3b82f6',
  interviewing: '#f59e0b',
  offer: '#22c55e',
  rejected: '#ef4444',
  withdrawn: '#6b7280',
}

export default function ApplicationHistory({ profileId = 1 }) {
  const queryClient = useQueryClient()
  const { data: applications = [], isLoading, error } = useQuery(
    ['applications', profileId],
    () => api.listApplications(100, profileId)
  )

  const statusMutation = useMutation(
    ({ jobId, appliedAt, status }) => api.updateStatus(jobId, appliedAt, status, profileId),
    { onSuccess: () => queryClient.invalidateQueries(['applications', profileId]) }
  )

  if (isLoading) return <div className="loading">Loading applications...</div>
  if (error) return <div className="error">Failed to load applications: {error.message}</div>

  if (applications.length === 0) {
    return (
      <div className="empty">
        No applications yet. Click "Apply" on a job and then "Mark as applied".
      </div>
    )
  }

  return (
    <div className="app-history">
      <div className="app-history-header">
        <h2>{applications.length} Application{applications.length !== 1 ? 's' : ''}</h2>
      </div>

      <table className="app-table">
        <thead>
          <tr>
            <th>Company</th>
            <th>Role</th>
            <th>Applied</th>
            <th>Status</th>
            <th>Notes</th>
          </tr>
        </thead>
        <tbody>
          {applications.map((app) => (
            <tr key={`${app.jobId}-${app.appliedAt}`}>
              <td className="td-company">{app.company}</td>
              <td className="td-title">{app.jobTitle}</td>
              <td className="td-date">
                <span title={format(new Date(app.appliedAt), 'PPpp')}>
                  {formatDistanceToNow(new Date(app.appliedAt), { addSuffix: true })}
                </span>
              </td>
              <td className="td-status">
                <select
                  className="status-select"
                  value={app.status}
                  style={{ borderColor: STATUS_COLORS[app.status] ?? '#6b7280' }}
                  onChange={(e) =>
                    statusMutation.mutate({
                      jobId: app.jobId,
                      appliedAt: app.appliedAt,
                      status: e.target.value,
                    })
                  }
                >
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s.charAt(0).toUpperCase() + s.slice(1)}
                    </option>
                  ))}
                </select>
              </td>
              <td className="td-notes">{app.notes || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
