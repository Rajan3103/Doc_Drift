-- Status, severity and drift type are now stored as fixed values (Java enums).
-- Older rows held free text from the LLM, so normalise them to a valid value.

UPDATE drift_suggestion SET severity = UPPER(TRIM(severity)) WHERE severity IS NOT NULL;
UPDATE drift_suggestion SET severity = 'MEDIUM'
    WHERE severity IS NULL OR severity NOT IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW');

UPDATE drift_suggestion SET drift_type = UPPER(TRIM(drift_type)) WHERE drift_type IS NOT NULL;
UPDATE drift_suggestion SET drift_type = 'BEHAVIORAL_LOGIC_DRIFT'
    WHERE drift_type IS NULL OR drift_type NOT IN ('API_CONTRACT_BREAKING_CHANGE', 'SCHEMA_DRIFT',
        'API_SIGNATURE_MISMATCH', 'CONFIG_PROPERTY_CHANGED', 'CLI_USAGE_CHANGED', 'BEHAVIORAL_LOGIC_DRIFT',
        'SAMPLE_CODE_BROKEN');

UPDATE drift_suggestion SET status = 'PENDING'
    WHERE status IS NULL OR status NOT IN ('PENDING', 'APPROVED', 'REJECTED');

UPDATE repository_config SET status = 'HEALTHY'
    WHERE status IS NULL OR status NOT IN ('HEALTHY', 'DRIFTED', 'SCANNING', 'ERROR');

UPDATE webhook_event SET status = 'ERROR'
    WHERE status IS NULL OR status NOT IN ('PENDING', 'PROCESSED', 'ERROR');
