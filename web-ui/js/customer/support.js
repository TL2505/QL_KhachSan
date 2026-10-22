import { api } from '../../api.js';

let disposeSupport;

export async function renderSupport(container, session = {}) {
  if (!container) return;
  disposeSupport?.();
  container.innerHTML = `
    <section class="customer-support" aria-label="Nhắn tin với nhân viên">
      <div class="customer-support-intro"><h2>Chúng tôi có thể giúp gì cho bạn?</h2><p>Nhắn tin cho đội ngũ lễ tân để được hỗ trợ về đặt phòng và kỳ lưu trú.</p></div>
      <section class="card customer-support-chat" aria-labelledby="customer-support-title">
        <header class="customer-support-header"><span class="customer-support-icon" aria-hidden="true"><i data-lucide="headset"></i></span><div><h3 id="customer-support-title">Lễ tân Aurelia Palace</h3><p>Tin nhắn được chuyển đến đội ngũ nhân viên hỗ trợ.</p></div><button id="customer-support-refresh" class="btn btn-ghost" type="button" aria-label="Làm mới tin nhắn"><i data-lucide="refresh-cw" aria-hidden="true"></i></button></header>
        <p id="customer-support-notice" role="status" hidden></p>
        <div id="customer-support-messages" class="customer-support-messages" role="log" aria-label="Lịch sử trò chuyện với nhân viên" aria-live="polite" aria-relevant="additions text"><p class="customer-support-empty">Đang tải hội thoại…</p></div>
        <form id="customer-support-form" class="customer-support-composer">
          <label for="customer-support-input">Tin nhắn của bạn</label>
          <div class="customer-support-compose-row"><textarea id="customer-support-input" class="textarea form-input" rows="2" maxlength="2000" placeholder="Nhập nội dung cần hỗ trợ…" required disabled></textarea><button id="customer-support-send" class="btn portal-primary" type="submit" disabled><span>Gửi tin nhắn</span><i data-lucide="send" aria-hidden="true"></i></button></div>
          <div class="customer-support-compose-footer"><small>Ctrl + Enter để gửi · Tối đa 2.000 ký tự</small><span id="customer-support-feedback" role="status" aria-live="polite"></span></div>
        </form>
      </section>
      <p class="customer-support-footnote">Bạn có thể rời trang và quay lại để xem phản hồi. Nếu cần hỗ trợ khẩn cấp trong kỳ nghỉ, vui lòng liên hệ trực tiếp quầy lễ tân.</p>
    </section>`;
  const root = container.querySelector('.customer-support');
  const find = id => root.querySelector('#customer-support-' + id);
  const messages = find('messages'), input = find('input'), send = find('send');
  const controller = new AbortController();
  let stopped = false, loading = false, sending = false, ready = false, historyKey = '', interval;
  const alive = () => !stopped && root.isConnected;
  const options = () => ({ signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]), retryOnNetworkError: false });
  const composer = () => { input.disabled = !ready || sending || stopped; send.disabled = input.disabled || !input.value.trim(); send.querySelector('span').textContent = sending ? 'Đang gửi…' : 'Gửi tin nhắn'; };
  const notice = text => { find('notice').textContent = text; find('notice').hidden = !text; };
  const timestamp = value => { const date = new Date(value); return value && Number.isFinite(date.getTime()) ? date.toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}) : ''; };
  const load = async () => {
    if (!alive() || loading) return;
    loading = true; find('refresh').disabled = true;
    try {
      const rows = await api.get('/chat/history?' + new URLSearchParams({u1:session.userId,u2:0}), options());
      if (!alive()) return;
      if (!Array.isArray(rows)) throw Error('Invalid history');
      const key = JSON.stringify(rows);
      if (key !== historyKey) {
        const nearBottom = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 80;
        const previousScroll = messages.scrollTop;
        messages.replaceChildren();
        if (!rows.length) messages.innerHTML = '<div class="customer-support-empty"><i data-lucide="messages-square" aria-hidden="true"></i><strong>Bắt đầu trò chuyện với lễ tân</strong><p>Gửi câu hỏi về đặt phòng, dịch vụ hoặc yêu cầu hỗ trợ. Nhân viên sẽ phản hồi tại đây.</p></div>';
        for (const msg of rows) {
          const own = String(msg.senderId) === String(session.userId);
          const row = document.createElement('div'); row.className = `chat ${own ? 'chat-end' : 'chat-start'}`;
          const author = document.createElement('div'); author.className = 'chat-header'; author.textContent = own ? 'Bạn' : 'Nhân viên lễ tân';
          const bubble = document.createElement('div'); bubble.className = 'chat-bubble'; bubble.textContent = msg.content || '';
          row.append(author,bubble);
          const time = timestamp(msg.createdAt);
          if (time) { const footer = document.createElement('div'); footer.className = 'chat-footer'; footer.textContent = time; row.append(footer); }
          messages.append(row);
        }
        if (!ready || nearBottom) messages.scrollTop = messages.scrollHeight; else messages.scrollTop = previousScroll;
        historyKey = key;
        window.lucide?.createIcons?.();
      }
      ready = true; notice('');
    } catch (error) {
      if (alive()) {
        if (!ready) messages.innerHTML = '<p class="customer-support-empty">Chưa thể tải hội thoại.</p>';
        notice('Kết nối tạm gián đoạn. Bấm làm mới để thử lại; hệ thống cũng sẽ tự cập nhật.');
      }
    } finally { loading = false; if (alive()) {find('refresh').disabled = false;composer();} }
  };
  find('refresh').onclick = load;
  input.addEventListener('input',composer);
  input.addEventListener('keydown',event => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !send.disabled) {event.preventDefault();find('form').requestSubmit();} });
  find('form').addEventListener('submit',async event => {
    event.preventDefault(); const content = input.value.trim();
    if (!alive() || !ready || sending || !content || content.length > 2000) return;
    sending = true; composer();find('feedback').textContent = '';
    try {
      await api.post('/chat/send',{senderId:Number(session.userId),receiverId:0,content},options());
      if (!alive()) return;
      input.value = ''; find('feedback').textContent = 'Đã gửi đến nhân viên.';
      await load();
    } catch (error) {if (alive()) find('feedback').textContent = 'Chưa xác nhận được tin nhắn đã gửi. Nội dung được giữ lại; hãy làm mới hội thoại trước khi thử lại.';}
    finally {sending = false;if (alive()) composer();}
  });
  const observer = new MutationObserver(() => {if (!root.isConnected || root.closest('#app-view')?.hidden) stop();});
  function stop() {
    if (stopped) return;
    stopped = true; clearInterval(interval);controller.abort();observer.disconnect();
    window.removeEventListener('hotel:session-expired',stop);
    if (disposeSupport === stop) disposeSupport = null;
    if (root.isConnected) composer();
  }
  disposeSupport = stop;
  observer.observe(container,{childList:true});
  const app = root.closest('#app-view'); if (app) observer.observe(app,{attributes:true,attributeFilter:['hidden']});
  window.addEventListener('hotel:session-expired',stop);
  window.lucide?.createIcons?.();
  if (!Number.isSafeInteger(Number(session.userId)) || Number(session.userId) <= 0) { notice('Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.');stop();return; }
  await load();
  if (alive()) interval = setInterval(() => {if (!sending) load();},5000);
}
