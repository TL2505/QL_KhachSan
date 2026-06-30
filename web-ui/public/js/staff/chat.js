import { api } from "../../api.js";

export async function renderChat(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Hộp Thư Hỗ Trợ Khách Hàng</h1>
            <p>Danh sách tin nhắn của khách hàng cần trợ giúp trực tuyến.</p>
        </div>

        <div class="card chat-container" style="height: 480px;">
            <div class="chat-inboxes" id="staff-chat-inboxes">
                <div style="padding: 15px; color: var(--text-muted); font-size: 13px;">Đang tải danh sách...</div>
            </div>
            <div class="chat-main" id="staff-chat-window" style="display: none;">
                <div class="chat-messages" id="staff-chat-bubbles">
                    <!-- Messages go here -->
                </div>
                <form class="chat-input-area" id="staff-chat-form">
                    <input class="form-input" type="text" id="staff-chat-input" placeholder="Nhập câu trả lời..." required>
                    <button class="btn btn-primary" type="submit">Gửi</button>
                </form>
            </div>
            <div id="chat-fallback-screen" style="flex-grow: 1; display: flex; justify-content: center; align-items: center; color: var(--text-muted);">
                Chọn một hội thoại bên trái để bắt đầu trả lời khách hàng
            </div>
        </div>
    `;

    let activeCustomerId = null;
    const inboxesDiv = document.getElementById("staff-chat-inboxes");
    const chatBubbles = document.getElementById("staff-chat-bubbles");
    const chatWindow = document.getElementById("staff-chat-window");
    const fallbackScreen = document.getElementById("chat-fallback-screen");

    const loadInboxes = async () => {
        try {
            const inboxList = await api.get("/chat/inbox");
            inboxesDiv.innerHTML = "";
            if (!inboxList || inboxList.length === 0) {
                inboxesDiv.innerHTML = "<div style='padding: 15px; color: var(--text-muted); font-size: 13px;'>Không có tin nhắn nào.</div>";
                return;
            }

            inboxList.forEach(item => {
                const itemDiv = document.createElement("div");
                itemDiv.className = `inbox-item ${activeCustomerId === item.senderId ? 'active' : ''}`;
                itemDiv.innerHTML = `
                    <div style="font-weight: 600; font-size: 14px;">User #${item.senderId}</div>
                    <div style="font-size: 12px; color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                        ${item.content}
                    </div>
                `;
                itemDiv.addEventListener("click", () => {
                    activeCustomerId = item.senderId;
                    loadInboxes(); // Re-render inbox selector
                    openConversation(item.senderId);
                });
                inboxesDiv.appendChild(itemDiv);
            });
        } catch (e) {
            console.error(e);
        }
    };

    const openConversation = async (userId) => {
        fallbackScreen.style.display = "none";
        chatWindow.style.display = "flex";
        
        try {
            const list = await api.get(`/chat/history?u1=${session.userId}&u2=${userId}`);
            chatBubbles.innerHTML = "";
            list.forEach(msg => {
                const bubble = document.createElement("div");
                bubble.className = `chat-bubble ${msg.senderId === session.userId ? 'sent' : 'received'}`;
                bubble.textContent = msg.content;
                chatBubbles.appendChild(bubble);
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
            alert("Lỗi gửi tin: " + err.message);
        }
    });
}
