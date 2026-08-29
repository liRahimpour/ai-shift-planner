import { useEffect, useState } from 'react'
import { useLocations } from '@/api/queries'
import type { Location, UUID } from '@/api/types'

const STORAGE_KEY = 'asp.locationId'

/**
 * Which location the user is currently working on.
 *
 * Almost every manager screen is scoped to one location, and re-picking it on every
 * navigation would be tedious, so the choice is remembered per browser. It falls back to the
 * first location the API returns, which makes the single-location case (most customers, at
 * first) require no choice at all.
 */
export function useSelectedLocation(): {
  locations: Location[]
  locationId: UUID | undefined
  location: Location | undefined
  setLocationId: (id: UUID) => void
  isLoading: boolean
  error: unknown
} {
  const { data: locations = [], isLoading, error } = useLocations()
  const [locationId, setStateLocationId] = useState<UUID | undefined>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) ?? undefined
    } catch {
      return undefined
    }
  })

  // Repair a remembered id that no longer exists (location deactivated, different tenant).
  useEffect(() => {
    if (locations.length === 0) return
    const stillValid = locationId && locations.some((l) => l.id === locationId)
    if (!stillValid) setStateLocationId(locations[0]?.id)
  }, [locations, locationId])

  const setLocationId = (id: UUID) => {
    setStateLocationId(id)
    try {
      localStorage.setItem(STORAGE_KEY, id)
    } catch {
      // Remembering is a convenience, not a requirement.
    }
  }

  return {
    locations,
    locationId,
    location: locations.find((l) => l.id === locationId),
    setLocationId,
    isLoading,
    error,
  }
}
