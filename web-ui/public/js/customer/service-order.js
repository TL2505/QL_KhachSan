export function resolveServiceSchedule(value, now = new Date()) {
  if (value === 'now') return null;
  if (value === '30m' || value === '1h') {
    return new Date(now.getTime() + (value === '30m' ? 30 : 60) * 60000).toISOString();
  }
  if (value !== '19:30' && value !== '08:00') throw new Error('Thời gian phục vụ không hợp lệ.');
  const hotelDate = new Date(now.getTime() + 7 * 3600000 + (value === '08:00' ? 86400000 : 0));
  const date = hotelDate.toISOString().slice(0, 10);
  const scheduled = `${date}T${value}:00+07:00`;
  if (new Date(scheduled) <= now) throw new Error('Giờ phục vụ đã qua. Vui lòng chọn giờ khác.');
  return scheduled;
}
