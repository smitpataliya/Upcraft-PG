-- Milestone 7 Audit Logs Verification Script (Read-only & Dev Reference)
-- Safe verification against migrated database:

SELECT COUNT(*) AS audit_log_rows FROM audit_logs;

SELECT id, organization_id, property_id, actor_email, actor_role, action, entity_type, entity_id, summary, trace_id, created_at
FROM audit_logs
ORDER BY created_at DESC
LIMIT 25;

