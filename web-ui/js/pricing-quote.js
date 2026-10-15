import {api} from './booking-api.js';
export async function pricingQuote(roomType, checkIn, checkOut) {
  const query = new URLSearchParams({room_type_id:roomType,check_in:checkIn,check_out:checkOut});
  return api(`/pricing/quote?${query}`);
}
export function occupancyLabel(quote) {
  const values = [...new Set((quote.nightly_rates ?? []).map(night => String(night.multiplier)))];
  return values.length ? `Hệ số giá theo công suất: ${values.join('x · ')}x` : '';
}
