create extension if not exists pgcrypto;

create table if not exists public.clients (
    id text primary key,
    client_type text not null check (client_type in ('browser', 'android')),
    label text not null,
    platform text,
    push_token text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    last_seen_at timestamptz
);

create table if not exists public.pair_codes (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    browser_client_id text not null references public.clients(id) on delete cascade,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default timezone('utc'::text, now())
);

create table if not exists public.pair_links (
    id uuid primary key default gen_random_uuid(),
    browser_client_id text not null references public.clients(id) on delete cascade,
    phone_client_id text not null references public.clients(id) on delete cascade,
    status text not null default 'active' check (status in ('active', 'revoked')),
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    unique (browser_client_id, phone_client_id)
);

create table if not exists public.push_events (
    id uuid primary key default gen_random_uuid(),
    pair_link_id uuid references public.pair_links(id) on delete set null,
    source_client_id text not null references public.clients(id) on delete cascade,
    target_client_id text not null references public.clients(id) on delete cascade,
    payload_type text not null check (payload_type in ('link', 'call')),
    payload text not null,
    status text not null default 'queued' check (status in ('queued', 'pending_provider', 'sent', 'delivered', 'opened', 'failed')),
    backend_message text,
    created_at timestamptz not null default timezone('utc'::text, now()),
    sent_at timestamptz,
    delivered_at timestamptz,
    failed_at timestamptz,
    updated_at timestamptz not null default timezone('utc'::text, now())
);

create table if not exists public.activity_logs (
    id bigint generated always as identity primary key,
    level text not null check (level in ('info', 'success', 'error-client', 'error-server')),
    message text not null,
    related_event_id uuid references public.push_events(id) on delete set null,
    created_at timestamptz not null default timezone('utc'::text, now())
);

create index if not exists idx_pair_codes_expires_at on public.pair_codes (expires_at);
create index if not exists idx_pair_links_browser on public.pair_links (browser_client_id);
create index if not exists idx_pair_links_phone on public.pair_links (phone_client_id);
create index if not exists idx_push_events_target_created on public.push_events (target_client_id, created_at desc);
create index if not exists idx_push_events_status_created on public.push_events (status, created_at desc);
create index if not exists idx_activity_logs_created on public.activity_logs (created_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = timezone('utc'::text, now());
    return new;
end;
$$;

drop trigger if exists trg_clients_updated_at on public.clients;
create trigger trg_clients_updated_at
before update on public.clients
for each row
execute function public.set_updated_at();

drop trigger if exists trg_pair_links_updated_at on public.pair_links;
create trigger trg_pair_links_updated_at
before update on public.pair_links
for each row
execute function public.set_updated_at();

drop trigger if exists trg_push_events_updated_at on public.push_events;
create trigger trg_push_events_updated_at
before update on public.push_events
for each row
execute function public.set_updated_at();

-- Temporary open policies while v1 is pairing-only and auth is disabled.
-- Tighten these policies when signed pairing tokens/client auth is introduced.
alter table public.clients enable row level security;
alter table public.pair_codes enable row level security;
alter table public.pair_links enable row level security;
alter table public.push_events enable row level security;
alter table public.activity_logs enable row level security;

drop policy if exists "temporary_open_access_clients" on public.clients;
create policy "temporary_open_access_clients"
on public.clients for all
using (true)
with check (true);

drop policy if exists "temporary_open_access_pair_codes" on public.pair_codes;
create policy "temporary_open_access_pair_codes"
on public.pair_codes for all
using (true)
with check (true);

drop policy if exists "temporary_open_access_pair_links" on public.pair_links;
create policy "temporary_open_access_pair_links"
on public.pair_links for all
using (true)
with check (true);

drop policy if exists "temporary_open_access_push_events" on public.push_events;
create policy "temporary_open_access_push_events"
on public.push_events for all
using (true)
with check (true);

drop policy if exists "temporary_open_access_activity_logs" on public.activity_logs;
create policy "temporary_open_access_activity_logs"
on public.activity_logs for all
using (true)
with check (true);

create or replace function public.cleanup_24h_logs()
returns void
language plpgsql
security definer
as $$
begin
    delete from public.activity_logs
    where created_at < timezone('utc'::text, now()) - interval '24 hours';

    delete from public.pair_codes
    where expires_at < timezone('utc'::text, now()) - interval '24 hours';
end;
$$;
