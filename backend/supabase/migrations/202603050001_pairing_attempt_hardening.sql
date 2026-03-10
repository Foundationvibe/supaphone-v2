create table if not exists public.pairing_attempts (
    id bigint generated always as identity primary key,
    phone_client_id text not null,
    request_fingerprint text not null,
    created_at timestamptz not null default timezone('utc'::text, now())
);

alter table public.pairing_attempts enable row level security;

create index if not exists idx_pairing_attempts_phone_created
on public.pairing_attempts (phone_client_id, created_at desc);

create index if not exists idx_pairing_attempts_fingerprint_created
on public.pairing_attempts (request_fingerprint, created_at desc);

revoke all on table public.pairing_attempts from anon, authenticated;
revoke all on sequence public.pairing_attempts_id_seq from anon, authenticated;

create or replace function public.cleanup_24h_logs()
returns void
language plpgsql
security definer
as $$
begin
    delete from public.activity_logs
    where created_at < timezone('utc'::text, now()) - interval '24 hours';

    delete from public.push_events
    where created_at < timezone('utc'::text, now()) - interval '24 hours';

    delete from public.pairing_attempts
    where created_at < timezone('utc'::text, now()) - interval '24 hours';
end;
$$;

revoke execute on function public.cleanup_24h_logs() from public;
grant execute on function public.cleanup_24h_logs() to postgres, service_role;
