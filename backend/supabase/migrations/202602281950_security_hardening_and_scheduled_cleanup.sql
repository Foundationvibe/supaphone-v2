create extension if not exists pg_cron;

-- Pairing-only architecture uses Edge Functions + service role.
-- Block direct table access from anon/authenticated roles.
revoke all on table public.clients from anon, authenticated;
revoke all on table public.pair_codes from anon, authenticated;
revoke all on table public.pair_links from anon, authenticated;
revoke all on table public.push_events from anon, authenticated;
revoke all on table public.activity_logs from anon, authenticated;
revoke all on sequence public.activity_logs_id_seq from anon, authenticated;

-- Keep operational data in a 24-hour window.
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
end;
$$;

revoke execute on function public.cleanup_24h_logs() from public;
grant execute on function public.cleanup_24h_logs() to postgres, service_role;

revoke execute on function public.cleanup_pair_codes_1h() from public;
grant execute on function public.cleanup_pair_codes_1h() to postgres, service_role;

do $job$
declare
    existing_job_id bigint;
begin
    select jobid
    into existing_job_id
    from cron.job
    where jobname = 'cleanup-24h-operational-data'
    limit 1;

    if existing_job_id is not null then
        perform cron.unschedule(existing_job_id);
    end if;

    perform cron.schedule(
        'cleanup-24h-operational-data',
        '15 * * * *',
        $cmd$select public.cleanup_24h_logs();$cmd$
    );
end;
$job$;
