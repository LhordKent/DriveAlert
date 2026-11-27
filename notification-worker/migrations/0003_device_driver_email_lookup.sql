CREATE TABLE device_driver_email_matches (
  match_id TEXT PRIMARY KEY,
  owner_uid TEXT NOT NULL,
  device_id TEXT NOT NULL,
  target_uid TEXT NOT NULL,
  email_fingerprint TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX device_driver_email_matches_expiry ON device_driver_email_matches(expires_at);
