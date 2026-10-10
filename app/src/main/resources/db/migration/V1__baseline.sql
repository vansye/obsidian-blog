CREATE TABLE post (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plate VARCHAR(32) NOT NULL,
  slug VARCHAR(128) DEFAULT NULL,
  vault_path VARCHAR(512) NOT NULL,
  section VARCHAR(16) NOT NULL,
  title VARCHAR(512) NOT NULL,
  description TEXT,
  body_md MEDIUMTEXT,
  body_html MEDIUMTEXT,
  tags JSON,
  status ENUM('published','unpublished') NOT NULL DEFAULT 'unpublished',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  published_at DATETIME,
  content_hash CHAR(64) NOT NULL,
  git_commit_sha VARCHAR(40) NOT NULL,
  word_count INT UNSIGNED NOT NULL DEFAULT 0,
  read_minutes INT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_plate (plate),
  UNIQUE KEY uk_slug (slug),
  UNIQUE KEY uk_vault_path (vault_path),
  KEY idx_status_created (status, created_at),
  KEY idx_section_status_created (section, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE asset (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  content_hash CHAR(64) NOT NULL,
  blob_id CHAR(40) NOT NULL,
  vault_path VARCHAR(512) NOT NULL,
  ext VARCHAR(16) NOT NULL,
  bytes INT UNSIGNED,
  mime VARCHAR(64),
  width INT UNSIGNED,
  height INT UNSIGNED,
  UNIQUE KEY uk_content_hash_ext (content_hash, ext),
  KEY idx_vault_path (vault_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE post_asset (
  post_id BIGINT UNSIGNED NOT NULL,
  asset_path VARCHAR(512) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  PRIMARY KEY (post_id, asset_path),
  KEY idx_content_hash (content_hash),
  CONSTRAINT fk_pa_post FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE sync_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  delivery_id VARCHAR(128) NOT NULL,
  from_sha VARCHAR(40),
  to_sha VARCHAR(40) NOT NULL,
  started_at DATETIME NOT NULL,
  duration_ms INT UNSIGNED NOT NULL DEFAULT 0,
  added INT UNSIGNED NOT NULL DEFAULT 0,
  updated INT UNSIGNED NOT NULL DEFAULT 0,
  unpublished INT UNSIGNED NOT NULL DEFAULT 0,
  skipped_json JSON,
  status ENUM('success','partial','failed') NOT NULL,
  error VARCHAR(1024),
  UNIQUE KEY uk_delivery_id (delivery_id),
  KEY idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plate_seq (
  name VARCHAR(32) NOT NULL PRIMARY KEY,
  value BIGINT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO plate_seq (name, value) VALUES ('plate:A',0),('plate:N',0),('plate:L',0),('plate:P',0);
