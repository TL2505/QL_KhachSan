-- Editable room catalogue, persistent media metadata and reviewed landing content.
ALTER TABLE room_types
  ADD COLUMN IF NOT EXISTS image_alt VARCHAR(255) NULL,
  ADD COLUMN IF NOT EXISTS display_order INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS is_visible TINYINT(1) NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS media_assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  storage_key VARCHAR(80) NOT NULL UNIQUE,
  public_url VARCHAR(255) NOT NULL,
  mime_type VARCHAR(40) NOT NULL,
  size_bytes INT UNSIGNED NOT NULL,
  width INT UNSIGNED NOT NULL,
  height INT UNSIGNED NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  uploaded_by INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_media_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_type_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_type_id INT NOT NULL,
  asset_id BIGINT NULL,
  external_url VARCHAR(255) NULL,
  alt_text VARCHAR(255) NULL,
  display_order INT NOT NULL DEFAULT 0,
  is_primary TINYINT(1) NOT NULL DEFAULT 0,
  is_visible TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_room_type_image_asset (room_type_id, asset_id),
  INDEX idx_room_type_images_visible (room_type_id, is_visible, display_order),
  CONSTRAINT fk_room_type_image_type FOREIGN KEY (room_type_id) REFERENCES room_types(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_type_image_asset FOREIGN KEY (asset_id) REFERENCES media_assets(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS landing_content (
  content_key VARCHAR(64) PRIMARY KEY,
  classification ENUM('dynamic','brand','legal') NOT NULL,
  draft_json LONGTEXT NULL,
  published_json LONGTEXT NULL,
  workflow_status ENUM('draft','pending_review','published','rejected') NOT NULL DEFAULT 'draft',
  submitted_by INT NULL,
  submitted_at DATETIME NULL,
  approved_by INT NULL,
  approved_at DATETIME NULL,
  review_note VARCHAR(1000) NULL,
  updated_by INT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_landing_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_landing_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_landing_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO room_type_images (room_type_id, external_url, alt_text, is_primary, is_visible)
SELECT rt.id, rt.image_url, COALESCE(rt.image_alt, rt.name), 1, 1
FROM room_types rt
WHERE rt.image_url IS NOT NULL AND rt.image_url <> ''
  AND NOT EXISTS (SELECT 1 FROM room_type_images i WHERE i.room_type_id=rt.id AND i.external_url=rt.image_url);
