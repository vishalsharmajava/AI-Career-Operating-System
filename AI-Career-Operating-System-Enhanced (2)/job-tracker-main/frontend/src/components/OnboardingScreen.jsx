import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from 'react-query'
import { api } from '../api/client'
import { useProfile } from '../context/ProfileContext'

export default function OnboardingScreen() {
  const [name, setName]   = useState('')
  const [file, setFile]   = useState(null)
  const [step, setStep]   = useState('name') // 'name' | 'resume'
  const [error, setError] = useState('')
  const fileRef           = useRef()
  const qc                = useQueryClient()
  const { selectProfile } = useProfile()

  const createMutation = useMutation(
    () => api.createProfile(name.trim()),
    {
      onSuccess: async (profile) => {
        if (file) {
          await api.uploadResume(profile.id, file)
        }
        await qc.invalidateQueries('profiles')
        selectProfile(profile.id)
      },
      onError: (e) => setError(e.message),
    }
  )

  function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) { setError('Enter your name'); return }
    setError('')
    createMutation.mutate()
  }

  return (
    <div className="onboarding-overlay">
      <div className="onboarding-box">
        <div className="onboarding-logo">
          <span className="onboarding-icon">💼</span>
        </div>
        <h1 className="onboarding-title">Job Tracker</h1>
        <p className="onboarding-sub">
          Automatic search for Java · Tech Lead · Staff Engineer jobs, filtered by AI.
        </p>

        <form className="onboarding-form" onSubmit={handleSubmit}>
          <div className="onboarding-field">
            <label className="onboarding-label">Your name</label>
            <input
              className="onboarding-input"
              placeholder="e.g. Matheus"
              value={name}
              onChange={e => setName(e.target.value)}
              autoFocus
            />
          </div>

          <div className="onboarding-field">
            <label className="onboarding-label">
              Resume / CV <span className="onboarding-optional">(optional — improves job matching)</span>
            </label>
            <div
              className={`onboarding-dropzone${file ? ' has-file' : ''}`}
              onClick={() => fileRef.current?.click()}
              onDragOver={e => e.preventDefault()}
              onDrop={e => { e.preventDefault(); setFile(e.dataTransfer.files[0]) }}
            >
              {file
                ? <><span className="drop-icon">📄</span>{file.name}</>
                : <><span className="drop-icon">⬆</span>Click or drag a PDF</>
              }
            </div>
            <input
              ref={fileRef}
              type="file"
              accept=".pdf"
              style={{ display: 'none' }}
              onChange={e => setFile(e.target.files[0])}
            />
          </div>

          {error && <p className="onboarding-error">{error}</p>}

          <button
            type="submit"
            className="onboarding-btn"
            disabled={!name.trim() || createMutation.isLoading}
          >
            {createMutation.isLoading
              ? (file ? 'Analyzing resume…' : 'Creating profile…')
              : 'Get started'}
          </button>
        </form>
      </div>
    </div>
  )
}
