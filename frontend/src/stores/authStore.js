import { create } from 'zustand'

/**
 * Holds the signed-in user's session. Placeholder until Phase 1 wires in Supabase Auth.
 */
export const useAuthStore = create((set) => ({
  session: null,
  setSession: (session) => set({ session }),
  clearSession: () => set({ session: null }),
}))
