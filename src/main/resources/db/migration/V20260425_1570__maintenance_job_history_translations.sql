-- Phase 5g i18n: per-locale translation map for maintenance job history messages.
-- Most rows are system-generated; the new column lets the FE pre-render in any
-- locale without a per-request translation roundtrip.
ALTER TABLE maintenance_job_histories
    ADD COLUMN IF NOT EXISTS message_translations TEXT;

COMMENT ON COLUMN maintenance_job_histories.message_translations IS
    'JSON map of locale -> translated history message. Reserved keys: _source, _auto.';
