import { api } from '../../api.js';
import { bookingKey } from '../booking-api.js';
import { escapeHtml as esc } from '../shared/ui.js';
import { bindRoomImageFallbacks } from '../shared/room-images.js';
import { renderRoomCard } from '../shared/room-card.js';
import { renderBookingSearchFields, roomAvailabilityPath } from '../shared/booking-search.js';

const money = value => Number(value).toLocaleString('vi-VN') + ' đ';
const hotelDate = value => {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(value);
  const fields = Object.fromEntries(parts.map(p => [p.type,p.value]));
  return `${fields.year}-${fields.month}-${fields.day}`;
};
const hotelClockMinutes = () => {
  const parts = new Intl.DateTimeFormat('en-GB', {timeZone:'Asia/Ho_Chi_Minh',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date());
  const fields = Object.fromEntries(parts.map(part=>[part.type,part.value]));
  return Number(fields.hour)*60+Number(fields.minute);
};
const formatMinutes = value => `${String(Math.floor(value/60)).padStart(2,'0')}:${String(value%60).padStart(2,'0')}`;
const timeMinutes = value => {
  if(!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) return NaN;
  const [hours,minutes]=value.split(':').map(Number);
  return hours*60+minutes;
};
const shiftDate = (value,days) => {
  const [year,month,day]=value.split('-').map(Number);
  return new Date(Date.UTC(year,month-1,day+days)).toISOString().slice(0,10);
};
const hourlyCheckInStart = () => Math.ceil(hotelClockMinutes()/15)*15;
const stayWindowIssue = (checkIn,checkOut,rentalType,inTime,outTime,today) => {
  if(checkIn!==today || !/^\d{4}-\d{2}-\d{2}$/.test(checkOut) || (rentalType==='HOURLY' ? checkOut<checkIn : checkOut<=checkIn)) {
    return 'Đặt tại quầy áp dụng nhận phòng trong hôm nay; ngày trả phòng phải sau ngày nhận phòng.';
  }
  if(!Number.isFinite(timeMinutes(inTime)) || !Number.isFinite(timeMinutes(outTime))) return 'Vui lòng chọn giờ nhận và giờ trả phòng hợp lệ.';
  const inMinutes=timeMinutes(inTime),outMinutes=timeMinutes(outTime);
  if(inMinutes<hotelClockMinutes()) return 'Giờ nhận phòng đã qua. Hãy chọn khung giờ trong tương lai.';
  if(rentalType==='DAILY') return inTime==='14:00' && outTime==='12:00' ? '' : 'Gói theo ngày cố định nhận phòng lúc 14:00 và trả phòng lúc 12:00.';
  if(rentalType==='OVERNIGHT') return checkOut===shiftDate(checkIn,1) && inTime==='18:00' && outTime==='08:00' ? '' : 'Gói qua đêm cố định 18:00 hôm trước đến 08:00 hôm sau (14 tiếng).';
  if(rentalType!=='HOURLY') return 'Loại hình lưu trú không hợp lệ.';
  const start=hourlyCheckInStart();
  if(start>23*60+45) return 'Hôm nay đã hết giờ nhận phòng theo giờ.';
  if(inMinutes<start) return `Giờ nhận phòng theo giờ phải từ ${formatMinutes(start)} trở đi.`;
  if(inMinutes%15!==0 || outMinutes%15!==0) return 'Gói theo giờ chỉ nhận giờ theo từng khoảng 15 phút.';
  const duration=(checkOut===checkIn ? outMinutes-inMinutes : 1440-inMinutes+outMinutes);
  if(checkOut!==checkIn && (inMinutes<18*60 || checkOut!==shiftDate(checkIn,1))) return 'Chỉ lượt nhận phòng từ 18:00 mới có thể trả vào ngày hôm sau.';
  if(duration<=0) return 'Giờ trả phòng phải sau giờ nhận phòng.';
  if(duration>360) return 'Lưu trú theo giờ tối đa 6 tiếng. Hãy chọn giờ trả phòng trong giới hạn này hoặc chọn gói phù hợp.';
  return '';
};
const roomAvailabilityLabel = room => {
  if(room.availability_status==='not_ready' && room.room_operational_status==='vacant_dirty') return ['Cần dọn phòng','not-ready'];
  if(room.availability_status==='not_ready' && room.room_operational_status==='occupied_dirty') return ['Có khách - cần dọn','not-ready'];
  return ({available:['Còn phòng','available'],reserved:['Đã đặt','reserved'],occupied:['Đang có khách','occupied'],maintenance:['Bảo trì','maintenance'],not_ready:['Chưa sẵn sàng','not-ready'],pricing_unconfigured:['Chưa cấu hình giá','not-ready']})[room.availability_status] || ['Chưa cập nhật','not-ready'];
};
const roomUnavailableReason = status => ({
  reserved:'Phòng đã có lịch đặt trùng khoảng thời gian này.',
  occupied:'Phòng đang được sử dụng trong khoảng thời gian đã chọn.',
  maintenance:'Phòng đang bảo trì và không thể đặt.',
  pricing_unconfigured:'Loại hình này chưa có đơn giá được cấu hình cho hạng phòng.',
  not_ready:'Phòng chưa sẵn sàng cho thời gian nhận phòng đã chọn.'
}[status] || 'Phòng không khả dụng.');

export async function renderFrontDesk(container, session = {}) {
  const allowed = ['STAFF','FRONT_DESK','ADMIN'].includes(String(session.role || '').toUpperCase());
  if (!allowed) { container.textContent = 'Chức năng tiếp nhận khách tại quầy dành cho nhân viên lễ tân.'; return; }
  const today = hotelDate(new Date());
  const tomorrow = new Date(today + 'T12:00:00+07:00'); tomorrow.setDate(tomorrow.getDate()+1);
  container.innerHTML = `<section class="front-desk-view">
    <div class="page-header"><div><h1>Đặt phòng tại quầy</h1><p>Tìm hồ sơ khách, kiểm tra phòng trống và hoàn tất đặt phòng cùng khách tại lễ tân.</p></div></div>
    <form id="desk-form" class="card card-border"><div class="card-body">
      <fieldset id="desk-fields" class="fieldset">
        <legend class="fieldset-legend">1. Thông tin khách hàng</legend>
        <p class="desk-customer-help">Nhập họ tên, số điện thoại hoặc CCCD / hộ chiếu để tìm hồ sơ. Khi trùng tên, nhập thêm số điện thoại hay giấy tờ để lọc chính xác hơn.</p>
        <fieldset class="fieldset desk-subfieldset">
          <legend class="fieldset-legend">Thông tin liên hệ và giấy tờ lưu trú</legend>
          <div class="desk-fields-grid">
            <label>Họ và tên<input id="desk-name" class="input input-bordered form-input" maxlength="100" required autocomplete="name"></label>
            <label>Số điện thoại<input id="desk-phone" class="input input-bordered form-input" type="tel" required maxlength="20" autocomplete="tel" placeholder="09xxxxxxxx"></label>
            <label>CCCD / Hộ chiếu<input id="desk-identity" class="input input-bordered form-input" required maxlength="20" placeholder="Giấy tờ của khách lưu trú"></label>
            <label>Email (không bắt buộc)<input id="desk-email" class="input input-bordered form-input" type="email" maxlength="100" autocomplete="email"></label>
          </div>
          <div id="desk-customer-suggestions" class="desk-customer-suggestions" role="status" aria-live="polite" hidden></div>
          <div id="desk-customer-selected" class="desk-customer-selected alert" role="status" hidden>
            <div><strong>Đã chọn hồ sơ khách hàng</strong><span id="desk-customer-selected-summary"></span><small class="desk-customer-lock-note">Thông tin tự điền từ hồ sơ đã được khóa. Có thể nhập giấy tờ còn trống để bổ sung hoặc đối chiếu; chọn “Đổi khách” để dùng hồ sơ khác.</small></div>
            <button id="desk-clear-customer" class="btn btn-sm" type="button">Đổi khách</button>
          </div>
        </fieldset>

        <section class="card card-border discovery-filter booking-search-panel desk-room-search" aria-labelledby="desk-room-search-title">
          <div class="desk-section-heading"><div><h2 id="desk-room-search-title">2. Tìm và chọn phòng</h2><p>Tìm phòng, chọn gói lưu trú và xem báo giá theo cùng cách khách đặt trên web.</p></div></div>
          ${renderBookingSearchFields({checkInId:'desk-in',checkInValue:today,checkInMin:today,checkInReadOnly:true,checkOutId:'desk-out',checkOutValue:hotelDate(tomorrow),checkOutMin:today,guestCountId:'desk-guests',guestCount:1,guestLabel:count=>`${count} khách`,voucher:{id:'desk-voucher-code',value:'',placeholder:'Mã ưu đãi (nếu có)',maxLength:50},searchButtonId:'desk-search',searchButtonLabel:'Tìm phòng trống'})}
          <div class="desk-stay-type-row">
            <label class="booking-search-field"><span>Gói lưu trú</span><select class="select select-sm booking-search-control" id="desk-rental-type"><option value="DAILY">Theo ngày</option><option value="OVERNIGHT">Qua đêm</option><option value="HOURLY">Theo giờ</option></select></label>
            <small>Chọn gói trước khi tìm phòng. Giờ nhận và trả sẽ theo gói hoặc có thể chỉnh trong tùy chọn nâng cao.</small>
          </div>
          <details class="collapse collapse-arrow booking-advanced-filters">
            <summary class="collapse-title">Tùy chọn lưu trú nâng cao</summary>
            <div class="collapse-content">
              <div class="booking-advanced-grid">
                <label class="booking-search-field"><span>Giờ nhận phòng</span><input class="input input-sm booking-search-control" id="desk-in-time" type="time" value="14:00" step="900" required></label>
                <label class="booking-search-field"><span>Giờ trả phòng</span><input class="input input-sm booking-search-control" id="desk-out-time" type="time" value="12:00" step="900" required></label>
                <label class="booking-search-field"><span>Hạng phòng</span><select class="select select-sm booking-search-control" id="desk-filter-type"><option value="">Tất cả hạng</option></select></label>
                <label class="booking-search-field"><span>Giá tối đa mỗi đêm</span><select class="select select-sm booking-search-control" id="desk-filter-price"><option value="0">Mọi mức giá</option><option value="1000000">Dưới 1 triệu</option><option value="2000000">Dưới 2 triệu</option><option value="5000000">Dưới 5 triệu</option></select></label>
              </div>
              <small id="desk-stay-time-hint" class="booking-time-help">Gói theo ngày cố định: nhận phòng 14:00, trả phòng 12:00 hôm sau.</small>
            </div>
          </details>
          <p class="desk-policy-note">Đặt tại quầy nhận phòng trong hôm nay. Gói ngày: 14:00–12:00; qua đêm: 18:00–08:00 hôm sau; theo giờ: tối đa 6 tiếng.</p>
          <div class="discovery-result-head"><strong id="desk-room-count" role="status" aria-live="polite">Nhập thông tin lưu trú rồi tìm phòng trống.</strong><span id="desk-price-unit">Giá theo mỗi đêm</span></div>
          <div id="desk-room-results" class="customer-room-grid" aria-live="polite"></div>
          <div id="desk-quote" class="desk-quote" role="status" aria-live="polite">Chọn một phòng để lấy báo giá từ máy chủ.</div>
        </section>
        <button id="desk-create" class="btn btn-primary" type="submit" disabled>Tạo đơn đặt phòng tại quầy</button>
      </fieldset>
    </div></form>
    <p id="desk-message" role="status" aria-live="polite"></p>
    <section id="desk-receipt" class="card card-border" hidden><div class="card-body">
      <h2 class="card-title">Đơn tiếp nhận tại quầy</h2><div id="desk-summary"></div>
      <fieldset id="desk-payment" class="fieldset"><legend class="fieldset-legend">Thu tiền tại quầy</legend>
        <label>Phương thức<select id="desk-method" class="select select-bordered form-input"><option value="cash">Tiền mặt</option><option value="POS">Thẻ qua máy POS</option></select></label>
        <label class="desk-payment-confirm"><input id="desk-collected" class="checkbox" type="checkbox"> Tôi đã nhận đủ số tiền trên bằng phương thức đã chọn.</label>
        <button id="desk-pay" class="btn btn-primary" type="button">Ghi nhận đã thu đủ tiền</button>
      </fieldset>
      <button id="desk-checkin" class="btn btn-primary" type="button" disabled>Nhận phòng cho khách</button>
      <p id="desk-checkin-hint" class="desk-policy-note" role="status" aria-live="polite"></p>
      <div class="desk-actions"><a class="btn" href="portal.html?view=bookings">Xem danh sách đặt phòng</a><button id="desk-next" class="btn" type="button">Tiếp nhận khách khác</button></div>
    </div></section>
  </section>`;
  const root = container.querySelector('.front-desk-view');
  const $ = id => root.querySelector('#'+id);
  let rooms = [], selected = null, quote = null, generation = 0, customerSearchGeneration = 0;
  let busy = false, receipt = null, selectedCustomer = null;
  let checkInTimer = null, bookedStay = null;
  const active = () => root.isConnected;
  const message = (text, error = false) => { if(active()) { $('desk-message').textContent=text; $('desk-message').classList.toggle('error',error); } };
  const syncCheckInAction = () => {
    clearTimeout(checkInTimer);checkInTimer=null;
    if(!receipt || !bookedStay || !active()) return;
    const checkInAt=Date.parse(`${bookedStay.date}T${bookedStay.time}:00+07:00`),remaining=checkInAt-Date.now();
    if(remaining>0) {
      $('desk-checkin').disabled=true;
      $('desk-checkin-hint').textContent=`Đã thu tiền. Có thể nhận phòng từ ${bookedStay.time} hôm nay.`;
      checkInTimer=setTimeout(syncCheckInAction,Math.min(remaining,2147483647));
    } else {
      $('desk-checkin').disabled=false;
      $('desk-checkin-hint').textContent='Đã đến giờ nhận phòng; có thể tiếp tục nhận phòng cho khách.';
    }
  };
  const rentalType = () => $('desk-rental-type').value;
  const checkInTime = () => $('desk-in-time').value;
  const checkOutTime = () => $('desk-out-time').value;
  const syncStayControls = () => {
    const type=rentalType(),checkOut=$('desk-out'),inTime=$('desk-in-time'),outTime=$('desk-out-time'),voucher=$('desk-voucher-code');
    inTime.disabled=type!=='HOURLY';
    outTime.disabled=type!=='HOURLY';
    checkOut.disabled=type==='OVERNIGHT';
    inTime.removeAttribute('min');inTime.removeAttribute('max');outTime.removeAttribute('min');outTime.removeAttribute('max');checkOut.removeAttribute('max');
    inTime.step=outTime.step=type==='HOURLY'?'900':'60';
    voucher.disabled=type!=='DAILY';
    voucher.title=voucher.disabled?'Mã ưu đãi chỉ áp dụng cho gói theo ngày.':'';
    if(type!=='DAILY') voucher.value='';
    if(type==='DAILY') {
      inTime.value='14:00';outTime.value='12:00';checkOut.min=shiftDate(today,1);
      if(checkOut.value<checkOut.min) checkOut.value=checkOut.min;
      $('desk-stay-time-hint').textContent='Gói theo ngày cố định: nhận phòng 14:00, trả phòng 12:00.';
    } else if(type==='OVERNIGHT') {
      inTime.value='18:00';outTime.value='08:00';checkOut.min=shiftDate(today,1);checkOut.max=shiftDate(today,1);checkOut.value=shiftDate(today,1);
      $('desk-stay-time-hint').textContent='Gói qua đêm cố định: nhận phòng hôm nay lúc 18:00, trả phòng ngày mai lúc 08:00 (14 tiếng).';
    } else {
      checkOut.min=today;
      const earliest=hourlyCheckInStart(),latestToday=23*60+45;
      if(earliest<=latestToday) {
        inTime.min=formatMinutes(earliest);
        if(timeMinutes(inTime.value)<earliest) inTime.value=formatMinutes(earliest);
      } else {
        inTime.min=formatMinutes(latestToday);inTime.max=formatMinutes(latestToday);inTime.value=formatMinutes(latestToday);
      }
      const start=timeMinutes(inTime.value),crossesDay=start>=18*60;
      const latestDate=crossesDay?shiftDate(today,1):today;
      checkOut.max=latestDate;
      if(checkOut.value>latestDate || checkOut.value<today) checkOut.value=today;
      if(checkOut.value===today) {
        const earliestOut=Math.floor(start/15)*15+15;
        const latestOut=Math.min(latestToday,Math.floor((start+360)/15)*15);
        outTime.min=formatMinutes(earliestOut);outTime.max=formatMinutes(latestOut);
        if(timeMinutes(outTime.value)<earliestOut) outTime.value=formatMinutes(earliestOut);
        if(timeMinutes(outTime.value)>latestOut) outTime.value=formatMinutes(latestOut);
      } else {
        const latestOut=Math.floor((start+360-1440)/15)*15;
        outTime.min='00:00';outTime.max=formatMinutes(latestOut);
        if(timeMinutes(outTime.value)>latestOut) outTime.value=formatMinutes(latestOut);
      }
      $('desk-stay-time-hint').textContent=earliest>latestToday
        ? 'Hôm nay đã hết giờ nhận phòng theo giờ. Chọn gói khác hoặc quay lại vào ngày khác.'
        : `Theo giờ tối đa 6 tiếng, chọn theo từng 15 phút. Hôm nay nhận phòng từ ${formatMinutes(earliest)}.`;
    }
    $('desk-price-unit').textContent=type==='DAILY'?'Giá theo mỗi đêm':'Báo giá theo loại hình đã chọn';
  };
  syncStayControls();
  const setCustomerReadOnly = value => {
    for(const [id,profileValue] of [
      ['desk-name',selectedCustomer?.fullName],
      ['desk-phone',selectedCustomer?.phone],
      ['desk-email',selectedCustomer?.email],
      ['desk-identity',$('desk-identity').value]
    ]) {
      const field=$(id);
      field.readOnly=Boolean(value && String(profileValue || '').trim());
      field.title=field.readOnly ? 'Thông tin lấy từ hồ sơ đã chọn. Chọn “Đổi khách” để dùng hồ sơ khác.' : '';
    }
    $('desk-identity').placeholder=selectedCustomer?.hasIdentityCard && !selectedCustomer.identityCard
      ? 'Hồ sơ đã lưu bảo mật · nhập giấy tờ để đối chiếu tại quầy'
      : 'Giấy tờ của khách lưu trú';
  };
  const renderRoomResults = () => {
    const guests=Number($('desk-guests').value),type=rentalType(),roomType=$('desk-filter-type').value,maxPrice=Number($('desk-filter-price').value);
    const visible=rooms.filter(room=>(!roomType || String(room.room_type_name||'')===roomType) && (!maxPrice || Number(room.price)<=maxPrice) && Number(room.capacity)>=guests);
    const issue=stayWindowIssue(today,$('desk-out').value,type,checkInTime(),checkOutTime(),today);
    const availableCount=visible.filter(room=>room.availability_status==='available' && Number(room.capacity)>=guests && !issue).length;
    $('desk-room-count').textContent=`${visible.length} phòng phù hợp · ${availableCount} phòng còn trống`;
    $('desk-room-results').innerHTML=visible.length ? visible.map(room=>{
      const [label,kind]=roomAvailabilityLabel(room);
      const capacity=Number(room.capacity);
      const canSelect=room.availability_status==='available' && Number.isInteger(capacity) && capacity>0 && guests<=capacity && !issue;
      const reason=room.availability_status==='available' && issue ? issue
        : room.availability_status==='available' ? (capacity ? `Phòng chỉ phục vụ tối đa ${capacity} khách.` : 'Chưa có thông tin sức chứa để xác nhận đặt phòng.')
        : roomUnavailableReason(room.availability_status);
      return renderRoomCard(room,{statusLabel:label,statusKind:kind,canSelect,reason,actionLabel:'Chọn phòng',actionAttribute:'data-select-room',selected:Number(selected?.id)===Number(room.id),rentalType:type,showDetails:false,money});
    }).join('') : rooms.length ? '<p class="customer-empty">Không có phòng phù hợp với bộ lọc đã chọn.</p>' : '<p class="customer-empty">Chưa có dữ liệu phòng. Hãy tìm phòng trống theo kỳ lưu trú.</p>';
    bindRoomImageFallbacks($('desk-room-results'));
  };
  function invalidate() {
    generation++; selected=null; quote=null; rooms=[];
    $('desk-room-results').replaceChildren();
    $('desk-room-count').textContent='Tìm lại phòng theo kỳ lưu trú và số khách đã chọn.';
    $('desk-create').disabled=true;
    $('desk-quote').textContent='Chọn một phòng để lấy báo giá từ máy chủ.';
  }
  const clearSelectedCustomer = ({clearFields=true}={}) => {
    selectedCustomer=null;
    $('desk-customer-selected').hidden=true;
    $('desk-customer-suggestions').hidden=true;
    $('desk-customer-suggestions').replaceChildren();
    if(clearFields) for(const id of ['desk-name','desk-phone','desk-identity','desk-email']) $(id).value='';
    setCustomerReadOnly(false);
  };
  const normalizeText=value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('vi-VN').trim();
  const normalizeDigits=value=>String(value||'').replace(/\D/g,'');
  const normalizePhone=value=>{
    const digits=normalizeDigits(value);
    return digits.startsWith('84') && digits.length>=11 ? '0'+digits.slice(2) : digits;
  };
  const searchNeedle=(id,value)=>{
    const text=String(value||'').trim();
    if(id==='desk-phone') return normalizeDigits(text).length>=6 ? normalizeDigits(text) : '';
    if(id==='desk-identity') return text.replace(/[^a-z0-9]/gi,'').length>=5 ? text.replace(/[^a-z0-9]/gi,'') : '';
    if(id==='desk-email') return text.includes('@') && text.length>=5 ? text.toLowerCase() : '';
    return text.length>=3 ? text : '';
  };
  const matchesInput=(customer,id,needle)=>{
    if(id==='desk-phone') return normalizePhone(customer.phone).includes(normalizePhone(needle));
    if(id==='desk-identity') {
      const normalizedNeedle=needle.toUpperCase().replace(/[^A-Z0-9]/g,'');
      const normalizedIdentity=String(customer.identityCard||'').toUpperCase().replace(/[^A-Z0-9]/g,'');
      return normalizedIdentity ? normalizedIdentity.includes(normalizedNeedle) : Boolean(customer.hasIdentityCard && customer.identityMatched);
    }
    if(id==='desk-email') return String(customer.email||'').toLowerCase().includes(needle.toLowerCase());
    return normalizeText(customer.fullName).includes(normalizeText(needle));
  };
  const collectCustomerSearchCriteria=()=>['desk-name','desk-phone','desk-identity','desk-email']
    .map(id=>({id,needle:searchNeedle(id,$(id).value)})).filter(item=>item.needle);
  let customerLookupTimer=null;
  const lookupCustomer=async(id,criteria,request)=>{
    try {
      const primary=criteria.find(item=>item.id==='desk-identity') || criteria.find(item=>item.id==='desk-phone') || criteria.find(item=>item.id==='desk-email') || criteria.find(item=>item.id===id) || criteria[0];
      const query=new URLSearchParams({search:primary.needle});
      const phoneCriteria=criteria.find(item=>item.id==='desk-phone');
      const identityCriteria=criteria.find(item=>item.id==='desk-identity');
      if(phoneCriteria) query.set('phone',normalizePhone($('desk-phone').value));
      if(identityCriteria) query.set('identity',$('desk-identity').value.trim());
      const result=await api.get('/customers?'+query.toString());
      if(!active() || request!==customerSearchGeneration || selectedCustomer) return;
      if(!Array.isArray(result)) throw Error('Dữ liệu hồ sơ khách hàng không hợp lệ.');
      const matches=result.filter(customer=>customer && Number.isInteger(Number(customer.id)) && criteria.every(item=>matchesInput(customer,item.id,item.needle))).slice(0,5);
      if(!matches.length) { $('desk-customer-suggestions').hidden=true;$('desk-customer-suggestions').replaceChildren();return; }
      $('desk-customer-suggestions').innerHTML=`<strong>Hồ sơ trùng thông tin · chọn đúng khách để điền nhanh</strong><div class="desk-customer-suggestion-list">${matches.map(customer=>`<button class="desk-customer-result card card-border" type="button" data-select-customer="${Number(customer.id)}" aria-label="Chọn hồ sơ khách hàng ${esc(customer.fullName || 'chưa cập nhật tên')}">
        <span class="desk-customer-result-body card-body">
          <span class="desk-customer-result-topline"><strong class="desk-customer-result-name">${esc(customer.fullName || 'Khách hàng chưa cập nhật tên')}</strong><span class="desk-customer-result-id">Hồ sơ #${Number(customer.id)}</span><span class="desk-customer-result-action">Chọn hồ sơ <span aria-hidden="true">→</span></span></span>
          <span class="desk-customer-result-details">
            <span class="desk-customer-result-detail"><span class="desk-customer-result-label">Số điện thoại</span><strong>${esc(customer.phone || 'Chưa có số điện thoại')}</strong></span>
            <span class="desk-customer-result-detail"><span class="desk-customer-result-label">CCCD / Hộ chiếu</span><strong>${esc(customer.identityCard || (customer.hasIdentityCard ? 'Đã lưu bảo mật' : 'Chưa cập nhật'))}</strong></span>
            <span class="desk-customer-result-detail"><span class="desk-customer-result-label">Email</span><strong>${esc(customer.email || 'Chưa cập nhật')}</strong></span>
          </span>
        </span>
      </button>`).join('')}</div>`;
      $('desk-customer-suggestions').hidden=false;
      $('desk-customer-suggestions').querySelectorAll('[data-select-customer]').forEach(button=>button.addEventListener('click',()=>{
        const customer=matches.find(item=>Number(item.id)===Number(button.dataset.selectCustomer));
        if(!customer) return;
        const suppliedIdentity=$('desk-identity').value;
        selectedCustomer=customer;
        $('desk-name').value=customer.fullName || '';
        $('desk-phone').value=customer.phone || '';
        $('desk-identity').value=customer.identityCard || suppliedIdentity;
        $('desk-email').value=customer.email || '';
        setCustomerReadOnly(true);
        $('desk-customer-selected-summary').textContent=`${customer.fullName || 'Khách hàng'} · ${customer.phone || 'Chưa có số điện thoại'}${customer.email?` · ${customer.email}`:''}`;
        $('desk-customer-selected').hidden=false;
        $('desk-customer-suggestions').hidden=true;
        $('desk-customer-suggestions').replaceChildren();
        message('Đã chọn hồ sơ khách. Kiểm tra giấy tờ lưu trú trước khi tạo đơn.');
      }));
    } catch(error) {
      if(active() && request===customerSearchGeneration && !selectedCustomer) {
        $('desk-customer-suggestions').innerHTML=`<p class="desk-inline-message error">Chưa thể kiểm tra hồ sơ khách hàng: ${esc(error.message || 'Vui lòng thử lại.')}</p>`;
        $('desk-customer-suggestions').hidden=false;
      }
    }
  };
  for(const id of ['desk-name','desk-phone','desk-identity','desk-email']) $(id).addEventListener('input',()=>{
    if(selectedCustomer) {
      message('Đang bổ sung hoặc đối chiếu thông tin cho hồ sơ khách đã chọn. Hồ sơ vẫn được giữ nguyên.');
      return;
    }
    clearTimeout(customerLookupTimer);
    const criteria=collectCustomerSearchCriteria();
    const request=++customerSearchGeneration;
    $('desk-customer-suggestions').hidden=true;
    $('desk-customer-suggestions').replaceChildren();
    if(!criteria.length) return;
    customerLookupTimer=setTimeout(()=>lookupCustomer(id,criteria,request),250);
  });
  $('desk-clear-customer').onclick=()=>{
    clearSelectedCustomer();
    message('Đã bỏ hồ sơ đang chọn. Nhập thông tin khách để tìm gợi ý hoặc tiếp tục với khách mới.');
    $('desk-name').focus();
  };
  for(const id of ['desk-out','desk-guests','desk-rental-type']) $(id).addEventListener('change',()=>{ syncStayControls(); invalidate(); renderRoomResults(); });
  for(const id of ['desk-in-time','desk-out-time']) $(id).addEventListener('input',()=>{ syncStayControls(); invalidate(); renderRoomResults(); });
  const updateRoomFilter=()=>{
    const type=$('desk-filter-type').value,price=Number($('desk-filter-price').value);
    const stillVisible=!selected || ((!type || String(selected.room_type_name||'')===type) && (!price || Number(selected.price)<=price));
    if(!stillVisible) { selected=null;quote=null;$('desk-create').disabled=true;$('desk-quote').textContent='Phòng đã chọn không còn khớp bộ lọc. Chọn phòng khác để lấy báo giá.'; }
    renderRoomResults();
  };
  $('desk-filter-type').addEventListener('change',updateRoomFilter);
  $('desk-filter-price').addEventListener('change',updateRoomFilter);
  let voucherTimer=null;
  $('desk-voucher-code').addEventListener('input',()=>{
    if(!selected || rentalType()!=='DAILY') return;
    clearTimeout(voucherTimer);
    generation++;
    selected=rooms.find(room=>Number(room.id)===Number(selected?.id)) || selected;
    quote=null;$('desk-create').disabled=true;$('desk-quote').textContent='Mã ưu đãi thay đổi; đang cập nhật báo giá…';
    const room=selected;
    voucherTimer=setTimeout(()=>loadQuote(room),350);
  });
  $('desk-search').onclick = async () => {
    if(busy || receipt) return;
    syncStayControls();
    invalidate();
    const current=generation, checkOut=$('desk-out').value, guests=Number($('desk-guests').value),type=rentalType(),inTime=checkInTime(),outTime=checkOutTime();
    const timeIssue=stayWindowIssue(today,checkOut,type,inTime,outTime,today);
    if(timeIssue) { $('desk-room-count').textContent='Thời gian lưu trú chưa hợp lệ'; message(timeIssue,true); return; }
    if(!Number.isInteger(guests) || guests<1 || guests>20) { message('Số khách phải là số nguyên từ 1 đến 20.',true); return; }
    busy=true; $('desk-search').disabled=true; message('Đang kiểm tra phòng trống…');
    try {
      const rows=await api.get(roomAvailabilityPath({checkIn:today,checkOut,rentalType:type,checkInTime:inTime,checkOutTime:outTime,includeStatus:true}),{signal:AbortSignal.timeout(15000)});
      if(!active() || current!==generation) return;
      if(!Array.isArray(rows)) throw Error('Không tải được danh sách phòng.');
      rooms=rows;
      const typeFilter=$('desk-filter-type'),previous=typeFilter.value,types=[...new Set(rooms.map(room=>room.room_type_name).filter(Boolean))];
      typeFilter.innerHTML='<option value="">Tất cả hạng</option>'+types.map(name=>`<option value="${esc(name)}">${esc(name)}</option>`).join('');
      typeFilter.value=types.includes(previous)?previous:'';
      renderRoomResults();
      message(rooms.length ? 'Tình trạng phòng được kiểm tra theo gói lưu trú đã chọn.' : 'Chưa có dữ liệu phòng.');
    } catch(error) { if(active() && current===generation) { $('desk-room-count').textContent='Không thể tải phòng'; message(error.name==='TimeoutError'?'Máy chủ phản hồi quá lâu khi tải phòng. Vui lòng thử tìm lại.':error.message,true); } }
    finally { busy=false;if(active()) $('desk-search').disabled=false; }
  };
  async function loadQuote(room) {
    if(!room || !active() || receipt) return;
    syncStayControls();
    const current=++generation,type=rentalType(),checkOut=$('desk-out').value,inTime=checkInTime(),outTime=checkOutTime(),voucherCode=$('desk-voucher-code').value.trim();
    const issue=stayWindowIssue(today,checkOut,type,inTime,outTime,today);
    if(issue) { selected=null;quote=null;renderRoomResults();$('desk-quote').textContent=issue;message(issue,true);return; }
    selected=room;quote=null;$('desk-create').disabled=true;$('desk-quote').textContent='Đang lấy báo giá…';renderRoomResults();
    try {
      const liveRooms=await api.get(roomAvailabilityPath({checkIn:today,checkOut,rentalType:type,checkInTime:inTime,checkOutTime:outTime,includeStatus:true}),{signal:AbortSignal.timeout(15000)});
      if(!active() || current!==generation) return;
      if(!Array.isArray(liveRooms)) throw Error('Không thể xác minh phòng còn trống. Tìm phòng lại.');
      const currentRoom=liveRooms.find(item=>Number(item.id)===Number(room.id) && item.availability_status==='available' && Number(item.capacity)>=Number($('desk-guests').value));
      if(!currentRoom) throw Error('Phòng vừa được cập nhật hoặc không còn trống. Tìm lại phòng trước khi đặt.');
      rooms=liveRooms;selected=currentRoom;renderRoomResults();
      const query=new URLSearchParams({room_type_id:String(currentRoom.room_type_id),check_in:today,check_out:checkOut,rental_type:type,check_in_time:inTime,check_out_time:outTime});
      const baseQuote=await api.get('/pricing/quote?'+query,{signal:AbortSignal.timeout(15000)});
      const discount=voucherCode ? await api.post('/promotions/validate',{voucher_code:voucherCode,room_type_id:Number(room.room_type_id),check_in_date:today,check_out_date:checkOut}) : null;
      if(!active() || current!==generation) return;
      const result=discount ? {...baseQuote,...discount} : baseQuote;
      if(!Number.isFinite(Number(result?.total_amount)) || Number(result.total_amount)<=0) throw Error('Báo giá chưa hợp lệ.');
      const effectiveType=result.rental_type || type,effectiveIn=result.check_in_date?.slice(0,10) || today,effectiveOut=result.check_out_date?.slice(0,10) || checkOut;
      const effectiveInTime=result.check_in_date?.slice(11,16) || inTime,effectiveOutTime=result.check_out_date?.slice(11,16) || outTime;
      if(effectiveType!==type || effectiveIn!==today || effectiveOut!==checkOut || effectiveInTime!==inTime || effectiveOutTime!==outTime) throw Error('Báo giá đã điều chỉnh thời gian lưu trú. Hãy chọn lại khung giờ rồi tìm phòng.');
      quote=result;
      $('desk-quote').innerHTML=`<strong>Phòng ${esc(room.room_number || room.id)} · ${type==='HOURLY'?'Theo giờ':type==='OVERNIGHT'?'Qua đêm':'Theo ngày'} · ${esc(today)} ${esc(inTime)}–${esc(checkOut)} ${esc(outTime)}</strong><div class="desk-quote-breakdown">${discount?`<span>Ưu đãi ${esc(voucherCode)} <strong>−${money(discount.discount_amount)}</strong></span>`:''}<span>Tiền phòng <strong>${money(result.room_subtotal)}</strong></span><span>Phí dịch vụ <strong>${money(result.service_charge)}</strong></span><span>Thuế <strong>${money(result.vat)}</strong></span><span class="desk-quote-total">Tổng cần thu <strong>${money(result.total_amount)}</strong></span></div>`;
      $('desk-create').disabled=false;
      message('Đã có báo giá. Kiểm tra thông tin khách rồi tạo đơn tại quầy.');
    } catch(error) {
      if(active() && current===generation) { $('desk-quote').textContent=error.message || 'Không thể lấy báo giá.';message(error.message || 'Không thể lấy báo giá.',true); }
    }
  }
  $('desk-room-results').addEventListener('click',async event=>{
    const button=event.target.closest('[data-select-room]');
    if(!button || busy || receipt) return;
    const room=rooms.find(item=>Number(item.id)===Number(button.dataset.selectRoom));
    if(!room) return;
    await loadQuote(room);
  });
  $('desk-form').onsubmit = async event => {
    event.preventDefault(); if(busy || receipt || !selected || !quote) return;
    if(!$('desk-form').reportValidity()) return;
    const type=rentalType(),inTime=checkInTime(),outTime=checkOutTime(),checkOut=$('desk-out').value,timeIssue=stayWindowIssue(today,checkOut,type,inTime,outTime,today);
    if(timeIssue) { message(timeIssue,true);return; }
    const voucherCode=$('desk-voucher-code').value.trim();
    if(voucherCode && type!=='DAILY') { message('Mã ưu đãi hiện chỉ áp dụng cho lưu trú theo ngày. Hãy xóa mã để tiếp tục.',true);return; }
    const payload={customer_name:$('desk-name').value.trim(),phone:$('desk-phone').value.trim(),identity_card:$('desk-identity').value.trim(),
      email:$('desk-email').value.trim() || undefined,room_id:Number(selected.id),room_version:Number(selected.version),rental_type:type,
      check_in_date:`${today} ${inTime}:00`,check_out_date:`${checkOut} ${outTime}:00`,guest_count:Number($('desk-guests').value),quoted_total:quote.total_amount,
      ...(voucherCode?{voucher_code:voucherCode}:{})};
    busy=true; $('desk-fields').disabled=true; message('Đang tạo đơn tại quầy…');
    try {
      const result=await api.post('/bookings/walk-in',payload,{headers:{'Idempotency-Key':bookingKey(payload)},retryOnNetworkError:false});
      if(!active()) return;
      if(!result?.booking_id || !result?.folio?.id) throw Error('Chưa nhận được kết quả đầy đủ. Thử lại cùng yêu cầu để kiểm tra đơn.');
      receipt=result;bookedStay={date:today,time:inTime}; $('desk-receipt').hidden=false;
      $('desk-summary').textContent=`${result.booking_code || '#'+result.booking_id} · Phòng ${result.room_number} · Cần thu ${money(result.folio.balance)} · Chờ thanh toán`;
      $('desk-checkin-hint').textContent=`Nhận phòng theo gói đã chọn từ ${inTime} hôm nay.`;
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
      $('desk-payment').hidden=true;syncCheckInAction();
      message($('desk-checkin').disabled?'Đã xác nhận thanh toán. Nhận phòng sẽ khả dụng khi đến giờ đã chọn.':'Đã xác nhận thanh toán. Có thể nhận phòng cho khách.');
    } catch(error) { message(error.message,true); }
    finally {busy=false;if(active()) {$('desk-payment').disabled=false;$('desk-next').disabled=false;} }
  };
  $('desk-checkin').onclick=async()=>{
    if(busy || !receipt) return;
    busy=true; $('desk-checkin').disabled=true;$('desk-next').disabled=true;
    try {
      await api.put(`/bookings/checkin/${receipt.booking_id}`,{}, {retryOnNetworkError:false});
      if(active()) { $('desk-checkin').hidden=true;$('desk-checkin-hint').textContent='';$('desk-summary').textContent=`${receipt.booking_code} · Phòng ${receipt.room_number} · Đã nhận phòng`;message('Đã nhận phòng thành công.'); }
    } catch(error) {message(error.message,true);if(active())syncCheckInAction();}
    finally {busy=false;if(active())$('desk-next').disabled=false;}
  };
  $('desk-next').onclick=()=>{ if(!busy) { clearTimeout(checkInTimer);clearTimeout(voucherTimer);renderFrontDesk(container,session); } };
}
