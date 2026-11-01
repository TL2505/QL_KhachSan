-- Store the guest's selected payment route. QR expiry is derived from bookings.created_at
-- and the existing payment_links.expires_at; no separate expiry rows are created.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_choice VARCHAR(16) NULL;
