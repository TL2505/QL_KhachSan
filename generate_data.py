import random
from datetime import datetime, timedelta

def random_date(start, end):
    return start + timedelta(seconds=random.randint(0, int((end - start).total_seconds())))

start_date = datetime(2025, 1, 1)
end_date = datetime(2026, 6, 1)

with open('seed_data.sql', 'w', encoding='utf-8') as f:
    f.write('-- GENERATED BOOKINGS\n')
    f.write('INSERT INTO bookings (customer_id, room_id, check_in_date, check_out_date, total_price, status, customer_type) VALUES\n')
    
    bookings = []
    invoices = []
    payments = []
    
    for i in range(1, 121):
        cust_id = random.randint(1, 13)
        room_id = random.randint(1, 15)
        room_price = 500000 if room_id <= 5 else (1000000 if room_id <= 10 else 1500000)
        
        c_in = random_date(start_date, end_date)
        days = random.randint(1, 5)
        c_out = c_in + timedelta(days=days)
        
        status = random.choice(['confirmed', 'checked_in', 'checked_out', 'checked_out', 'checked_out', 'checked_out'])
        c_type = random.choice(['OTA', 'Walk-in', 'Corporate', 'Walk-in', 'Walk-in'])
        
        total_room = room_price * days
        total_service = random.choice([0, 50000, 100000, 150000, 200000]) if status != 'cancelled' else 0
        final_total = total_room + total_service
        
        row = f"({cust_id}, {room_id}, '{c_in.strftime('%Y-%m-%d %H:00:00')}', '{c_out.strftime('%Y-%m-%d 12:00:00')}', {total_room}, '{status}', '{c_type}')"
        if i < 120:
            row += ','
        else:
            row += ';'
        f.write(row + '\n')
        
        if status in ['checked_out', 'paid']:
            invoices.append((i, total_room, final_total, 'paid'))
            payments.append((len(invoices), final_total, random.choice(['cash', 'credit_card', 'bank_transfer'])))
            
    f.write('\n-- GENERATED INVOICES\n')
    f.write('INSERT INTO invoices (booking_id, total_room_fee, total_service_fee, final_total, status) VALUES\n')
    for idx, inv in enumerate(invoices):
        row = f"({inv[0]}, {inv[1]}, {inv[2] - inv[1]}, {inv[2]}, '{inv[3]}')"
        if idx < len(invoices) - 1:
            row += ','
        else:
            row += ';'
        f.write(row + '\n')
        
    f.write('\n-- GENERATED PAYMENTS\n')
    f.write('INSERT INTO payments (invoice_id, amount, payment_method) VALUES\n')
    for idx, pay in enumerate(payments):
        row = f"({pay[0]}, {pay[1]}, '{pay[2]}')"
        if idx < len(payments) - 1:
            row += ','
        else:
            row += ';'
        f.write(row + '\n')
