
import { api } from '../../api.js';
import { escapeHtml } from '../shared/ui.js';
import { normalizeSupportInboxes } from './chat-model.js';

let disposeChat = null;


export async function renderChat(container, session) {
    disposeChat?.();
    container.innerHTML = `

        <section class="support-view">
            <div class="page-header">
                <div><h1>Hộp thư hỗ trợ khách hàng</h1><p>Theo dõi hội thoại và trả lời yêu cầu của khách lưu trú.</p></div>
            </div>
            <div class="card support-workspace" data-conversation-open="false">
                <aside class="support-inbox" aria-label="Danh sách hội thoại">
                    <div class="support-inbox-heading"><h2>Hội thoại</h2><span id="support-count" class="badge" aria-label="Số hội thoại">0</span></div>
                    <div class="support-search"><label for="support-search">Tìm hội thoại</label><input id="support-search" class="input form-input" type="search" placeholder="Tên khách hoặc mã khách…" autocomplete="off"></div>
                    <p id="support-inbox-notice" class="support-notice" role="status" hidden></p>
                    <div id="staff-chat-inboxes" class="support-inbox-list"><p class="support-list-state" role="status">Đang tải hội thoại…</p></div>
                    <p class="support-inbox-footer"><i data-lucide="refresh-cw" aria-hidden="true"></i> Cập nhật tự động</p>
                </aside>
                <section id="staff-chat-window" class="support-conversation" aria-label="Cuộc trò chuyện" hidden>
                    <header class="support-thread-header">
                        <button id="support-back" class="btn btn-ghost btn-square" type="button" aria-label="Trở lại danh sách hội thoại"><i data-lucide="arrow-left" aria-hidden="true"></i></button>
                        <div class="avatar avatar-placeholder support-avatar" aria-hidden="true"><div id="support-thread-avatar">KH</div></div>
                        <div class="support-thread-title"><h2 id="support-thread-name"></h2><p id="support-thread-id"></p></div>
                    </header>
                    <div id="support-history-notice" class="support-history-notice" role="status" hidden><span></span><button class="btn btn-secondary" id="support-history-retry" type="button">Thử lại</button></div>
                    <div id="staff-chat-bubbles" class="support-messages" role="log" aria-label="Lịch sử tin nhắn" aria-live="polite" aria-relevant="additions text"></div>
                    <form id="staff-chat-form" class="support-composer">
                        <label for="staff-chat-input">Trả lời khách hàng</label>
                        <div class="support-compose-row">
                            <textarea id="staff-chat-input" class="textarea form-input" rows="2" placeholder="Nhập nội dung hỗ trợ…" required disabled></textarea>
                            <button id="support-send" class="btn btn-primary" type="submit" disabled><span>Gửi tin nhắn</span><i data-lucide="send" aria-hidden="true"></i></button>
                        </div>
                        <div class="support-compose-actions"><small>Ctrl + Enter để gửi</small></div>
                        <p id="support-send-feedback" class="support-send-feedback" role="status" aria-live="polite"></p>
                    </form>
                </section>
                <div id="chat-fallback-screen" class="support-placeholder">
                    <span class="support-placeholder-icon"><i data-lucide="messages-square" aria-hidden="true"></i></span>
                    <h2>Sẵn sàng hỗ trợ khách hàng</h2><p>Chọn một hội thoại để xem tin nhắn và bắt đầu trả lời.</p>
                </div>
            </div>
        </section>`;


    const root = container.querySelector('.support-view');
    const find = selector => root.querySelector(selector);
    const workspace = find('.support-workspace');
    const inbox = find('#staff-chat-inboxes');
    const messages = find('#staff-chat-bubbles');
    const thread = find('#staff-chat-window');
    const fallback = find('#chat-fallback-screen');
    const search = find('#support-search');
    const input = find('#staff-chat-input');
    const send = find('#support-send');
    const feedback = find('#support-send-feedback');
    const controller = new AbortController();
    let stopped = false, sending = false, inboxBusy = false, historyReady = false;
    let activeId = null, inboxList = [], historyVersion = 0, historyKey = '';
    const drafts = new Map();
    const alive = () => !stopped && root.isConnected;
    const icons = () => window.lucide?.createIcons?.();
    const requestOptions = () => ({ signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]) });
    const nameOf = item => item?.senderName?.trim() || `Khách #${item?.customerId ?? activeId}`;
    const initials = name => name.split(/\s+/).filter(Boolean).slice(-2).map(part => part[0]).join('').toUpperCase();
    const timestamp = (value, short = false) => {
        if (!value) return '';
        const date = new Date(value);
        if (!Number.isFinite(date.getTime())) return '';
        return date.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', ...(short ? {} : { day: '2-digit', month: '2-digit' }), hour: '2-digit', minute: '2-digit', hour12: false });
    };
    const updateComposer = () => {
        input.disabled = !activeId || !historyReady || sending || stopped;
        send.disabled = input.disabled || !input.value.trim();
        send.querySelector('span').textContent = sending ? 'Đang gửi…' : 'Gửi tin nhắn';
    };
    const showInboxNotice = text => {
        const notice = find('#support-inbox-notice');
        notice.textContent = text;
        notice.hidden = !text;
    };

    const renderInboxes = () => {
        const focusedId = inbox.contains(document.activeElement) ? document.activeElement.dataset.customerId : null;
        inbox.replaceChildren();
        const q = search.value.trim().toLocaleLowerCase('vi-VN');
        const filtered = inboxList.filter(item => `${nameOf(item)} ${item.customerId}`.toLocaleLowerCase('vi-VN').includes(q));
        find('#support-count').textContent = String(inboxList.length);
        if (!filtered.length) {
            const empty = document.createElement('p');
            empty.className = 'support-list-state';
            empty.textContent = q ? 'Không tìm thấy hội thoại phù hợp.' : 'Chưa có tin nhắn từ khách hàng.';
            inbox.appendChild(empty);
        }
        filtered.forEach(item => {
            const name = nameOf(item);
            const button = document.createElement('button');
            button.type = 'button';
            button.className = `support-inbox-item ${String(item.customerId) === activeId ? 'active' : ''}`;
            button.dataset.customerId = String(item.customerId);
            button.setAttribute('aria-pressed', String(String(item.customerId) === activeId));
            button.innerHTML = `<span class="avatar avatar-placeholder support-avatar" aria-hidden="true"><span>${escapeHtml(initials(name))}</span></span><span class="support-inbox-copy"><span class="support-inbox-line"><strong>${escapeHtml(name)}</strong><time>${escapeHtml(timestamp(item.createdAt, true))}</time></span><span class="support-inbox-preview">${escapeHtml(item.content || 'Xem cuộc trò chuyện')}</span></span>`;
            button.addEventListener('click', () => selectConversation(item));
            inbox.appendChild(button);
        });
        if (focusedId) [...inbox.querySelectorAll('button')].find(button => button.dataset.customerId === focusedId)?.focus();
    };

    const inboxNames = new Map();
    const filterInboxes = () => {
        const query = document.getElementById('staff-chat-search').value.trim().toLocaleLowerCase('vi');
        let visible = 0;
        inboxesDiv.querySelectorAll('.sc-chat-inbox').forEach(item => {
            item.hidden = !item.dataset.search.includes(query);
            if (!item.hidden) visible++;
        });
        document.getElementById('staff-chat-no-results').hidden = !query || visible > 0;
    };
    document.getElementById('staff-chat-search').addEventListener('input', filterInboxes);

    const loadInboxes = async () => {
        if (!alive() || inboxBusy) return;
        inboxBusy = true;
        try {

            const rows = await api.get(`/chat/inbox?staffId=${encodeURIComponent(session.userId)}`, requestOptions());
            if (!alive()) return;
            const next = normalizeSupportInboxes(rows, session.userId);
            const changed = JSON.stringify(inboxList) !== JSON.stringify(next);
            inboxList = next;
            if (changed || !inbox.querySelector('button')) renderInboxes();
            showInboxNotice('');
        } catch (error) {
            if (alive()) {
                if (!inboxList.length) inbox.innerHTML = '<p class="support-list-state">Chưa thể tải danh sách hội thoại.</p>';
                showInboxNotice('Kết nối tạm gián đoạn. Hệ thống sẽ tự động thử lại.');
            }
        } finally {
            inboxBusy = false;
        }
    };

    const loadHistory = async (id, initial = false) => {
        const version = ++historyVersion;
        if (initial) {
            historyReady = false;
            messages.innerHTML = '<p class="support-list-state" role="status">Đang tải tin nhắn…</p>';
            messages.setAttribute('aria-busy', 'true');
            updateComposer();
        }
        try {
            const rows = await api.get(`/chat/history?u1=${encodeURIComponent(session.userId)}&u2=${encodeURIComponent(id)}`, requestOptions());
            if (!alive() || id !== activeId || version !== historyVersion) return;
            const list = Array.isArray(rows) ? rows : [];
            const key = `${id}:${JSON.stringify(list)}`;
            const previousScroll = messages.scrollTop;
            const nearBottom = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 80;
            if (key !== historyKey || initial) {
                messages.replaceChildren();
                if (!list.length) messages.innerHTML = '<p class="support-list-state">Chưa có tin nhắn trong hội thoại này.</p>';
                list.forEach(msg => {
                    const own = String(msg.senderId) === String(session.userId);
                    const row = document.createElement('div');
                    row.className = `chat ${own ? 'chat-end' : 'chat-start'}`;
                    const author = document.createElement('div');
                    author.className = 'chat-header';
                    author.textContent = own ? 'Bạn' : find('#support-thread-name').textContent;
                    const bubble = document.createElement('div');
                    bubble.className = 'chat-bubble';
                    bubble.textContent = msg.content || '';
                    row.append(author, bubble);
                    const time = timestamp(msg.createdAt);
                    if (time) { const footer = document.createElement('div'); footer.className = 'chat-footer'; footer.textContent = time; row.appendChild(footer); }
                    messages.appendChild(row);
                });
                if (initial || nearBottom) messages.scrollTop = messages.scrollHeight;
                else messages.scrollTop = previousScroll;
                historyKey = key;
            }
            historyReady = true;
            find('#support-history-notice').hidden = true;
        } catch (error) {
            if (!alive() || id !== activeId || version !== historyVersion) return;
            if (initial) messages.replaceChildren();
            const notice = find('#support-history-notice');
            notice.querySelector('span').textContent = 'Không thể tải tin nhắn. Vui lòng thử lại.';
            notice.hidden = false;
        } finally {
            if (alive() && id === activeId && version === historyVersion) {
                messages.setAttribute('aria-busy', 'false');
                updateComposer();
            }

        }
    };

    const selectConversation = item => {
        if (activeId) drafts.set(activeId, input.value);
        activeId = String(item.customerId);
        const name = nameOf(item);
        find('#support-thread-name').textContent = name;
        find('#support-thread-id').textContent = `Khách hàng #${item.customerId}`;
        find('#support-thread-avatar').textContent = initials(name);
        thread.hidden = false;
        fallback.hidden = true;
        workspace.dataset.conversationOpen = 'true';
        feedback.textContent = '';
        find('#support-history-notice').hidden = true;
        input.value = drafts.get(activeId) || '';
        renderInboxes();
        loadHistory(activeId, true);
    };

    find('#support-back').addEventListener('click', () => {
        if (activeId) drafts.set(activeId, input.value);
        const previous = activeId;
        activeId = null;
        historyVersion++;
        thread.hidden = true;
        fallback.hidden = false;
        workspace.dataset.conversationOpen = 'false';
        renderInboxes();
        [...inbox.querySelectorAll('button')].find(button => button.dataset.customerId === previous)?.focus();
    });
    search.addEventListener('input', renderInboxes);
    input.addEventListener('input', () => { if (activeId) drafts.set(activeId, input.value); updateComposer(); });
    input.addEventListener('keydown', event => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !send.disabled) { event.preventDefault(); find('#staff-chat-form').requestSubmit(); }
    });
    find('#support-history-retry').addEventListener('click', () => { if (activeId) loadHistory(activeId, !historyReady); });
    find('#staff-chat-form').addEventListener('submit', async event => {
        event.preventDefault();
        const content = input.value.trim();
        if (!alive() || sending || !activeId || !historyReady || !content) return;
        const receiver = activeId;
        const draft = input.value;
        sending = true;
        feedback.textContent = '';
        updateComposer();
        try {
            await api.post('/chat/send', { senderId: session.userId, receiverId: Number(receiver), content }, requestOptions());
            if (!alive()) return;
            if (drafts.get(receiver) === draft) drafts.delete(receiver);
            if (activeId === receiver) {
                input.value = '';
                feedback.textContent = 'Đã gửi tin nhắn.';
                await loadHistory(receiver);
            }
            loadInboxes();
        } catch (error) {
            if (alive() && activeId === receiver) feedback.textContent = 'Chưa gửi được tin nhắn. Nội dung đã được giữ lại để bạn thử lại.';
        } finally {
            sending = false;
            if (alive()) updateComposer();
        }
    });

    let interval;
    const observer = new MutationObserver(() => { if (!root.isConnected || root.closest('#app-view')?.hidden) stop(); });
    const stop = () => {
        if (stopped) return;
        stopped = true;
        clearInterval(interval);
        controller.abort();
        observer.disconnect();
        window.removeEventListener('hotel:session-expired', stop);
        if (disposeChat === stop) disposeChat = null;
        if (root.isConnected) updateComposer();
    };
    disposeChat = stop;
    observer.observe(container, { childList: true });
    const appView = root.closest('#app-view');
    if (appView) observer.observe(appView, { attributes: true, attributeFilter: ['hidden'] });
    window.addEventListener('hotel:session-expired', stop);
    icons();
    if (!session?.userId) {
        showInboxNotice('Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.');
        inbox.replaceChildren();
        stop();
        return;
    }
    loadInboxes();
    let polling = false;
    interval = setInterval(async () => {
        if (!alive() || sending || polling) return;
        polling = true;
        try {
            await loadInboxes();
            if (alive() && activeId) await loadHistory(activeId);
        } finally { polling = false; }
    }, 5000);
}
