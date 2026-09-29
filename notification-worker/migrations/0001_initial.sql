CREATE TABLE installations (
  installation_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  fcm_token TEXT NOT NULL UNIQUE,
  updated_at INTEGER NOT NULL
);
CREATE INDEX installations_user_updated ON installations(user_id, updated_at DESC);

CREATE TABLE dispatch_groups (
  group_id TEXT PRIMARY KEY,
  driver_uid TEXT NOT NULL,
  chunk_count INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'RECEIVING',
  created_at INTEGER NOT NULL,
  completed_at INTEGER
);

CREATE TABLE dispatch_chunks (
  group_id TEXT NOT NULL,
  chunk_index INTEGER NOT NULL,
  batch_id TEXT NOT NULL,
  record_ids_json TEXT NOT NULL,
  PRIMARY KEY(group_id, chunk_index),
  UNIQUE(group_id, batch_id),
  FOREIGN KEY(group_id) REFERENCES dispatch_groups(group_id) ON DELETE CASCADE
);

CREATE TABLE dispatch_records (
  group_id TEXT NOT NULL,
  record_id TEXT NOT NULL,
  record_type TEXT NOT NULL,
  period_started_at INTEGER NOT NULL,
  uploaded_at INTEGER NOT NULL,
  PRIMARY KEY(group_id, record_id),
  FOREIGN KEY(group_id) REFERENCES dispatch_groups(group_id) ON DELETE CASCADE
);

CREATE TABLE device_deliveries (
  group_id TEXT NOT NULL,
  trusted_uid TEXT NOT NULL,
  installation_id TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(group_id, trusted_uid, installation_id),
  FOREIGN KEY(group_id) REFERENCES dispatch_groups(group_id) ON DELETE CASCADE
);
CREATE INDEX device_deliveries_pending ON device_deliveries(group_id, status);
