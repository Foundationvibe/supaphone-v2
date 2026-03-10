create table if not exists public.request_events (
    id bigint generated always as identity primary key,
    action text not null,
    client_id text,
    request_fingerprint text not null,
    created_at timestamptz not null default timezone('utc'::text, now())
);

alter table public.request_events enable row level security;

create index if not exists idx_request_events_action_client_created
on public.request_events (action, client_id, created_at desc);

create index if not exists idx_request_events_action_fingerprint_created
on public.request_events (action, request_fingerprint, created_at desc);

revoke all on table public.request_events from anon, authenticated;
revoke all on sequence public.request_events_id_seq from anon, authenticated;

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

    delete from public.request_events
    where created_at < timezone('utc'::text, now()) - interval '24 hours';
end;
$$;

revoke execute on function public.cleanup_24h_logs() from public;
grant execute on function public.cleanup_24h_logs() to postgres, service_role;
