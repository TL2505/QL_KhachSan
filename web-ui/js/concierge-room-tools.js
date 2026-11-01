import { apiFetch } from './api.js';

const within = async (path, signal) => apiFetch(path, { method: 'GET', signal });

function fold(value) {
  return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
}
const amenityKey = value => fold(value).replace(/[^\p{L}\p{N}]/gu, '');

function amenitiesOf(value) {
  return Array.isArray(value) ? value.map(item => String(item).trim()).filter(Boolean)
    : String(value ?? '').split(',').map(item => item.trim()).filter(Boolean);
}

/** Read-only concierge tool. It can call only availability and pricing preview APIs. */
export async function searchConciergeRooms(criteria, { signal } = {}) {
  const stay = {
    check_in: criteria.check_in,
    check_out: criteria.check_out,
    rental_type: criteria.rental_type,
    check_in_time: criteria.check_in_time,
    check_out_time: criteria.check_out_time
  };
  const controller = signal ? null : new AbortController();
  const timeout = controller ? setTimeout(() => controller.abort(), 15000) : null;
  try {
    const apiSignal = signal || controller.signal;
    const availabilityQuery = new URLSearchParams({ ...stay, include_status: 'true' });
    const rooms = await within(`/rooms/availability?${availabilityQuery}`, apiSignal);
    if (!Array.isArray(rooms)) throw new Error('Availability API returned invalid data');

    const wanted = (criteria.amenities ?? []).map(amenityKey).filter(Boolean);
    const candidates = rooms.filter(room => {
      if (room?.availability_status !== 'available') return false;
      const capacity = Number(room.capacity);
      if (!Number.isInteger(capacity) || capacity < Number(criteria.guest_count)) return false;
      const offered = amenitiesOf(room.amenities).map(amenityKey);
      return wanted.every(item => offered.some(value => value.includes(item)));
    });
    const byRoomType = new Map();
    for (const room of candidates) {
      if (!byRoomType.has(String(room.room_type_id))) byRoomType.set(String(room.room_type_id), room);
    }
    const selectedTypes = [...byRoomType.values()].slice(0, 5);
    const priced = await Promise.all(selectedTypes.map(async room => {
      const query = new URLSearchParams({ room_type_id: String(room.room_type_id), ...stay });
      const quote = await within(`/pricing/quote?${query}`, apiSignal);
      if (!quote || !Number.isFinite(Number(quote.total_amount)) || Number(quote.total_amount) <= 0) {
        throw new Error('Pricing API returned invalid data');
      }
      return { room, quote };
    }));
    const options = priced.sort((a, b) => Number(a.quote.total_amount) - Number(b.quote.total_amount)
      || String(a.room.room_type_name).localeCompare(String(b.room.room_type_name), 'vi'))
      .slice(0, 3).map(({ room, quote }) => ({
        room_id: room.id,
        room_version: room.version,
        room_type_id: room.room_type_id,
        room_type_name: room.room_type_name,
        room_number: room.room_number,
        capacity: Number(room.capacity),
        amenities: amenitiesOf(room.amenities),
        quote
      }));
    return { checked_at: new Date().toISOString(), options };
  } finally {
    if (timeout) clearTimeout(timeout);
  }
}

export function conciergeRoomSearchIntent(message) {
  const text = fold(message);
  return /\b(tim|kiem|dat)\s+(?:(?:mot|a)\s+)?(phong|room)\b|\bphong\s+(trong|con)\b|\bcon\s+phong\b|\bco\s+phong\s+trong\b|\b(bao gia|gia)\s+(phong|room)\b|\bphong\b.{0,30}\b(gia|bao gia|bao nhieu)\b|\b(room|rooms)\s+(availability|available|price|rates)\b|\b(find|book)\s+(?:me\s+)?(a\s+)?room\b|\bhow much\b.{0,30}\broom\b/.test(text);
}
