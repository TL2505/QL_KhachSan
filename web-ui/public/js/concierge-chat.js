import { apiFetch, getAuthSession, getStoredCustomerToken } from './api.js';
import { conciergeRoomSearchIntent, searchConciergeRooms } from './concierge-room-tools.js';

// Kept in memory: closing the panel preserves the conversation; reloading clears it.
export function initConciergeChat() {
  const trigger = document.getElementById('quick-chat-btn');
  if (!trigger || document.getElementById('aurelia-concierge')) return;
  const host = document.createElement('div');
  host.id = 'aurelia-concierge';
  document.body.append(host);
  // Scope the UI library so it cannot change the existing hotel page.
  const root = host.attachShadow({ mode: 'open' });
  root.innerHTML = `
    <link rel="stylesheet" href="${new URL('../css/concierge-ui.css', import.meta.url)}">
    <link rel="stylesheet" href="${new URL('../css/concierge-chat.css?v=6', import.meta.url)}">
    <section class="card" data-theme="dark" role="dialog" aria-modal="false" aria-labelledby="concierge-title" hidden>
      <header>
        <span class="concierge-emblem" aria-hidden="true">✦</span>
        <div class="concierge-heading"><span class="eyebrow">AURELIA PALACE</span><h2 id="concierge-title">Quản gia AI</h2><p>Đồng hành cùng kỳ nghỉ của bạn</p></div>
        <button class="btn btn-ghost btn-circle" id="close" type="button" aria-label="Đóng trò chuyện">×</button>
      </header>
      <div class="conversation" role="log" aria-label="Cuộc trò chuyện" aria-live="polite" aria-relevant="additions text"></div>
      <div class="suggestions" aria-label="Câu hỏi gợi ý">
        <button class="btn btn-sm" type="button" data-prompt="Giờ nhận phòng và trả phòng là khi nào?">Nhận / trả phòng</button>
        <button class="btn btn-sm" type="button" data-prompt="Khách sạn có những tiện nghi nào?">Tiện nghi</button>
        <button class="btn btn-sm" type="button" data-prompt="Tôi muốn tìm hiểu các dịch vụ của khách sạn.">Dịch vụ</button>
        <button class="btn btn-sm" type="button" data-prompt="Tôi muốn tìm phòng.">Tìm phòng</button>
      </div>
      <div class="feedback" role="status"></div>
      <div class="actions">
        <button class="btn btn-sm" id="login" type="button" hidden>Đăng nhập để trò chuyện</button>
        <button class="btn btn-sm" id="retry" type="button" hidden>Thử lại câu hỏi</button>
        <button class="btn btn-sm" id="escalate" type="button" hidden>Chuyển tới lễ tân</button>
      </div>
      <form aria-label="Gửi câu hỏi cho Quản gia AI">
        <input class="input" aria-label="Câu hỏi của bạn" placeholder="Bạn cần Aurelia hỗ trợ gì?" maxlength="2000" autocomplete="off" required>
        <button class="btn" type="submit" aria-label="Gửi câu hỏi"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg></button>
      </form>
      <footer>Thông tin AI cần được xác nhận khi đặt dịch vụ. <a href="tel:+842438889999">Gọi lễ tân ↗</a></footer>
    </section>`;
  const $ = selector => root.querySelector(selector);
  const panel = $('section');
  const messages = $('.conversation');
  const input = $('input');
  const feedback = $('.feedback');
  const login = $('#login');
  const retry = $('#retry');
  const escalate = $('#escalate');
  let busy = false;
  let lastQuestion = '';
  let identity;
  let sessionId;
  const token = () => getAuthSession()?.token || getStoredCustomerToken();
  const bubble = (text, sent = false) => {
    const row = document.createElement('div');
    row.className = `chat ${sent ? 'chat-end' : 'chat-start'}`;
    const content = document.createElement('div');
    content.className = 'chat-bubble';
    content.textContent = text;
    row.append(content);
    messages.append(row);
    messages.scrollTop = messages.scrollHeight;
    return content;
  };
  const welcome = () => bubble('Xin chào! Tôi là trợ lý AI của Aurelia. Tôi có thể hỗ trợ thông tin về tiện nghi, dịch vụ và kỳ nghỉ của bạn. Bạn muốn tìm hiểu điều gì?');
  const syncIdentity = () => {
    const current = token() || null;
    if (identity === current) return;
    if (identity) input.value = '';
    identity = current;
    sessionId = crypto.randomUUID();
    messages.replaceChildren();
    welcome();
    lastQuestion = '';
    feedback.textContent = '';
    retry.hidden = escalate.hidden = true;
    login.hidden = !!current;
  };
  const close = () => {
    panel.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    trigger.focus();
  };
  trigger.setAttribute('aria-haspopup', 'dialog');
  trigger.setAttribute('aria-expanded', 'false');
  trigger.addEventListener('click', () => {
    if (!panel.hidden) return close();
    syncIdentity();
    panel.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    input.focus();
  });
  $('#close').addEventListener('click', close);
  root.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.stopPropagation(); close(); }
  });
  login.addEventListener('click', () => {
    close();
    if (typeof window.openLoginModal === 'function') window.openLoginModal();
    else window.location.assign('/portal.html');
  });
  window.addEventListener('hotel:session-expired', syncIdentity);
  window.addEventListener('storage', () => { if (!busy) syncIdentity(); });

  const setBusy = value => {
    busy = value;
    $('form button').disabled = value;
    root.querySelectorAll('[data-prompt], #retry, #escalate').forEach(button => { button.disabled = value; });
    root.querySelectorAll('.room-search-form button').forEach(button => { button.disabled = value; });
    messages.setAttribute('aria-busy', String(value));
  };
  const request = async (path, body) => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 55000);
    try {
      return await apiFetch(path, { method: 'POST', body: JSON.stringify(body), signal: controller.signal });
    } finally { clearTimeout(timeout); }
  };
  const fail = error => {
    login.hidden = error.status !== 401 && !!token();
    retry.hidden = [400, 401, 403].includes(error.status);
    escalate.hidden = [400, 401, 403, 409, 429].includes(error.status);
    return error.status === 401 ? 'Vui lòng đăng nhập để trò chuyện với Quản gia AI.'
      : error.status === 429 ? 'Bạn đã gửi nhiều câu hỏi. Vui lòng đợi một phút rồi thử lại.'
      : error.status === 403 ? 'Tài khoản hiện tại chưa thể sử dụng Quản gia AI. Vui lòng liên hệ lễ tân.'
      : error.status === 400 ? 'Câu hỏi chưa hợp lệ. Vui lòng diễn đạt lại để trợ lý có thể hỗ trợ.'
      : error.status === 409 ? 'Câu hỏi trước đang được xử lý. Vui lòng đợi câu trả lời rồi thử lại.'
      : error.status === 422 ? error.message || 'Trợ lý AI chưa sẵn sàng. Vui lòng liên hệ lễ tân.'
      : 'Chưa thể kết nối với trợ lý AI. Bạn có thể thử lại hoặc liên hệ lễ tân.';
  };
  const noPreference = value => /^(khong yeu cau|khong co yeu cau|khong|bat ky|khong uu tien|no preference|none|any)$/i
    .test(String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').trim().replace(/[.!?]+$/, ''));
  const knownRoomDetails = message => {
    const normalized = String(message).normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLowerCase();
    const dates = [...message.matchAll(/\b(20\d{2}-\d{2}-\d{2})\b/g)].map(match => match[1]);
    if (!dates.length) {
      for (const match of message.matchAll(/\b(\d{1,2})[/.](\d{1,2})[/.](20\d{2})\b/g)) {
        dates.push(`${match[3]}-${match[2].padStart(2, '0')}-${match[1].padStart(2, '0')}`);
      }
    }
    const times = [...message.matchAll(/\b([01]?\d|2[0-3]):([0-5]\d)\b/g)].map(match => `${match[1].padStart(2, '0')}:${match[2]}`);
    const guests = normalized.match(/\b(\d{1,2})\s*(khach|nguoi|guests?)\b/)?.[1];
    const rentalType = /qua dem|overnight/.test(normalized) ? 'OVERNIGHT'
      : /theo gio|hang gio|hourly/.test(normalized) ? 'HOURLY'
        : /theo ngay|hang ngay|daily/.test(normalized) ? 'DAILY' : '';
    const amenityLabel = /(?:tien nghi|amenities)\s*:\s*([^.;!?]+)/.exec(normalized)?.[1]?.trim();
    return {
      check_in: dates[0] || '', check_out: dates[1] || '', check_in_time: times[0] || '', check_out_time: times[1] || '',
      guest_count: guests || '', rental_type: rentalType,
      amenities: amenityLabel ? (amenityLabel.split(',').map(item => item.trim()).filter(Boolean).join(', ')) : ''
    };
  };
  const searchForm = prefill => {
    const content = bubble('Nếu tin nhắn của bạn có ngày, giờ hoặc số khách rõ ràng, các mục đó có thể đã được điền sẵn. Hãy kiểm tra, bổ sung mục còn thiếu cùng yêu cầu tiện nghi rồi bấm tra cứu. Nếu không có yêu cầu tiện nghi cụ thể, ghi “Không yêu cầu”.');
    const form = document.createElement('form');
    form.className = 'room-search-form';
    const field = (labelText, type, name, options = {}) => {
      const label = document.createElement('label');
      label.textContent = labelText;
      let control;
      if (type === 'select') {
        control = document.createElement('select');
        control.className = 'select select-sm';
        for (const [value, title] of options.choices) {
          const option = document.createElement('option'); option.value = value; option.textContent = title; control.append(option);
        }
      } else {
        control = document.createElement('input'); control.type = type;
        control.className = 'input input-sm';
        for (const [key, value] of Object.entries(options.attrs || {})) control.setAttribute(key, value);
      }
      if (prefill?.[name]) control.value = prefill[name];
      control.name = name; control.required = true; label.append(control); form.append(label); return control;
    };
    const todayParts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    const today = Object.fromEntries(todayParts.map(part => [part.type, part.value]));
    const minDate = `${today.year}-${today.month}-${today.day}`;
    field('Ngày nhận phòng', 'date', 'check_in', { attrs: { min: minDate } });
    field('Giờ nhận phòng', 'time', 'check_in_time');
    field('Ngày trả phòng', 'date', 'check_out', { attrs: { min: minDate } });
    field('Giờ trả phòng', 'time', 'check_out_time');
    field('Loại lưu trú', 'select', 'rental_type', { choices: [['', 'Chọn loại lưu trú'], ['DAILY', 'Theo ngày'], ['OVERNIGHT', 'Qua đêm'], ['HOURLY', 'Theo giờ']] });
    field('Số khách', 'number', 'guest_count', { attrs: { min: '1', max: '20', step: '1', inputmode: 'numeric' } });
    field('Tiện nghi mong muốn', 'text', 'amenities', { attrs: { maxlength: '200', placeholder: 'Wi-Fi, bồn tắm; hoặc “Không yêu cầu”' } });
    const submit = document.createElement('button');
    submit.className = 'btn btn-sm'; submit.type = 'submit'; submit.textContent = 'Tra cứu phòng và giá'; form.append(submit);
    content.append(form);
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (busy || !form.reportValidity()) return;
      const values = Object.fromEntries(new FormData(form).entries());
      const guestCount = Number(values.guest_count);
      if (!Number.isInteger(guestCount) || guestCount < 1 || guestCount > 20) {
        form.elements.guest_count.setCustomValidity('Số khách phải từ 1 đến 20.');
        form.elements.guest_count.reportValidity(); form.elements.guest_count.setCustomValidity(''); return;
      }
      const amenityText = values.amenities.trim();
      const amenities = noPreference(amenityText) ? [] : amenityText.split(',').map(value => value.trim()).filter(Boolean);
      if (amenities.length > 10 || amenities.some(value => value.length > 50)) {
        form.elements.amenities.setCustomValidity('Nhập tối đa 10 tiện nghi, mỗi tiện nghi không quá 50 ký tự.');
        form.elements.amenities.reportValidity(); form.elements.amenities.setCustomValidity(''); return;
      }
      const criteria = { ...values, guest_count: guestCount, amenities };
      const requestIdentity = identity;
      const resultBubble = bubble('Đang tra cứu tình trạng phòng và báo giá từ máy chủ…');
      setBusy(true);
      try {
        const result = await searchConciergeRooms(criteria);
        if (token() !== requestIdentity) { syncIdentity(); return; }
        await request('/ai/concierge/tool-log', { session_id: sessionId, outcome: result.options.length ? 'success' : 'no_results' });
        if (token() !== requestIdentity) { syncIdentity(); return; }
        form.remove();
        renderRoomOptions(resultBubble, result, criteria);
      } catch (error) {
        if (token() !== requestIdentity) { syncIdentity(); return; }
        try { await request('/ai/concierge/tool-log', { session_id: sessionId, outcome: 'unavailable' }); } catch { /* The failed tool call is still not presented as a result. */ }
        resultBubble.textContent = error.status === 400
          ? 'Khoảng ngày, giờ hoặc loại lưu trú chưa hợp lệ. Hãy sửa thông tin rồi tra cứu lại; tôi chưa thể đưa ra gợi ý.'
          : 'Chưa thể lấy đủ dữ liệu tình trạng phòng và báo giá từ máy chủ. Vui lòng thử tra cứu lại; tôi chưa thể đưa ra gợi ý.';
        form.querySelector('button[type="submit"]').textContent = 'Thử tra cứu lại';
      } finally {
        setBusy(false);
        messages.scrollTop = messages.scrollHeight;
        if (!panel.hidden) input.focus();
      }
    });
  };
  const renderRoomOptions = (target, result, criteria) => {
    const formatMoney = value => Number.isFinite(Number(value))
      ? `${new Intl.NumberFormat('vi-VN').format(Number(value))} ₫` : 'Không có dữ liệu giá';
    const checkedAt = new Date(result.checked_at);
    const checkedLabel = Number.isNaN(checkedAt.getTime()) ? result.checked_at
      : new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' }).format(checkedAt) + ' (giờ Việt Nam)';
    target.replaceChildren();
    const intro = document.createElement('p');
    intro.textContent = result.options.length
      ? `Máy chủ đã trả về ${result.options.length} lựa chọn phù hợp. Tra cứu lúc ${checkedLabel}.`
      : `Hiện không có lựa chọn khớp dữ liệu tình trạng phòng và tiện nghi đã chọn. Tra cứu lúc ${checkedLabel}.`;
    target.append(intro);
    for (const option of result.options) {
      const card = document.createElement('article'); card.className = 'room-search-option card card-border';
      const title = document.createElement('strong'); title.textContent = `${option.room_type_name || 'Hạng phòng'} · Phòng ${option.room_number || option.room_id}`; card.append(title);
      const facts = document.createElement('p'); facts.textContent = `Sức chứa ${option.capacity} khách · Tiện nghi: ${option.amenities.join(', ') || 'chưa có dữ liệu'}`; card.append(facts);
      const quote = option.quote;
      const stay = document.createElement('small');
      const rentalLabel = ({ DAILY: 'theo ngày', OVERNIGHT: 'qua đêm', HOURLY: 'theo giờ' })[quote.rental_type] || 'loại lưu trú đã chọn';
      stay.textContent = `Kỳ lưu trú ${rentalLabel}: ${quote.check_in_date || `${criteria.check_in} ${criteria.check_in_time}`} → ${quote.check_out_date || `${criteria.check_out} ${criteria.check_out_time}`} · ${criteria.guest_count} khách`;
      card.append(stay);
      const charges = document.createElement('p');
      charges.textContent = `Tiền phòng ${formatMoney(quote.room_subtotal)} · Phí dịch vụ ${formatMoney(quote.service_charge)} · Thuế ${formatMoney(quote.vat)} · Tổng ${formatMoney(quote.total_amount)}`;
      card.append(charges);
      if (Array.isArray(quote.nightly_rates) && quote.nightly_rates.length) {
        const nights = document.createElement('small');
        nights.textContent = quote.nightly_rates.map(item => `${item.date}: ${formatMoney(item.amount)}`).join(' · '); card.append(nights);
      }
      const conditions = document.createElement('small');
      conditions.textContent = `Tra cứu API lúc ${checkedLabel}. Điều kiện: báo giá áp dụng cho kỳ lưu trú đã chọn; sức chứa đáp ứng số khách; chưa giữ phòng. Tình trạng và giá sẽ được kiểm tra lại ở bước xác nhận.`;
      card.append(conditions);
      const action = document.createElement('button'); action.type = 'button'; action.className = 'btn btn-sm'; action.textContent = 'Mở bước xác nhận';
      action.addEventListener('click', () => {
        try {
          sessionStorage.setItem('aurelia-discovery-booking-intent', JSON.stringify({
            roomId: option.room_id, roomVersion: option.room_version, quotedTotal: Number(quote.total_amount),
            checkedAt: result.checked_at, fromConcierge: true,
            checkIn: quote.check_in_date?.slice(0, 10) || criteria.check_in,
            checkOut: quote.check_out_date?.slice(0, 10) || criteria.check_out,
            checkInTime: quote.check_in_date?.slice(11, 16) || criteria.check_in_time,
            checkOutTime: quote.check_out_date?.slice(11, 16) || criteria.check_out_time,
            guests: criteria.guest_count, rentalType: quote.rental_type || criteria.rental_type,
            type: option.room_type_name, price: '0', voucherCode: '', amenities: criteria.amenities
          }));
          window.location.assign('/portal.html?view=discovery&resumeBooking=1');
        } catch {
          feedback.textContent = 'Không thể chuyển lựa chọn sang luồng đặt phòng. Vui lòng mở mục Đặt phòng trong cổng thành viên.';
        }
      });
      card.append(action); target.append(card);
    }
    const note = document.createElement('small');
    note.textContent = 'Các gợi ý không phải là booking đã xác nhận. Chỉ luồng đặt phòng hiện có mới xác nhận sau khi bạn kiểm tra lại thông tin.';
    target.append(note);
  };
  const send = async (question, isRetry = false) => {
    if (busy || !question.trim() || question.length > 2000) return;
    syncIdentity();
    lastQuestion = question;
    if (!token()) {
      feedback.textContent = 'Vui lòng đăng nhập để sử dụng Quản gia AI. Câu hỏi của bạn vẫn được giữ trong ô nhập.';
      input.value = question;
      login.hidden = false;
      return;
    }
    const requestIdentity = identity;
    if (!isRetry) bubble(question, true);
    if (!isRetry && conciergeRoomSearchIntent(question)) {
      input.value = '';
      feedback.textContent = '';
      retry.hidden = escalate.hidden = true;
      searchForm(knownRoomDetails(question));
      return;
    }
    input.value = '';
    feedback.textContent = '';
    retry.hidden = escalate.hidden = true;
    const pending = bubble('Đang kiểm tra thông tin khách sạn…');
    setBusy(true);
    try {
      const answer = await request('/ai/concierge/chat', { session_id: sessionId, message: question });
      if (token() !== requestIdentity) { syncIdentity(); return; }
      if (typeof answer?.reply !== 'string' || !answer.reply.trim()) throw new Error('Empty AI response');
      pending.textContent = answer.reply;
      const sources = Array.isArray(answer.citations) ? answer.citations
        .filter(item => item && typeof (item.doc || item.source) === 'string')
        .map(item => [item.doc || item.source, typeof item.section === 'string' ? item.section : ''].filter(Boolean).join(' · ')) : [];
      if (sources.length) {
        const note = document.createElement('small');
        note.textContent = `Nguồn: ${sources.join('; ')}`;
        pending.append(note);
      }
      if ((answer.escalate || answer.escalated) && answer.ticket_id) {
        feedback.textContent = `Đã chuyển tới lễ tân · Mã tiếp nhận: ${answer.ticket_id}`;
      }
    } catch (error) {
      if (token() !== requestIdentity) { syncIdentity(); return; }
      pending.textContent = fail(error);
    } finally {
      setBusy(false);
      messages.scrollTop = messages.scrollHeight;
      if (!panel.hidden) input.focus();
    }
  };
  $('form').addEventListener('submit', event => { event.preventDefault(); send(input.value.trim()); });
  root.querySelectorAll('[data-prompt]').forEach(button => button.addEventListener('click', () => {
    input.value = button.dataset.prompt;
    send(input.value);
  }));
  retry.addEventListener('click', () => { syncIdentity(); if (lastQuestion) send(lastQuestion, true); });
  escalate.addEventListener('click', async () => {
    syncIdentity();
    if (busy || !lastQuestion) return;
    const requestIdentity = token();
    setBusy(true);
    try {
      const ticket = await request('/ai/concierge/escalate', { session_id: sessionId, reason: lastQuestion });
      if (token() !== requestIdentity) { syncIdentity(); return; }
      if (!ticket?.ticket_id) throw new Error('Missing ticket');
      feedback.textContent = `Đã chuyển tới lễ tân · Mã tiếp nhận: ${ticket.ticket_id}`;
      escalate.hidden = retry.hidden = true;
    } catch (error) {
      if (token() !== requestIdentity) { syncIdentity(); return; }
      feedback.textContent = fail(error);
    } finally { setBusy(false); }
  });
}

if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initConciergeChat);
  else initConciergeChat();
}
