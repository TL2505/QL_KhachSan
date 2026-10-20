import { api } from "../../api.js";
import { escapeHtml } from "../shared/ui.js";

function messageTime(value) {
    const date = new Date(value || '');
    return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    }).format(date) : '';
}

export async function renderChat(container, session) {
    container.innerHTML = `
        <style>
            body.staff-portal:has(.sc-chat-view) .portal-shell { grid-template-columns:248px minmax(0,1fr); }
            body.staff-portal:has(.sc-chat-view) :is(.portal-sidebar,.portal-nav) { min-width:0; }
            .sc-chat-view { min-width:0; color:var(--portal-text); }
            .sc-chat-view * { box-sizing:border-box; }
            .sc-chat-intro { display:flex; justify-content:space-between; flex-wrap:wrap; gap:10px 20px; align-items:center; margin-bottom:22px; }
            .sc-chat-intro p { margin:0; font-size:13px; line-height:1.7; color:var(--portal-muted); }
            .sc-chat-eyebrow { display:block; margin:0 0 6px; color:var(--portal-gold); font-size:10px; font-weight:650; letter-spacing:.1em; }
            .sc-chat-intro > span { display:flex; align-items:center; gap:7px; color:var(--portal-muted); font-size:11px; }
            .sc-chat-view svg { width:16px; height:16px; flex:none; }
            #view-container .sc-chat-layout { display:grid; grid-template-columns:290px minmax(0,1fr); height:clamp(520px,calc(100dvh - 295px),740px); min-height:0; border:1px solid var(--portal-border); border-radius:14px; background:var(--portal-panel); overflow:hidden; }
            .sc-chat-list-pane { display:flex; flex-direction:column; grid-column:1; grid-row:1; min-width:0; min-height:0; border-right:1px solid var(--portal-border); }
            .sc-chat-list-top { padding:21px 18px 16px; border-bottom:1px solid var(--portal-border); }
            .sc-chat-list-heading { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:16px; }
            .sc-chat-list-heading h2 { margin:0; font-family:inherit; font-size:14px; line-height:1.4; font-weight:650; }
            .sc-chat-count { padding:3px 7px; border-radius:6px; color:var(--portal-muted); background:var(--portal-surface); font-size:10px; font-variant-numeric:tabular-nums; }
            .sc-chat-search { position:relative; display:block; }
            .sc-chat-search svg { position:absolute; top:50%; left:11px; transform:translateY(-50%); color:var(--portal-muted); }
            #view-container .sc-chat-search input { display:block; width:100%; min-width:0; min-height:39px; margin:0; padding:9px 10px 9px 34px; border:1px solid var(--portal-border); border-radius:8px; background:var(--portal-bg); color:var(--portal-text); font:inherit; font-size:11px; }
            .sc-chat-view input::placeholder { color:var(--portal-muted); opacity:.85; }
            .sc-chat-inboxes { flex:1; min-height:0; padding:9px; overflow:auto; scrollbar-width:thin; scrollbar-color:var(--portal-border) transparent; }
            #view-container .sc-chat-inbox { display:flex; align-items:center; gap:10px; width:100%; min-height:77px; margin-bottom:5px; padding:12px 10px; border:1px solid transparent; border-radius:10px; background:transparent; color:var(--portal-text); text-align:left; cursor:pointer; font:inherit; }
            #view-container .sc-chat-inbox:hover { background:var(--portal-surface); }
            #view-container .sc-chat-inbox.active { border-color:color-mix(in srgb,var(--portal-gold) 28%,transparent); background:color-mix(in srgb,var(--portal-gold) 9%,transparent); }
            #view-container .sc-chat-inbox[hidden] { display:none; }
            .sc-chat-avatar { display:grid; place-items:center; width:37px; height:37px; border:1px solid var(--portal-border); border-radius:11px; background:var(--portal-surface); color:var(--portal-soft); font-size:12px; font-weight:650; flex:none; }
            .sc-chat-inbox-copy { display:block; flex:1; min-width:0; }
            .sc-chat-inbox-top { display:flex; justify-content:space-between; align-items:baseline; gap:8px; margin-bottom:6px; }
            .sc-chat-inbox-top strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:12px; line-height:1.5; font-weight:600; }
            .sc-chat-inbox-top time { flex:none; color:var(--portal-muted); font-size:9px; }
            .sc-chat-preview { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:var(--portal-muted); font-size:11px; line-height:1.6; }
            .sc-chat-note { padding:22px 13px; margin:0; color:var(--portal-muted); text-align:center; font-size:12px; line-height:1.8; }
            .sc-chat-error { color:#f3a58f; }
            .sc-chat-main { grid-column:2; grid-row:1; display:flex; flex-direction:column; min-width:0; min-height:0; background:color-mix(in srgb,var(--portal-bg) 35%,var(--portal-panel)); }
            .sc-chat-conversation-head { display:flex; align-items:center; gap:12px; min-height:81px; padding:18px 24px; border-bottom:1px solid var(--portal-border); background:var(--portal-panel); }
            .sc-chat-conversation-head h3 { margin:0 0 5px; font-family:inherit; font-size:14px; line-height:1.5; font-weight:650; overflow-wrap:anywhere; }
            .sc-chat-conversation-head p { margin:0; color:var(--portal-muted); font-size:11px; }
            .sc-chat-messages { display:flex; flex-direction:column; gap:17px; min-height:0; flex:1; overflow:auto; padding:24px; scrollbar-width:thin; scrollbar-color:var(--portal-border) transparent; }
            .sc-chat-message { max-width:80%; display:flex; flex-direction:column; gap:6px; align-self:flex-start; }
            .sc-chat-message.sent { align-self:flex-end; }
            .sc-chat-message .chat-bubble { padding:12px 15px; border:1px solid var(--portal-border); border-radius:12px 12px 12px 3px; background:var(--portal-panel); font-size:13px; line-height:1.75; overflow-wrap:anywhere; white-space:pre-wrap; }
            .sc-chat-message.sent .chat-bubble { border-color:color-mix(in srgb,var(--portal-gold) 27%,transparent); border-radius:12px 12px 3px 12px; background:color-mix(in srgb,var(--portal-gold) 12%,var(--portal-panel)); }
            .sc-chat-message small { color:var(--portal-muted); font-size:10px; line-height:1.5; }
            .sc-chat-message.sent small { text-align:right; }
            .sc-chat-compose { display:flex; gap:10px; padding:17px 20px; border-top:1px solid var(--portal-border); background:var(--portal-panel); }
            #view-container .sc-chat-compose input { width:100%; min-width:0; flex:1; min-height:44px; margin:0; padding:11px 14px; border:1px solid var(--portal-border); border-radius:9px; background:var(--portal-bg); color:var(--portal-text); font:inherit; font-size:13px; }
            #view-container .sc-chat-send { display:inline-flex; justify-content:center; align-items:center; gap:8px; min-height:44px; padding:10px 17px; border:1px solid var(--portal-gold); border-radius:9px; color:#161510; background:var(--portal-gold); font:inherit; font-size:12px; font-weight:650; cursor:pointer; }
            .sc-chat-view :is(input,button):focus-visible { outline:2px solid var(--portal-gold); outline-offset:3px; }
            .sc-chat-fallback { display:flex; grid-column:2; grid-row:1; align-items:center; justify-content:center; min-width:0; padding:28px; text-align:center; background:color-mix(in srgb,var(--portal-bg) 35%,var(--portal-panel)); }
            .sc-chat-empty-icon { display:grid; place-items:center; width:72px; height:72px; margin:0 auto 22px; border:1px solid var(--portal-border); border-radius:22px; background:var(--portal-surface); color:var(--portal-gold); }
            .sc-chat-empty-icon svg { width:30px; height:30px; }
            .sc-chat-fallback h3 { margin:0 0 10px; font-family:inherit; font-size:18px; font-weight:600; }
            .sc-chat-fallback p { max-width:310px; margin:0; color:var(--portal-muted); font-size:12px; line-height:1.8; }
            html[data-theme="light"] body.staff-portal:has(.sc-chat-view) .portal-content { background:var(--portal-bg); }
            html[data-theme="light"] #view-container .sc-chat-send { color:#fff; }
            html[data-theme="light"] .sc-chat-error { color:#b34d35; }
            @media(max-width:1200px) { #view-container .sc-chat-layout { grid-template-columns:250px minmax(0,1fr); } }
            @media(max-width:1000px) { body.staff-portal:has(.sc-chat-view) .portal-shell { grid-template-columns:minmax(0,1fr); } }
            @media(max-width:700px) { #view-container .sc-chat-layout { grid-template-columns:minmax(0,1fr); grid-template-rows:225px 470px; height:auto; } .sc-chat-list-pane { grid-column:1; grid-row:1; border-right:0; border-bottom:1px solid var(--portal-border); } .sc-chat-list-top { padding:14px; } .sc-chat-list-heading { margin-bottom:10px; } .sc-chat-main,.sc-chat-fallback { grid-column:1; grid-row:2; } .sc-chat-conversation-head { min-height:72px; padding:14px 16px; } .sc-chat-messages { padding:18px 14px; } .sc-chat-compose { padding:13px; gap:8px; } #view-container .sc-chat-send { padding:10px 12px; } .sc-chat-message { max-width:92%; } .sc-chat-intro > span { font-size:10px; } }
        </style>
        <section class="sc-chat-view" aria-label="Tin nhắn hỗ trợ khách hàng">
            <div class="sc-chat-intro"><div><span class="sc-chat-eyebrow">CHĂM SÓC KHÁCH HÀNG</span><p>Tiếp nhận yêu cầu và trao đổi với khách lưu trú.</p></div><span><i data-lucide="refresh-cw" aria-hidden="true"></i>Tin nhắn được cập nhật định kỳ</span></div>
            <div class="sc-chat-layout">
                <section class="sc-chat-list-pane" aria-label="Danh sách hội thoại"><div class="sc-chat-list-top"><div class="sc-chat-list-heading"><h2>Hội thoại</h2><span class="sc-chat-count" id="staff-chat-count">—</span></div><label class="sc-chat-search"><i data-lucide="search" aria-hidden="true"></i><input type="search" id="staff-chat-search" placeholder="Tìm hội thoại…" aria-label="Tìm hội thoại theo tên, mã khách hoặc nội dung"></label></div><div class="sc-chat-inboxes" id="staff-chat-inboxes"><p class="sc-chat-note" role="status">Đang tải hội thoại…</p></div><p class="sc-chat-note" id="staff-chat-no-results" hidden>Không tìm thấy hội thoại phù hợp.</p></section>
                <section class="sc-chat-main" id="staff-chat-window" aria-label="Nội dung hội thoại" style="display:none"><div class="sc-chat-conversation-head"><span class="sc-chat-avatar"><i data-lucide="user-round" aria-hidden="true"></i></span><div><h3 id="staff-chat-name">Hội thoại với khách</h3><p id="staff-chat-reference"></p></div></div><div class="sc-chat-messages" id="staff-chat-bubbles" role="log" aria-label="Lịch sử tin nhắn" aria-live="polite"></div><form class="sc-chat-compose" id="staff-chat-form"><input type="text" id="staff-chat-input" placeholder="Nhập tin nhắn trả lời khách…" aria-label="Nội dung trả lời" autocomplete="off" required><button class="sc-chat-send" type="submit">Gửi<i data-lucide="send" aria-hidden="true"></i></button></form></section>
                <div class="sc-chat-fallback" id="chat-fallback-screen"><div><span class="sc-chat-empty-icon"><i data-lucide="messages-square" aria-hidden="true"></i></span><h3>Sẵn sàng hỗ trợ khách</h3><p>Chọn một hội thoại để xem nội dung và gửi tin nhắn trả lời.</p></div></div>
            </div>
        </section>
    `;
    globalThis.lucide?.createIcons();

    let activeCustomerId = null;
    const inboxesDiv = document.getElementById("staff-chat-inboxes");
    const chatBubbles = document.getElementById("staff-chat-bubbles");
    const chatWindow = document.getElementById("staff-chat-window");
    const fallbackScreen = document.getElementById("chat-fallback-screen");

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
        try {
            // Inbox is scoped to the authenticated receptionist. The backend
            // requires this identifier for the legacy chat query.
            const inboxList = await api.get(`/chat/inbox?staffId=${encodeURIComponent(session.userId)}`);
            inboxesDiv.innerHTML = "";
            document.getElementById('staff-chat-count').textContent = inboxList?.length || 0;
            document.getElementById('staff-chat-no-results').hidden = true;
            if (!inboxList || inboxList.length === 0) {
                inboxesDiv.innerHTML = '<p class="sc-chat-note">Chưa có hội thoại nào.<br>Tin nhắn của khách sẽ xuất hiện tại đây.</p>';
                return;
            }

            inboxList.forEach(item => {
                const name = item.senderName?.trim() || `Khách #${item.senderId}`;
                inboxNames.set(item.senderId, name);
                const initials = name.split(/\s+/).filter(Boolean).slice(-2).map(part => part.charAt(0)).join('').toUpperCase();
                const itemDiv = document.createElement("button");
                itemDiv.type = 'button';
                itemDiv.className = `sc-chat-inbox ${activeCustomerId === item.senderId ? 'active' : ''}`;
                itemDiv.setAttribute('aria-pressed', String(activeCustomerId === item.senderId));
                itemDiv.dataset.search = `${name} ${item.senderId} ${item.content || ''}`.toLocaleLowerCase('vi');
                itemDiv.innerHTML = `<span class="sc-chat-avatar" aria-hidden="true">${escapeHtml(initials)}</span><span class="sc-chat-inbox-copy"><span class="sc-chat-inbox-top"><strong>${escapeHtml(name)}</strong><time>${messageTime(item.createdAt)}</time></span><span class="sc-chat-preview">${escapeHtml(item.content)}</span></span>`;
                itemDiv.addEventListener("click", () => {
                    activeCustomerId = item.senderId;
                    loadInboxes(); // Re-render inbox selector
                    openConversation(item.senderId);
                });
                inboxesDiv.appendChild(itemDiv);
            });
            filterInboxes();
        } catch (e) {
            console.error(e);
            inboxesDiv.innerHTML = '<p class="sc-chat-note sc-chat-error" role="alert">Chưa tải được hội thoại. Vui lòng chờ lần cập nhật tiếp theo.</p>';
            document.getElementById('staff-chat-count').textContent = '—';
        }
    };

    const openConversation = async (userId) => {
        fallbackScreen.style.display = "none";
        chatWindow.style.display = "flex";
        document.getElementById('staff-chat-name').textContent = inboxNames.get(userId) || `Khách #${userId}`;
        document.getElementById('staff-chat-reference').textContent = `Mã tài khoản khách #${userId}`;
        
        try {
            const list = await api.get(`/chat/history?u1=${session.userId}&u2=${userId}`);
            chatBubbles.innerHTML = "";
            list.forEach(msg => {
                const bubble = document.createElement("div");
                bubble.className = `chat-bubble ${msg.senderId === session.userId ? 'sent' : 'received'}`;
                bubble.textContent = msg.content;
                const row = document.createElement('div');
                row.className = `sc-chat-message ${msg.senderId === session.userId ? 'sent' : 'received'}`;
                const time = document.createElement('small');
                time.textContent = [msg.senderId === session.userId ? 'Bạn' : 'Khách', messageTime(msg.createdAt)].filter(Boolean).join(' · ');
                row.appendChild(bubble);
                row.appendChild(time);
                chatBubbles.appendChild(row);
            });
            chatBubbles.scrollTop = chatBubbles.scrollHeight;
        } catch (e) {
            console.error(e);
        }
    };

    loadInboxes();
    const interval = setInterval(() => {
        if (document.getElementById("staff-chat-inboxes")) {
            loadInboxes();
            if (activeCustomerId) openConversation(activeCustomerId);
        } else {
            clearInterval(interval);
        }
    }, 5000);

    document.getElementById("staff-chat-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const input = document.getElementById("staff-chat-input");
        const val = input.value.trim();
        if (!val || !activeCustomerId) return;

        try {
            await api.post("/chat/send", {
                senderId: session.userId,
                receiverId: activeCustomerId,
                content: val
            });
            input.value = "";
            openConversation(activeCustomerId);
        } catch (err) {
            window.showCustomAlert("Lỗi gửi tin: " + err.message);
        }
    });
}
