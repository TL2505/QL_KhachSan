-- Web reservations that choose payment at the counter are confirmed when booked.
-- They may check in normally; the stay is held at checkout until staff collection.
UPDATE bookings
SET status='confirmed'
WHERE customer_type='Web'
  AND payment_choice='counter'
  AND status='pending'
  AND check_out_date>CURRENT_TIMESTAMP(6);
