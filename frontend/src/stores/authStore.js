import { create } from 'zustand'
import { supabase } from '../lib/supabase'

/**
 * Signed-in session from Supabase Auth. `initialized` turns true once the stored
 * session (if any) has been read, so the route guard doesn't flash the login page.
 */
export const useAuthStore = create((set) => ({
  session: null,
  initialized: false,

  init: () => {
    if (!supabase) {
      set({ initialized: true })
      return () => {}
    }
    supabase.auth.getSession().then(({ data }) => {
      set({ session: data.session, initialized: true })
    })
    const { data } = supabase.auth.onAuthStateChange((_event, session) => {
      set({ session, initialized: true })
    })
    return () => data.subscription.unsubscribe()
  },

  signOut: async () => {
    if (supabase) await supabase.auth.signOut()
    set({ session: null })
  },
}))
