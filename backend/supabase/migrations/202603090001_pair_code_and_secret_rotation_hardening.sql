alter table public.pairing_attempts
    add column if not exists pair_code text,
    add column if not exists browser_client_id text;

create index if not exists idx_pairing_attempts_pair_code_created
on public.pairing_attempts (pair_code, created_at desc);

create index if not exists idx_pairing_attempts_browser_created
on public.pairing_attempts (browser_client_id, created_at desc);
