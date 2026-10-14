import {api, bookingKey} from './booking-api.js';
import {createOtpFlow} from './guest-otp.js';
import {paymentFlow} from './payment-qr.js';
import {pricingQuote,occupancyLabel} from './pricing-quote.js';
import {voucherFlow} from './voucher.js';

const $ = id => document.getElementById(id);
const money = value => new Intl.NumberFormat('vi-VN', {style: 'currency', currency: 'VND'}).format(value);
const otp = createOtpFlow();
const payment = paymentFlow();
let selected, rooms = [], generation = 0, quoteGeneration = 0, searchAbort, searching = false, booking = false;
let currentQuote;
const voucher=voucherFlow(()=>{
  if(!selected || !currentQuote)return null;
  const {checkIn,checkOut}=dates();return {room_type_id:selected.room_type_id,check_in_date:checkIn,check_out_date:checkOut};
},preview=>{
  if(!currentQuote)return;
  $('quote').replaceChildren();
  if(preview){const old=document.createElement('s');old.textContent=money(currentQuote.total_amount);$('quote').append(old,document.createElement('br'));}
  $('quote').append(`${money(preview?.total_amount ?? currentQuote.total_amount)} · tổng giá dự kiến`);
});
function message(text, error = false) { $('message').textContent = text; $('message').classList.toggle('error', error); }
function dates() {
  const checkIn = $('check-in').value, checkOut = $('check-out').value;
  if (!checkIn || !checkOut || checkOut <= checkIn || checkIn < $('check-in').min) throw new Error('Vui lòng chọn ngày trả phòng sau ngày nhận phòng.');
  return {checkIn, checkOut};
}
function invalidate() { payment.stop(); selected = undefined; currentQuote=undefined;voucher.reset();quoteGeneration++; $('reservation').hidden = true; $('receipt').hidden = true; }
function render() {
  $('rooms').replaceChildren();
  const category = $('room-category').value;
  const shown = rooms.filter(room => !category || String(room.room_type_id) === category);
  $('room-count').textContent = `${shown.length} phòng trống`;
  for (const room of shown) {
    const card = document.createElement('article'); card.className = 'room-card';
    const label = document.createElement('p'); label.className = 'eyebrow'; label.textContent = 'LƯU TRÚ TẠI AURELIA';
    const title = document.createElement('h3'); title.textContent = room.room_type_name ?? 'Phòng khách sạn';
    const description = document.createElement('p'); description.textContent = 'Giá và phòng được kiểm tra lại khi đặt.';
    const price = document.createElement('p'); price.className = 'room-price';
    price.textContent = Number.isFinite(Number(room.price)) ? `${money(room.price)} / đêm · giá cơ bản` : 'Xem báo giá';
    const button = document.createElement('button'); button.type = 'button'; button.textContent = 'Xem giá & chọn phòng';
    button.addEventListener('click', () => choose(room)); card.append(label, title, description, price, button); $('rooms').append(card);
  }
}
async function choose(room) {
  if (booking) return;
  const current = ++quoteGeneration; selected = room; $('receipt').hidden = true; $('reservation').hidden = false;
  currentQuote=undefined;voucher.reset();$('occupancy-price').textContent='';
  $('selected-room').textContent = room.room_type_name ?? 'Phòng khách sạn'; $('quote').textContent = 'Đang lấy báo giá…';
  $('reserve-button').disabled = true;
  try {
    const {checkIn, checkOut} = dates();
    const quote = await pricingQuote(room.room_type_id,checkIn,checkOut);
    if (current !== quoteGeneration) return;
    const total = quote.total_amount ?? quote.total_quote ?? quote.total;
    if (!Number.isFinite(Number(total)) || Number(total) <= 0) throw new Error('Chưa có báo giá hợp lệ. Vui lòng liên hệ lễ tân.');
    $('quote').textContent = `${money(total)} · tổng giá dự kiến`; $('quote').className = 'quote-total';
    currentQuote=quote;$('occupancy-price').textContent=occupancyLabel(quote);
    $('reserve-button').disabled = false;
  } catch (error) { if (current === quoteGeneration) { $('quote').textContent = 'Chưa thể lấy giá. Vui lòng liên hệ lễ tân.'; message(error.message, true); } }
  $('reservation').scrollIntoView({behavior: 'smooth', block: 'nearest'});
}
$('search-form').addEventListener('submit', async event => {
  event.preventDefault(); if (booking) return;
  let range;
  try { range = dates(); } catch (error) { message(error.message, true); return; }
  const current = ++generation; invalidate(); searchAbort?.abort(); searchAbort = new AbortController();
  searching = true; $('search-button').disabled = true; message('Đang tìm phòng…'); $('rooms').replaceChildren();
  try {
    const query = new URLSearchParams({check_in: range.checkIn, check_out: range.checkOut});
    const result = await api(`/rooms/availability?${query}`, {signal: searchAbort.signal});
    if (current !== generation) return;
    rooms = Array.isArray(result) ? result : result.rooms;
    if (!Array.isArray(rooms)) throw new Error('Chưa thể xác minh phòng trống. Vui lòng liên hệ lễ tân.');
    const categories = new Map(rooms.map(room => [String(room.room_type_id), room.room_type_name ?? 'Phòng khách sạn']));
    const previous = $('room-category').value;
    $('room-category').replaceChildren(new Option('Tất cả hạng phòng', ''));
    for (const [id, name] of categories) $('room-category').append(new Option(name, id));
    $('room-category').value = categories.has(previous) ? previous : '';
    render(); message(rooms.length ? 'Chọn phòng để xem tổng giá lưu trú.' : 'Không còn phòng phù hợp. Vui lòng chọn ngày khác.');
  } catch (error) { if (error.name !== 'AbortError' && current === generation) { rooms = []; message(error.message, true); } }
  finally { if (current === generation) { searching = false; $('search-button').disabled = false; } }
});
for (const id of ['check-in', 'check-out']) $(id).addEventListener('change', () => {
  generation++; searchAbort?.abort(); searching = false; $('search-button').disabled = false; rooms = []; render(); invalidate();
});
$('room-category').addEventListener('change', () => { invalidate(); render(); });
$('guest-form').addEventListener('submit', async event => {
  event.preventDefault(); if (booking || searching || !selected) return;
  booking = true; $('reserve-button').disabled = true;
  const room = selected;
  const fields = ['check-in', 'check-out', 'room-category', 'guest-name', 'guest-phone', 'guest-email', 'search-button'];
  fields.forEach(id => { $(id).disabled = true; });
  voucher.freeze(true);
  try {
    const {checkIn, checkOut} = dates();
    const contact = {phone: $('guest-phone').value.trim(), email: $('guest-email').value.trim() || undefined};
    const token = await otp(contact);
    const payload = {customer_name: $('guest-name').value.trim(), ...contact, room_id: room.id, room_version: room.version,
      check_in_date: `${checkIn} 14:00:00`, check_out_date: `${checkOut} 12:00:00`};
    if(voucher.code())payload.voucher_code=voucher.code();
    if (!payload.customer_name) throw new Error('Vui lòng nhập họ và tên.');
    const result = await api('/bookings/public', {method: 'POST', body: payload, key: bookingKey(payload), token});
    $('booking-reference').textContent = result.booking_code ?? `Mã đặt phòng: ${result.booking_id}`;
    $('booking-details').textContent = `Phòng ${result.room_number} · ${checkIn} → ${checkOut}`;
    $('booking-payment').textContent = result.folio ? `Số dư cần thanh toán: ${money(result.folio.balance)}` : '';
    $('receipt').hidden = false; $('reservation').hidden = true; selected = undefined; rooms = []; render(); message('Đặt phòng thành công.');
    $('receipt').scrollIntoView({behavior: 'smooth'});
    if(result.folio) {
      $('receipt').hidden=true;
      payment.start(result.folio,token,() => {$('booking-payment').textContent='Thanh toán đã được xác nhận.';$('receipt').hidden=false;$('receipt').scrollIntoView({behavior:'smooth'});});
    }
  } catch (error) {
    message(error.message, true);
    if (error.status === 409) { invalidate(); rooms = []; render(); }
  } finally { booking = false; $('reserve-button').disabled = !selected; fields.forEach(id => { $(id).disabled = false; });voucher.freeze(false); }
});
const now = new Date();
const localDate = date => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
$('check-in').min = localDate(now); $('check-in').value = localDate(now);
now.setDate(now.getDate() + 1); $('check-out').min = localDate(now); $('check-out').value = localDate(now);
