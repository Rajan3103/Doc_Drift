-- Per-repository settings
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS doc_paths TEXT;
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS watched_branches TEXT;
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS min_severity VARCHAR(255) DEFAULT 'LOW';
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS min_confidence DOUBLE PRECISION DEFAULT 0;
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS analyze_pull_requests BOOLEAN DEFAULT TRUE;
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS slack_webhook_url TEXT;
ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS alert_email VARCHAR(255);

-- Pull request checks
ALTER TABLE drift_report ADD COLUMN IF NOT EXISTS pull_request_number INTEGER;
ALTER TABLE drift_report ADD COLUMN IF NOT EXISTS head_branch VARCHAR(255);
ALTER TABLE webhook_event ADD COLUMN IF NOT EXISTS pull_request_number INTEGER;

-- Edited suggestions and rule-based findings
ALTER TABLE drift_suggestion ADD COLUMN IF NOT EXISTS original_suggested_text TEXT;
ALTER TABLE drift_suggestion ADD COLUMN IF NOT EXISTS source VARCHAR(255) DEFAULT 'AI';

CREATE INDEX IF NOT EXISTS ix_webhook_event_received_at ON webhook_event (received_at);
