import { createClient } from "npm:@supabase/supabase-js@2";
import { requireEnv } from "./runtime.ts";

export function createServiceClient() {
    const supabaseUrl = requireEnv("SUPABASE_URL");
    const serviceRoleKey = requireEnv("SUPABASE_SERVICE_ROLE_KEY");

    return createClient(supabaseUrl, serviceRoleKey, {
        auth: { persistSession: false }
    });
}
