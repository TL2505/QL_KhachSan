-- Retain the in-house booking state while marking a Web counter-payment stay
-- that passed its 30-minute checkout grace without being settled.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS checkout_overdue_at DATETIME(6) NULL AFTER payment_choice;
