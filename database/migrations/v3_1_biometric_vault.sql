-- MariaDB 11. Additive, repeatable DDL only. Does not rewrite or erase existing identities.
-- Backfill runs separately through IdentityVaultMigration with operator-supplied keys.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS identity_hash BINARY(32) NULL;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL;
ALTER TABLE customers MODIFY identity_card VARCHAR(20) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_identity_hash ON customers(identity_hash);
CREATE TABLE IF NOT EXISTS guest_identity_vault (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL UNIQUE,
    encrypted_id_number VARBINARY(512) NOT NULL,
    encrypted_raw_payload MEDIUMBLOB NULL,
    face_embedding_blob BLOB NULL,
    captured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vault_customer FOREIGN KEY(customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB;
-- Every encrypted field is a versioned envelope (version byte + 12-byte IV + ciphertext/tag).
-- A single shared IV column would reuse a nonce across encryption operations; it is intentionally absent.
