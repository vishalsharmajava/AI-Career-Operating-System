import { useState } from 'react'
import { useQuery, useQueryClient, useMutation } from 'react-query'
import { api } from './api/client'
import { ProfileProvider, useProfile } from './context/ProfileContext'
import JobList from './components/JobList'
import ApplicationHistory from './components/ApplicationHistory'
import SyncStatus from './components/SyncStatus'
import SyncDaysModal from './components/SyncDaysModal'
import ProfileSelector from './components/ProfileSelector'
import OnboardingScreen from './components/OnboardingScreen'

const STALE_THRESHOLD_DAYS = 15

function needsDaysPicker(syncs) {
  if (!syncs || syncs.length === 0) return { needed: true, reason: 'This is your first sync.' }
  const last = syncs.find(s => s.status === 'success')
  if (!last) return { needed: true, reason: 'No successful syncs found yet.' }
  const daysSince = (Date.now() - new Date(last.syncedAt)) / 86_400_000
  if (daysSince > STALE_THRESHOLD_DAYS) {
    return {
      needed: true,
      reason: `Last sync was ${Math.round(daysSince)} days ago. Choose how far back to look.`,
    }
  }
  return { needed: false }
}

function AppShell() {
  const [tab, setTab]           = useState('jobs')
  const [showModal, setShowModal] = useState(false)
  const [modalReason, setModalReason] = useState('')
  const { profileId, profiles, isLoading } = useProfile()
  const qc = useQueryClient()

  const { data: syncs } = useQuery(
    ['syncs', profileId],
    () => api.listSyncs(10, profileId),
    { refetchInterval: 60_000, enabled: !!profileId }
  )

  const syncMutation = useMutation(
    ({ days }) => api.triggerSync(days, profileId),
    {
      onSuccess: () => {
        qc.invalidateQueries(['jobs', profileId])
        qc.invalidateQueries(['syncs', profileId])
      },
    }
  )

  // Show onboarding if no profiles exist (and not loading)
  if (!isLoading && profiles?.length === 0) {
    return <OnboardingScreen />
  }

  function handleSyncClick() {
    const { needed, reason } = needsDaysPicker(syncs)
    if (needed) { setModalReason(reason); setShowModal(true) }
    else syncMutation.mutate({ days: 0 })
  }

  return (
    <div className="app">
      <header className="header">
        <div className="header-left">
          <h1>Job Tracker</h1>
          <span className="header-sub">Java · Tech Lead · Staff Engineer</span>
        </div>
        <div className="header-right">
          <ProfileSelector />
          <SyncStatus
            syncs={syncs}
            onSyncClick={handleSyncClick}
            syncing={syncMutation.isLoading}
          />
        </div>
      </header>

      <nav className="tabs">
        <button className={tab === 'jobs' ? 'tab active' : 'tab'} onClick={() => setTab('jobs')}>
          Jobs
        </button>
        <button className={tab === 'applications' ? 'tab active' : 'tab'} onClick={() => setTab('applications')}>
          Applications
        </button>
      </nav>

      <main className="main">
        {tab === 'jobs'         && <JobList profileId={profileId} />}
        {tab === 'applications' && <ApplicationHistory profileId={profileId} />}
      </main>

      {showModal && (
        <SyncDaysModal
          reason={modalReason}
          onConfirm={(days) => { setShowModal(false); syncMutation.mutate({ days }) }}
          onCancel={() => setShowModal(false)}
        />
      )}
    </div>
  )
}

export default function App() {
  return (
    <ProfileProvider>
      <AppShell />
    </ProfileProvider>
  )
}
