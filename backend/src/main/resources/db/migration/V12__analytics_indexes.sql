-- Phase 6C: executive dashboard queries filter history by action/status and time.
CREATE INDEX ix_workflow_history_action_created ON workflow_history (action, created_at);
CREATE INDEX ix_workflow_history_to_status_created ON workflow_history (to_status, created_at);
