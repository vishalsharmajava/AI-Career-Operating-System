const BASE = import.meta.env.VITE_API_URL ?? '/api'

async function request(path, options = {}) {
  const isForm = options.body instanceof FormData
  const res = await fetch(`${BASE}${path}`, {
    headers: isForm ? undefined : { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })
  if (!res.ok) {
    const error = await res.text()
    throw new Error(error || res.statusText)
  }
  return res.status === 204 ? null : res.json()
}

export const api = {
  // Profiles
  listProfiles: () => request('/profiles'),
  createProfile: (name) => request('/profiles', {
    method: 'POST',
    body: JSON.stringify({ name }),
  }),
  deleteProfile: (id) => request(`/profiles/${id}`, { method: 'DELETE' }),
  uploadResume: (profileId, file) => {
    const form = new FormData()
    form.append('file', file)
    return request(`/profiles/${profileId}/resume`, { method: 'POST', body: form })
  },

  // Jobs
  listJobs: (limit = 50, profileId = 1) =>
    request(`/jobs?limit=${limit}&profileId=${profileId}`),
  getJob: (id, profileId = 1) =>
    request(`/jobs/${encodeURIComponent(id)}?profileId=${profileId}`),
  dismissJob: (id) =>
    request(`/jobs/${encodeURIComponent(id)}/dismiss`, { method: 'POST' }),
  undismissJob: (id) =>
    request(`/jobs/${encodeURIComponent(id)}/undismiss`, { method: 'POST' }),

  // Applications
  listApplications: (limit = 50, profileId = 1) =>
    request(`/applications?limit=${limit}&profileId=${profileId}`),
  apply: (jobId, profileId = 1, notes = '') =>
    request(`/applications/${encodeURIComponent(jobId)}/apply?profileId=${profileId}`, {
      method: 'POST',
      body: JSON.stringify({ notes }),
    }),
  updateStatus: (jobId, appliedAt, status, profileId = 1) =>
    request(
      `/applications/${encodeURIComponent(jobId)}/${encodeURIComponent(appliedAt)}/status?profileId=${profileId}`,
      { method: 'PATCH', body: JSON.stringify({ status }) }
    ),

  // Sync
  triggerSync: (days = 0, profileId = 1) =>
    request(`/sync?profileId=${profileId}`, {
      method: 'POST',
      body: JSON.stringify({ days }),
    }),
  listSyncs: (limit = 10, profileId = 1) =>
    request(`/sync?limit=${limit}&profileId=${profileId}`),
}
