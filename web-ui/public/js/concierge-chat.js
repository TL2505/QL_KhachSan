import { apiFetch, getAuthSession, getStoredCustomerToken } from './api.js';

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
    <link rel="stylesheet" href="${new URL('../css/concierge-chat.css', import.meta.url)}">
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
