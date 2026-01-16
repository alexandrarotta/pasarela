CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS merchants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  api_key_hash TEXT NOT NULL UNIQUE,
  config_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_intents (
  id TEXT PRIMARY KEY,
  merchant_id TEXT NOT NULL,
  amount_minor INTEGER NOT NULL,
  currency TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL,
  provider TEXT NOT NULL,
  provider_ref TEXT,
  idempotency_key TEXT,
  routing_decision_id TEXT,
  routing_reason_code TEXT,
  root_payment_intent_id TEXT,
  attempt_number INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX IF NOT EXISTS idx_payment_intents_merchant_created_at ON payment_intents(merchant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_intents_status ON payment_intents(status);

CREATE TABLE IF NOT EXISTS payment_intent_private_data (
  payment_intent_id TEXT PRIMARY KEY,
  checkout_config_enc TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS routing_decisions (
  id TEXT PRIMARY KEY,
  payment_intent_id TEXT NOT NULL,
  merchant_id TEXT NOT NULL,
  chosen_provider TEXT NOT NULL,
  candidate_scores_json TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(id) ON DELETE CASCADE,
  FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX IF NOT EXISTS idx_routing_decisions_created_at ON routing_decisions(created_at);
CREATE INDEX IF NOT EXISTS idx_routing_decisions_provider ON routing_decisions(chosen_provider);

CREATE TABLE IF NOT EXISTS provider_health_snapshot (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL UNIQUE,
  window_start INTEGER,
  window_end INTEGER,
  success_rate REAL NOT NULL DEFAULT 0,
  error_rate REAL NOT NULL DEFAULT 0,
  p95_latency_ms INTEGER NOT NULL DEFAULT 0,
  last_failure_at INTEGER,
  circuit_state TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_events (
  id TEXT PRIMARY KEY,
  payment_intent_id TEXT,
  provider TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  sanitized_payload_json TEXT,
  created_at INTEGER NOT NULL,
  FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_payment_events_created_at ON payment_events(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_events_provider_type_created_at ON payment_events(provider, event_type, created_at);

CREATE TABLE IF NOT EXISTS idempotency_records (
  id TEXT PRIMARY KEY,
  merchant_id TEXT NOT NULL,
  endpoint TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  payment_intent_id TEXT NOT NULL,
  request_hash TEXT,
  created_at INTEGER NOT NULL,
  UNIQUE (merchant_id, endpoint, idempotency_key),
  FOREIGN KEY (merchant_id) REFERENCES merchants(id),
  FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(id)
);
