import { api } from '../../api.js';
import { bookingKey } from '../booking-api.js';
import { escapeHtml as esc } from '../shared/ui.js';

const money = value => Number(value).toLocaleString('vi-VN') + ' đ';
const hotelDate = value => {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(value);
  const fields = Object.fromEntries(parts.map(p => [p.type,p.value]));
  return `${fields.year}-${fields.month}-${fields.day}`;
};

export async function renderFrontDesk(container, session = {}) {
  const allowed = ['STAFF','FRONT_DESK','ADMIN'].includes(String(session.role || '').toUpperCase());
  if (!allowed) { container.textContent = 'Chức năng tiếp nhận khách tại quầy dành cho nhân viên lễ tân.'; return; }
  const today = hotelDate(new Date());
  const tomorrow = new Date(today + 'T12:00:00+07:00'); tomorrow.setDate(tomorrow.getDate()+1);
  container.innerHTML = `<section class="front-desk-view">
    <div class="page-header"><div><h1>Tiếp nhận khách vãng lai</h1><p>Tạo hồ sơ lưu trú, chọn phòng trống, thu tiền và nhận phòng tại quầy.</p></div></div>
    <form id="desk-form" class="card"><div class="card-body">
      <fieldset id="desk-fields" class="fieldset">
        <legend class="fieldset-legend">Thông tin khách và kỳ lưu trú</legend>
        <div class="desk-fields-grid">
          <label>Họ và tên<input id="desk-name" class="input form-input" maxlength="100" required autocomplete="name"></label>
          <label>Số điện thoại<input id="desk-phone" class="input form-input" type="tel" required maxlength="20" autocomplete="tel" placeholder="09xxxxxxxx"></label>
          <label>CCCD / Hộ chiếu<input id="desk-identity" class="input form-input" required maxlength="20" placeholder="Giấy tờ của khách lưu trú"></label>
          <label>Email (không bắt buộc)<input id="desk-email" class="input form-input" type="email" maxlength="100" autocomplete="email"></label>
          <label>Nhận phòng hôm nay<input id="desk-in" class="input form-input" type="date" value="${today}" readonly required></label>
          <label>Ngày trả phòng<input id="desk-out" class="input form-input" type="date" value="${hotelDate(tomorrow)}" min="${hotelDate(tomorrow)}" required></label>
          <label>Số khách<input id="desk-guests" class="input form-input" type="number" min="1" max="20" step="1" value="1" required></label>
        </div>
        <p>Giờ nhận phòng tiêu chuẩn 14:00, trả phòng 12:00. Chỉ chọn phòng trống sạch phù hợp với số khách.</p>
        <button id="desk-search" class="btn btn-secondary" type="button">Tìm phòng trống</button>
        <label for="desk-room">Phòng lưu trú</label>
        <select id="desk-room" class="select form-input" required disabled><option value="">Tìm phòng để chọn</option></select>
        <div id="desk-quote" class="desk-quote" role="status">Chọn phòng để lấy báo giá từ máy chủ.</div>
        <button id="desk-create" class="btn btn-primary" type="submit" disabled>Tạo đơn khách vãng lai</button>
      </fieldset>
    </div></form>
    <p id="desk-message" role="status" aria-live="polite"></p>
    <section id="desk-receipt" class="card" hidden><div class="card-body">
      <h2 class="card-title">Đơn tiếp nhận tại quầy</h2><div id="desk-summary"></div>
      <fieldset id="desk-payment" class="fieldset"><legend class="fieldset-legend">Thu tiền tại quầy</legend>
        <label>Phương thức<select id="desk-method" class="select form-input"><option value="cash">Tiền mặt</option><option value="POS">Thẻ qua máy POS</option></select></label>
        <label><input id="desk-collected" class="checkbox" type="checkbox"> Tôi đã nhận đủ số tiền trên bằng phương thức đã chọn.</label>
        <button id="desk-pay" class="btn btn-primary" type="button">Ghi nhận đã thu đủ tiền</button>
      </fieldset>
      <button id="desk-checkin" class="btn btn-primary" type="button" disabled>Nhận phòng cho khách</button>
      <div class="desk-actions"><a class="btn" href="portal.html?view=bookings">Xem danh sách đặt phòng</a><button id="desk-next" class="btn" type="button">Tiếp nhận khách khác</button></div>
    </div></section>
  </section>`;
  const root = container.querySelector('.front-desk-view');
  const $ = id => root.querySelector('#'+id);
  let rooms = [], selected = null, quote = null, generation = 0, busy = false, receipt = null;
  const active = () => root.isConnected;
  const message = (text, error = false) => { if(active()) { $('desk-message').textContent=text; $('desk-message').classList.toggle('error',error); } };
  function invalidate() {
    generation++; selected=null; quote=null; rooms=[];
    $('desk-room').innerHTML='<option value="">Tìm phòng lại để chọn</option>';
    $('desk-room').disabled=true; $('desk-create').disabled=true;
    $('desk-quote').textContent='Tìm lại phòng theo kỳ lưu trú và số khách đã chọn.';
  }
  for(const id of ['desk-out','desk-guests']) $(id).addEventListener('change',invalidate);
  $('desk-search').onclick = async () => {
    if(busy || receipt) return;
    invalidate();
    const current=generation, checkOut=$('desk-out').value, guests=Number($('desk-guests').value);
    if(!checkOut || checkOut<=today || !Number.isInteger(guests) || guests<1 || guests>20) { message('Chọn ngày trả phòng sau hôm nay và số khách hợp lệ.',true); return; }
    busy=true; $('desk-search').disabled=true; message('Đang kiểm tra phòng trống…');
    try {
      const rows=await api.get('/rooms/availability?'+new URLSearchParams({check_in:today,check_out:checkOut,include_status:'true'}));
      if(!active() || current!==generation) return;
      if(!Array.isArray(rows)) throw Error('Không tải được danh sách phòng.');
      rooms=rows.filter(r=>r.availability_status==='available' && Number(r.capacity)>=guests);
      $('desk-room').innerHTML='<option value="">Chọn phòng</option>'+rooms.map(r=>`<option value="${Number(r.id)}">Phòng ${esc(r.room_number || r.id)} · ${esc(r.room_type_name)} · tối đa ${Number(r.capacity)} khách</option>`).join('');
      $('desk-room').disabled=!rooms.length;
      message(rooms.length ? `${rooms.length} phòng trống phù hợp. Chọn phòng để xem báo giá.` : 'Không có phòng trống sạch phù hợp. Vui lòng kiểm tra lại lịch hoặc buồng phòng.');
    } catch(error) { message(error.message,true); }
    finally { busy=false;if(active()) $('desk-search').disabled=false; }
  };
  $('desk-room').onchange = async () => {
    const current=++generation; quote=null; selected=rooms.find(r=>String(r.id)===$('desk-room').value);
    $('desk-create').disabled=true;
    if(!selected) return;
    $('desk-quote').textContent='Đang lấy báo giá…';
    try {
      const result=await api.get('/pricing/quote?'+new URLSearchParams({room_type_id:selected.room_type_id,check_in:today,check_out:$('desk-out').value}));
      if(!active() || current!==generation) return;
      if(!Number.isFinite(Number(result?.total_amount)) || Number(result.total_amount)<=0) throw Error('Báo giá chưa hợp lệ.');
      quote=result;
      $('desk-quote').textContent=`Tiền phòng: ${money(result.room_subtotal)} · Phí dịch vụ: ${money(result.service_charge)} · Thuế: ${money(result.vat)} · Tổng cần thu: ${money(result.total_amount)}`;
      $('desk-create').disabled=false;
    } catch(error) { message(error.message,true); }
  };
  $('desk-form').onsubmit = async event => {
    event.preventDefault(); if(busy || receipt || !selected || !quote) return;
    if(!$('desk-form').reportValidity()) return;
    const payload={customer_name:$('desk-name').value.trim(),phone:$('desk-phone').value.trim(),identity_card:$('desk-identity').value.trim(),
      email:$('desk-email').value.trim() || undefined,room_id:Number(selected.id),room_version:Number(selected.version),
      check_in_date:today+' 14:00:00',check_out_date:$('desk-out').value+' 12:00:00',guest_count:Number($('desk-guests').value),quoted_total:quote.total_amount};
    busy=true; $('desk-fields').disabled=true; message('Đang tạo đơn tại quầy…');
    try {
      const result=await api.post('/bookings/walk-in',payload,{headers:{'Idempotency-Key':bookingKey(payload)},retryOnNetworkError:false});
      if(!active()) return;
      if(!result?.booking_id || !result?.folio?.id) throw Error('Chưa nhận được kết quả đầy đủ. Thử lại cùng yêu cầu để kiểm tra đơn.');
      receipt=result; $('desk-receipt').hidden=false;
      $('desk-summary').textContent=`${result.booking_code || '#'+result.booking_id} · Phòng ${result.room_number} · Cần thu ${money(result.folio.balance)} · Chờ thanh toán`;
      message('Đã tạo đơn. Ghi nhận thu tiền trước khi nhận phòng.');
    } catch(error) { message(error.message,true); }
    finally { busy=false;if(active()) $('desk-fields').disabled=!!receipt; }
  };
  $('desk-pay').onclick=async()=>{
    if(busy || !receipt) return;
    if(!$('desk-collected').checked) { message('Xác nhận đã nhận đủ tiền trước khi ghi nhận thanh toán.',true);return; }
    busy=true; $('desk-payment').disabled=true; $('desk-next').disabled=true;
    try {
      const folios=await api.get(`/folios/booking/${receipt.booking_id}`);
      const folio=Array.isArray(folios) && folios.find(f=>Number(f.id)===Number(receipt.folio.id));
      if(!folio || !Number.isFinite(Number(folio.balance)) || Number(folio.balance)<0) throw Error('Không xác minh được số dư folio.');
      if(Number(folio.balance)>0 && Number(folio.balance)!==Number(receipt.folio.balance)) {
        receipt.folio.balance=folio.balance;
        $('desk-summary').textContent=`${receipt.booking_code} · Phòng ${receipt.room_number} · Cần thu ${money(folio.balance)}`;
        $('desk-collected').checked=false;
        throw Error('Số dư vừa thay đổi. Kiểm tra và xác nhận lại số tiền thực nhận.');
      }
      if(Number(folio.balance)>0) {
        const payload={amount:Number(folio.balance),payment_method:$('desk-method').value,checkout:false};
        await api.post(`/folios/${folio.id}/pay`,payload,{headers:{'Idempotency-Key':bookingKey({folioId:folio.id,...payload})},retryOnNetworkError:false});
      }
      if(!active()) return;
      $('desk-payment').hidden=true; $('desk-checkin').disabled=false;
      message('Đã xác nhận thanh toán. Có thể nhận phòng cho khách.');
    } catch(error) { message(error.message,true); }
    finally {busy=false;if(active()) {$('desk-payment').disabled=false;$('desk-next').disabled=false;} }
  };
  $('desk-checkin').onclick=async()=>{
    if(busy || !receipt) return;
    busy=true; $('desk-checkin').disabled=true;$('desk-next').disabled=true;
    try {
      await api.put(`/bookings/checkin/${receipt.booking_id}`,{}, {retryOnNetworkError:false});
      if(active()) { $('desk-checkin').hidden=true;$('desk-summary').textContent=`${receipt.booking_code} · Phòng ${receipt.room_number} · Đã nhận phòng`;message('Đã nhận phòng thành công.'); }
    } catch(error) {message(error.message,true);if(active())$('desk-checkin').disabled=false;}
    finally {busy=false;if(active())$('desk-next').disabled=false;}
  };
  $('desk-next').onclick=()=>{ if(!busy) renderFrontDesk(container,session); };
}
