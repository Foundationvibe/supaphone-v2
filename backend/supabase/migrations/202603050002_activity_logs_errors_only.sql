create or replace function public.keep_error_activity_logs_only()
returns trigger
language plpgsql
as $$
begin
    if new.level in ('error-client', 'error-server') then
        return new;
    end if;
    return null;
end;
$$;

drop trigger if exists trg_activity_logs_errors_only on public.activity_logs;
create trigger trg_activity_logs_errors_only
before insert on public.activity_logs
for each row
execute function public.keep_error_activity_logs_only();
