import { useState } from 'react'
import { useMutation, useQueryClient } from 'react-query'
import { formatDistanceToNow, parseISO } from 'date-fns'
import { api } from '../api/client'

const SOURCE_LOGOS = {
  greenhouse: 'https://logo.clearbit.com/greenhouse.io',
  lever:      'https://logo.clearbit.com/lever.co',
  ashby:      'https://logo.clearbit.com/ashbyhq.com',
  remotive:   'https://logo.clearbit.com/remotive.com',
}

function LogoImage({ src, source, company }) {
  const [stage, setStage] = useState(0) // 0=company 1=source 2=letter
  const fallbackSrc = SOURCE_LOGOS[source]

  if (stage === 2 || (!src && !fallbackSrc)) {
    return <div className="row-logo">{company?.charAt(0)?.toUpperCase()}</div>
  }

  const activeSrc = stage === 0 ? src : fallbackSrc
  if (!activeSrc) return <div className="row-logo">{company?.charAt(0)?.toUpperCase()}</div>

  return (
    <img
      src={activeSrc}
      alt={company}
      className="row-logo-img"
      onError={() => setStage(s => s + 1)}
    />
  )
}

function ScorePill({ score }) {
  const color = score >= 80 ? '#22c55e' : score >= 60 ? '#f97316' : '#94a3b8'
  return (
    <div className="score-inline">
      <span className="score-pill" style={{ background: color }}>{score}%</span>
      <div className="score-mini-track">
        <div className="score-mini-fill" style={{ width: `${score}%`, background: color }} />
      </div>
    </div>
  )
}

function PostedAgo({ dateStr }) {
  if (!dateStr) return null
  try {
    const date = parseISO(dateStr)
    const ago = formatDistanceToNow(date, { addSuffix: true })
    return <span className="row-posted">{ago}</span>
  } catch {
    return null
  }
}

export default function JobCard({ job, saved, onToggleSaved, profileId = 1 }) {
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [notes, setNotes] = useState('')
  const [showApplyConfirm, setShowApplyConfirm] = useState(false)

  const applyMutation = useMutation(
    () => api.apply(job.id, profileId, notes),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['jobs', profileId])
        queryClient.invalidateQueries(['applications', profileId])
        setShowApplyConfirm(false)
        setNotes('')
      },
    }
  )

  const dismissMutation = useMutation(
    () => job.dismissed ? api.undismissJob(job.id) : api.dismissJob(job.id),
    { onSuccess: () => queryClient.invalidateQueries(['jobs', profileId]) }
  )

  const locationLabel = job.remote ? 'Remote' : job.location

  return (
    <div className={`job-row ${expanded ? 'open' : ''} ${saved ? 'saved' : ''} ${job.applied ? 'applied' : ''}`}>
      <div className="job-row-main">
        <LogoImage src={job.logoUrl} source={job.source} company={job.company} />

        <div className="job-row-info">
          <span className="row-title">{job.title}</span>
          <span className="row-sep"> | </span>
          <span className="row-company">{job.company}</span>
          {locationLabel && <span className="row-loc-inline"> ({locationLabel})</span>}
        </div>

        <div className="job-row-right">
          {locationLabel && <span className="row-tag">{locationLabel}</span>}
          {job.applied && <span className="row-tag applied-tag">Applied</span>}
          <PostedAgo dateStr={job.postedAt || job.firstSeenAt} />
          <ScorePill score={job.matchScore} />

          <button className="btn-show-more" onClick={() => setExpanded(v => !v)}>
            {expanded ? 'Show Less ▲' : 'Show More ▾'}
          </button>

          <button
            className={`btn-star ${saved ? 'active' : ''}`}
            onClick={onToggleSaved}
            title={saved ? 'Remover da avaliação' : 'Marcar para avaliar'}
          >
            {saved ? '★' : '☆'}
          </button>

          <button
            className={`btn-dismiss ${job.dismissed ? 'active' : ''}`}
            onClick={() => dismissMutation.mutate()}
            title={job.dismissed ? 'Desfazer' : 'Não vou aplicar'}
            disabled={dismissMutation.isLoading}
          >
            ✕
          </button>
        </div>
      </div>

      {expanded && (
        <div className="job-row-detail">
          {job.summary && <p className="row-summary">{job.summary}</p>}

          {(job.keyPoints?.length > 0 || job.requirements?.length > 0) && (
            <div className="row-detail-cols">
              {job.keyPoints?.length > 0 && (
                <div className="row-detail-section">
                  <div className="row-detail-label">Key Points</div>
                  <ul>{job.keyPoints.map((p, i) => <li key={i}>{p}</li>)}</ul>
                </div>
              )}
              {job.requirements?.length > 0 && (
                <div className="row-detail-section">
                  <div className="row-detail-label">Requirements</div>
                  <ul>{job.requirements.map((r, i) => <li key={i}>{r}</li>)}</ul>
                </div>
              )}
            </div>
          )}

          <a href={job.url} target="_blank" rel="noopener noreferrer" className="show-details-link">
            Show details.
          </a>

          <div className="row-actions">
            <a href={job.applyUrl} target="_blank" rel="noopener noreferrer" className="btn-apply">
              Apply
            </a>
            {!job.applied && !showApplyConfirm && (
              <button className="btn-mark-applied" onClick={() => setShowApplyConfirm(true)}>
                Mark as applied
              </button>
            )}
            {showApplyConfirm && (
              <>
                <input
                  className="notes-input"
                  placeholder="Notes (optional)"
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                />
                <button
                  className="btn-confirm"
                  onClick={() => applyMutation.mutate()}
                  disabled={applyMutation.isLoading}
                >
                  {applyMutation.isLoading ? 'Saving...' : 'Confirm'}
                </button>
                <button className="btn-cancel" onClick={() => setShowApplyConfirm(false)}>
                  Cancel
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
