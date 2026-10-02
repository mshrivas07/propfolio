import { createClient } from '@supabase/supabase-js'
import { config } from './config'

export const isSupabaseConfigured = Boolean(config.supabaseUrl && config.supabaseAnonKey)

// Null when .env is missing values; the login page shows a setup message instead of crashing.
export const supabase = isSupabaseConfigured
  ? createClient(config.supabaseUrl, config.supabaseAnonKey, {
      auth: { persistSession: true, autoRefreshToken: true },
    })
  : null
