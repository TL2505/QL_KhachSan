CREATE INDEX IF NOT EXISTS idx_revenue_dates ON bookings(check_in_date,check_out_date,status);
-- Use reconciled, closed-date snapshots; never infer historical physical inventory from today's rooms.
CREATE OR REPLACE VIEW v_daily_occupancy_history AS
SELECT audit_date AS business_date, occupied_rooms, available_rooms AS operational_rooms,
       CASE WHEN available_rooms>0 THEN occupied_rooms*100.0/available_rooms ELSE NULL END AS occupancy
FROM daily_business_reports WHERE audit_date<CURRENT_DATE;
CREATE OR REPLACE VIEW v_daily_revpar_history AS
SELECT audit_date AS business_date, room_revenue,
       CASE WHEN available_rooms>0 THEN room_revenue/available_rooms ELSE NULL END AS revpar
FROM daily_business_reports WHERE audit_date<CURRENT_DATE;
CREATE TABLE IF NOT EXISTS pricing_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id INT NOT NULL,
    rate_date DATE NOT NULL,
    multiplier DECIMAL(5,2) NOT NULL CHECK(multiplier BETWEEN 0.70 AND 2.50),
    version INT NOT NULL DEFAULT 1,
    approved_by INT NOT NULL,
    override_reason VARCHAR(255) NULL,
    approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_pricing_type_date(room_type_id,rate_date),
    CONSTRAINT fk_pricing_room_type FOREIGN KEY(room_type_id) REFERENCES room_types(id),
    CONSTRAINT chk_pricing_override CHECK(multiplier>=0.80 OR override_reason IS NOT NULL)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS pricing_rule_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id INT NOT NULL,
    rate_date DATE NOT NULL,
    old_multiplier DECIMAL(5,2) NULL,
    new_multiplier DECIMAL(5,2) NOT NULL,
    actor_id INT NOT NULL,
    new_version INT NOT NULL,
    override_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
