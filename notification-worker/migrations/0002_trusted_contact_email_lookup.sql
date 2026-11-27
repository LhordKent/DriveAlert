CREATE TABLE email_lookup_limits (
  driver_uid TEXT PRIMARY KEY,
  window_started_at INTEGER NOT NULL,
  searches INTEGER NOT NULL
);
CREATE INDEX email_lookup_limits_window ON email_lookup_limits(window_started_at);

CREATE TABLE email_lookup_matches (
  match_id TEXT PRIMARY KEY,
  driver_uid TEXT NOT NULL,
  trusted_uid TEXT NOT NULL,
  email_fingerprint TEXT NOT NULL,
  connection_version TEXT,
  expires_at INTEGER NOT NULL
);
CREATE INDEX email_lookup_matches_expiry ON email_lookup_matches(expires_at);
