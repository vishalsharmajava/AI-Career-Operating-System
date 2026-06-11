import { formatDistanceToNow } from 'date-fns'

export default function SyncStatus({ syncs, onSyncClick, syncing }) {
  const latest = syncs?.[0]

  return (
    <div className="sync-status">
      {latest && (
        <div className="sync-info">
          <span className={`sync-dot ${latest.status === 'success' ? 'ok' : 'err'}`} />
          <span className="sync-text">
            Last sync {formatDistanceToNow(new Date(latest.syncedAt), { addSuffix: true })}
            {' · '}
            <strong>+{latest.newJobsCount} new</strong>
            {' · '}
            {latest.totalChecked} checked
          </span>
        </div>
      )}
      <button
        className="btn-sync"
        onClick={onSyncClick}
        disabled={syncing}
      >
        {syncing ? 'Syncing...' : 'Sync now'}
      </button>
    </div>
  )
}
