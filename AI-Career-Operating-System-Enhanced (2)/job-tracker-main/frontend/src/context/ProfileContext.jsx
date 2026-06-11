import { createContext, useContext, useState } from 'react'
import { useQuery } from 'react-query'
import { api } from '../api/client'

const ProfileContext = createContext(null)

export function ProfileProvider({ children }) {
  const [profileId, setProfileId] = useState(() =>
    parseInt(localStorage.getItem('selectedProfileId') || '1')
  )

  const { data: profiles, isLoading, refetch } = useQuery('profiles', api.listProfiles, {
    staleTime: 30_000,
  })

  function selectProfile(id) {
    setProfileId(id)
    localStorage.setItem('selectedProfileId', String(id))
  }

  const activeProfile = profiles?.find(p => p.id === profileId) ?? profiles?.[0]
  const resolvedId    = activeProfile?.id ?? profileId

  return (
    <ProfileContext.Provider value={{ profileId: resolvedId, profiles, isLoading, selectProfile, refetchProfiles: refetch }}>
      {children}
    </ProfileContext.Provider>
  )
}

export const useProfile = () => useContext(ProfileContext)
