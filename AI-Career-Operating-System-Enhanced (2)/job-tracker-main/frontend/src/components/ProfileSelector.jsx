import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from 'react-query'
import { api } from '../api/client'
import { useProfile } from '../context/ProfileContext'

function initials(name) {
  return (name ?? '?')
    .split(' ')
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() ?? '')
    .join('')
}

function AddProfileModal({ onClose }) {
  const [name, setName]   = useState('')
  const [file, setFile]   = useState(null)
  const [error, setError] = useState('')
  const fileRef           = useRef()
  const qc                = useQueryClient()
  const { selectProfile } = useProfile()

  const mutation = useMutation(
    async () => {
      const profile = await api.createProfile(name.trim())
      if (file) await api.uploadResume(profile.id, file)
      return profile
    },
    {
      onSuccess: async (profile) => {
        await qc.invalidateQueries('profiles')
        selectProfile(profile.id)
        onClose()
      },
      onError: (e) => setError(e.message),
    }
  )

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) { setError('Name is required'); return }
    setError('')
    mutation.mutate()
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-box" onClick={e => e.stopPropagation()}>
        <h2 className="modal-title">Add person</h2>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <input
            className="onboarding-input"
            placeholder="Name"
            value={name}
            onChange={e => setName(e.target.value)}
            autoFocus
          />

          <div
            className={`onboarding-dropzone${file ? ' has-file' : ''}`}
            style={{ padding: '10px 16px' }}
            onClick={() => fileRef.current?.click()}
            onDragOver={e => e.preventDefault()}
            onDrop={e => { e.preventDefault(); setFile(e.dataTransfer.files[0]) }}
          >
            {file
              ? <><span className="drop-icon">📄</span>{file.name}</>
              : <><span className="drop-icon">⬆</span>Resume PDF (optional)</>}
          </div>
          <input ref={fileRef} type="file" accept=".pdf" style={{ display: 'none' }}
            onChange={e => setFile(e.target.files[0])} />

          {error && <p className="onboarding-error" style={{ margin: 0 }}>{error}</p>}

          <div className="modal-actions">
            <button type="button" className="btn-cancel" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-confirm" disabled={!name.trim() || mutation.isLoading}>
              {mutation.isLoading ? (file ? 'Analyzing…' : 'Creating…') : 'Add'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function ProfileSelector() {
  const { profileId, profiles, selectProfile } = useProfile()
  const [showAdd, setShowAdd] = useState(false)

  if (!profiles || profiles.length === 0) return null

  return (
    <>
      <div className="profile-selector">
        {profiles.map(p => (
          <button
            key={p.id}
            className={`profile-avatar${p.id === profileId ? ' active' : ''}`}
            onClick={() => selectProfile(p.id)}
            title={p.name}
          >
            {initials(p.name)}
          </button>
        ))}
        <button className="profile-avatar add" onClick={() => setShowAdd(true)} title="Add person">
          +
        </button>
      </div>

      {showAdd && <AddProfileModal onClose={() => setShowAdd(false)} />}
    </>
  )
}
