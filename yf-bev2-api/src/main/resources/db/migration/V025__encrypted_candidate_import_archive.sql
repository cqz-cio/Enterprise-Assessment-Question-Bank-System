-- Only authenticated ciphertext is stored; the existing assignment credential hashes are unchanged.
CREATE TABLE el_candidate_import_task (
    id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    committed TINYINT NOT NULL DEFAULT 0,
    expires_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_candidate_import_owner (owner_id,created_at),
    KEY idx_candidate_import_file (owner_id,file_hash,expires_at),
    KEY idx_candidate_import_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
