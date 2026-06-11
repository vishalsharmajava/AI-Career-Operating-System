import { useState } from 'react'
import { useQuery } from 'react-query'
import { api } from '../api/client'
import JobCard from './JobCard'

function loadSaved() {
  try { return new Set(JSON.parse(localStorage.getItem('jobs-review') || '[]')) }
  catch { return new Set() }
}
function saveSaved(set) {
  localStorage.setItem('jobs-review', JSON.stringify([...set]))
}

function PillGroup({ value, onChange, options }) {
  return (
    <div className="pill-group">
      {options.map(o => (
        <button
          key={o.value}
          className={`pill-opt ${value === o.value ? 'active' : ''}`}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export default function JobList({ profileId = 1 }) {
  const [tab, setTab]       = useState('all')
  const [search, setSearch] = useState('')
  const [saved, setSaved]   = useState(loadSaved)
  const [sort, setSort]     = useState('recent')   // recent | score
  const [remote, setRemote] = useState('all')       // all | remote
  const [minScore, setMinScore] = useState(0)       // 0 | 60 | 75 | 85

  const { data: jobs = [], isLoading, error } = useQuery(
    ['jobs', profileId],
    () => api.listJobs(200, profileId),
    { refetchInterval: 15 * 60 * 1000 }
  )

  function toggleSaved(id) {
    setSaved(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      saveSaved(next)
      return next
    })
  }

  const filtered = jobs
    .filter(job => {
      if (tab === 'skipped') return job.dismissed
      if (job.dismissed)     return false   // hide dismissed from all other tabs
      if (tab === 'new'     && job.applied)        return false
      if (tab === 'applied' && !job.applied)       return false
      if (tab === 'review'  && !saved.has(job.id)) return false
      if (remote === 'remote' && !job.remote)      return false
      if (job.matchScore < minScore)               return false
      if (search) {
        const q = search.toLowerCase()
        if (!job.title?.toLowerCase().includes(q) && !job.company?.toLowerCase().includes(q)) return false
      }
      return true
    })
    .sort((a, b) => {
      if (sort === 'score') return b.matchScore - a.matchScore
      const da = a.postedAt || a.firstSeenAt || ''
      const db = b.postedAt || b.firstSeenAt || ''
      return db.localeCompare(da)
    })

  const active = jobs.filter(j => !j.dismissed)
  const tabs = [
    { key: 'all',     label: 'All' },
    { key: 'new',     label: `New (${active.filter(j => !j.applied).length})` },
    { key: 'review',  label: `To Review (${saved.size})` },
    { key: 'applied', label: `Applied (${active.filter(j => j.applied).length})` },
    { key: 'skipped', label: `Skipped (${jobs.filter(j => j.dismissed).length})` },
  ]

  if (isLoading) return <div className="loading">Loading jobs...</div>
  if (error)     return <div className="error">Failed to load jobs: {error.message}</div>

  return (
    <div className="job-list-container">
      <div className="job-list-header">
        <span className="job-count">{filtered.length} vagas</span>
        <div className="filter-pills">
          {tabs.map(({ key, label }) => (
            <button
              key={key}
              className={`pill ${tab === key ? 'active' : ''}`}
              onClick={() => setTab(key)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="filter-bar">
        <div className="filter-group">
          <span className="filter-label">Ordenar</span>
          <PillGroup
            value={sort}
            onChange={setSort}
            options={[
              { value: 'recent', label: 'Mais recentes' },
              { value: 'score',  label: 'Melhor match' },
            ]}
          />
        </div>

        <div className="filter-group">
          <span className="filter-label">Regime</span>
          <PillGroup
            value={remote}
            onChange={setRemote}
            options={[
              { value: 'all',    label: 'Todos' },
              { value: 'remote', label: 'Remote' },
            ]}
          />
        </div>

        <div className="filter-group">
          <span className="filter-label">Match mínimo</span>
          <PillGroup
            value={minScore}
            onChange={v => setMinScore(Number(v))}
            options={[
              { value: 0,  label: 'Todos' },
              { value: 60, label: '60%+' },
              { value: 75, label: '75%+' },
              { value: 85, label: '85%+' },
            ]}
          />
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="empty">
          {tab === 'review'
            ? 'Nenhuma vaga marcada. Clique em ☆ para marcar para avaliação.'
            : 'Nenhuma vaga encontrada.'}
        </div>
      ) : (
        <div className="job-rows">
          {filtered.map(job => (
            <JobCard
              key={job.id}
              job={job}
              profileId={profileId}
              saved={saved.has(job.id)}
              onToggleSaved={() => toggleSaved(job.id)}
            />
          ))}
        </div>
      )}

      <div className="bottom-bar">
        <div className="search-wrap">
          <span className="search-icon">🔍</span>
          <input
            className="search-input"
            placeholder="Buscar por título ou empresa"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
      </div>
    </div>
  )
}
