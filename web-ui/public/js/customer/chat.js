import { api } from "../../api.js";

export async function renderChat(container, session) {
    container.innerHTML = `
        <div class="page-header">
            <h1>Hỗ Trợ Trực Tuyến</h1>
            <p>Trò chuyện trực tiếp với nhân viên lễ tân của khách sạn để nhận hỗ trợ nhanh nhất.</p>
        </div>

        <div class="card chat-container" style="height: 480px;">
            <div class="chat-main">
                <div class="chat-messages" id="cust-chat-bubbles">
                    <!-- Messages go here -->
                </div>
                <form class="chat-input-area" id="cust-chat-form">
                    <input class="form-input" type="text" id="cust-chat-input" placeholder="Nhập câu hỏi của bạn..." required>
                    <button class="btn btn-primary" type="submit">Gửi tin</button>
                </form>
            </div>
        </div>
    `;

    const chatBubbles = document.getElementById("cust-chat-bubbles");
    const loadChat = async () => {
        try {
            const list = await api.get(`/chat/history?u1=${session.userId}&u2=1`);
            chatBubbles.innerHTML = "";
            if (!list || list.length === 0) {
                chatBubbles.innerHTML = "<div style='text-align: center; color: var(--text-muted); margin-top: 50px;'>Hãy bắt đầu cuộc hội thoại bằng cách gửi tin nhắn hỗ trợ đầu tiên.</div>";
                return;
            }
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

    loadChat();
    const interval = setInterval(() => {
        if (document.getElementById("cust-chat-bubbles")) {
            loadChat();
        } else {
            clearInterval(interval);
        }
    }, 5000);

    document.getElementById("cust-chat-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const input = document.getElementById("cust-chat-input");
        const val = input.value.trim();
        if (!val) return;

        try {
            await api.post("/chat/send", {
                senderId: session.userId,
                receiverId: 1,
                content: val
            });
            input.value = "";
            loadChat();
        } catch (err) {
            alert("Lỗi gửi tin nhắn: " + err.message);
        }
    });
}
