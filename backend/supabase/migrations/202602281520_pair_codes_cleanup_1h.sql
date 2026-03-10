create extension if not exists pg_cron;

create index if not exists idx_pair_codes_created_at
on public.pair_codes (created_at);

create or replace function public.cleanup_pair_codes_1h()
returns void
language plpgsql
security definer
as $$
begin
    delete from public.pair_codes
    where created_at < timezone('utc'::text, now()) - interval '1 hour';
end;
$$;

do $job$
declare
    existing_job_id bigint;
begin
    select jobid
    into existing_job_id
    from cron.job
    where jobname = 'cleanup-pair-codes-1h'
    limit 1;

    if existing_job_id is not null then
        perform cron.unschedule(existing_job_id);
    end if;

    perform cron.schedule(
        'cleanup-pair-codes-1h',
        '*/15 * * * *',
        $cmd$select public.cleanup_pair_codes_1h();$cmd$
    );
end;
$job$;
