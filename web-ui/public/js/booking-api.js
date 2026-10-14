const API = '/api/v1';

export async function api(path, {method = 'GET', body, key, token, signal} = {}) {
  const headers = {Accept: 'application/json'};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (key) headers['Idempotency-Key'] = key;
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(API + path, {method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal});
  let payload;
  try { payload = await response.json(); } catch { throw new Error('Không thể nhận phản hồi. Vui lòng liên hệ lễ tân.'); }
  if (!response.ok) {
    const error = new Error(response.status === 409 ? 'Phòng vừa được đặt hoặc dữ liệu đã thay đổi. Vui lòng tìm lại phòng.' : response.status === 400 ? 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.' : 'Chưa thể hoàn tất yêu cầu. Vui lòng liên hệ lễ tân.');
    error.status = response.status; throw error;
  }
  return payload.data ?? payload;
}

export function bookingKey(payload) {
  const fingerprint = JSON.stringify(payload);
  let saved;
  try { saved = JSON.parse(sessionStorage.getItem('aurelia-booking-intent')); } catch { /* Recover invalid local state. */ }
  if (saved?.fingerprint === fingerprint && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(saved.key)) return saved.key;
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
  const key = `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
  sessionStorage.setItem('aurelia-booking-intent', JSON.stringify({fingerprint, key}));
  return key;
}
