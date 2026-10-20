-- Columns added for webhook de-duplication, error reporting and pull request links.
-- IF NOT EXISTS because databases run with ddl-auto=update may already have them.

ALTER TABLE webhook_event ADD COLUMN IF NOT EXISTS delivery_id VARCHAR(255);
ALTER TABLE webhook_event ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE drift_suggestion ADD COLUMN IF NOT EXISTS pr_url VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_webhook_event_delivery_id ON webhook_event (delivery_id);
CREATE INDEX IF NOT EXISTS ix_webhook_event_status ON webhook_event (status);
CREATE INDEX IF NOT EXISTS ix_repository_config_status ON repository_config (status);
CREATE INDEX IF NOT EXISTS ix_drift_report_repo_commit ON drift_report (repository_name, commit_sha);
CREATE INDEX IF NOT EXISTS ix_drift_report_analyzed_at ON drift_report (analyzed_at);
CREATE INDEX IF NOT EXISTS ix_drift_suggestion_report_id ON drift_suggestion (report_id);
CREATE INDEX IF NOT EXISTS ix_drift_suggestion_status ON drift_suggestion (status);
