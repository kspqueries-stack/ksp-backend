-- Report History table for tracking generated reports stored in S3
CREATE TABLE IF NOT EXISTS report_history (
    id BIGSERIAL PRIMARY KEY,
    report_name VARCHAR(255) NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    s3_key VARCHAR(500) NOT NULL UNIQUE,
    file_size BIGINT,
    content_type VARCHAR(100),
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    generated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_report_history_type ON report_history(report_type);
CREATE INDEX IF NOT EXISTS idx_report_history_date ON report_history(generated_at);
CREATE INDEX IF NOT EXISTS idx_report_history_type_date ON report_history(report_type, generated_at);
