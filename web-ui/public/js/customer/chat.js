import { api } from "../../api.js";

const createSessionId = userId => {
    const storageKey = `aurelia-ai-session-${userId}`;
    try {
        let id = sessionStorage.getItem(storageKey);
        if (!id) {
            id = crypto.randomUUID();
            sessionStorage.setItem(storageKey, id);
        }
        return id;
    } catch {
        return crypto.randomUUID();
    }
};

export async function renderChat(container, session) {
    container.innerHTML = `
        <div class="concierge-view">
          <div class="page-header concierge-header">
              <div>
                  <p class="portal-eyebrow">AURELIA CONCIERGE</p>
                  <h2>Hỗ trợ trực tuyến</h2>
                  <p>Hỏi trợ lý về tiện ích khách sạn hoặc chuyển yêu cầu tới nhân viên lễ tân.</p>
              </div>
              <span class="concierge-status"><i aria-hidden="true"></i> Hỗ trợ khách hàng</span>
          </div>
          <div class="concierge-tabs" role="tablist" aria-label="Chọn hình thức hỗ trợ">
              <button class="concierge-tab active" type="button" role="tab" aria-selected="true" data-concierge-mode="ai">Trợ lý Aurelia</button>
              <button class="concierge-tab" type="button" role="tab" aria-selected="false" data-concierge-mode="staff">Nhắn lễ tân</button>
          </div>
          <section class="card chat-container concierge-panel" id="ai-concierge-panel" role="tabpanel" aria-label="Trò chuyện với trợ lý Aurelia">
            <div class="chat-main">
                <div class="chat-messages" id="ai-chat-messages" aria-live="polite" aria-relevant="additions">
                    <div class="chat-empty-state">
                        <span class="chat-empty-icon"><i data-lucide="sparkles" aria-hidden="true"></i></span>
                        <strong>Trợ lý Aurelia</strong>
                        <span>Hỏi về dịch vụ và tiện ích khách sạn. Thông tin được lấy từ nguồn dữ liệu khách sạn.</span>
                    </div>
                </div>
                <div class="chat-suggestions" aria-label="Câu hỏi gợi ý">
                    <button type="button" class="chat-suggestion" data-chat-prompt="Giờ nhận phòng và trả phòng là khi nào?">Giờ nhận/trả phòng</button>
                    <button type="button" class="chat-suggestion" data-chat-prompt="Khách sạn có những tiện nghi nào?">Tiện nghi khách sạn</button>
                    <button type="button" class="chat-suggestion" data-chat-prompt="Tôi muốn biết các dịch vụ hiện có.">Dịch vụ khách sạn</button>
                </div>
                <form class="chat-input-area" id="ai-chat-form">
                    <input class="form-input" type="text" id="ai-chat-input" maxlength="2000" placeholder="Nhập câu hỏi..." aria-label="Câu hỏi cho trợ lý Aurelia" required>
                    <button class="btn btn-primary" type="submit"><i data-lucide="send" aria-hidden="true"></i><span>Gửi</span></button>
                </form>
                <div class="ai-chat-feedback" id="ai-chat-feedback" role="status" aria-live="polite"></div>
                <div class="ai-chat-escalation" id="ai-chat-escalation" hidden>
                    <span>Trợ lý chưa thể xác minh câu trả lời. Bạn có thể chuyển câu hỏi hiện tại tới Lễ tân.</span>
                    <button class="btn btn-secondary btn-sm" id="ai-chat-escalate" type="button">Chuyển tới Lễ tân</button>
                </div>
            </div>
          </section>
          <section class="card chat-container concierge-panel" id="staff-concierge-panel" role="tabpanel" aria-label="Cuộc trò chuyện với nhân viên lễ tân" hidden>
            <div class="chat-main">
                <div class="chat-messages" id="cust-chat-bubbles" aria-live="polite" aria-relevant="additions">
                    <div class="chat-empty-state">
                        <span class="chat-empty-icon"><i data-lucide="messages-square" aria-hidden="true"></i></span>
                        <strong>Bắt đầu cuộc trò chuyện</strong>
                        <span>Gửi câu hỏi của bạn, nhân viên lễ tân sẽ hỗ trợ sớm nhất.</span>
                    </div>
                </div>
                <form class="chat-input-area" id="cust-chat-form">
                    <input class="form-input" type="text" id="cust-chat-input" maxlength="2000" placeholder="Nhập tin nhắn..." aria-label="Tin nhắn của bạn gửi Lễ tân" required>
                    <button class="btn btn-primary" type="submit"><i data-lucide="send" aria-hidden="true"></i><span>Gửi tin</span></button>
                </form>
                <p class="chat-feedback" id="cust-chat-feedback" role="status" aria-live="polite"></p>
            </div>
          </section>
        </div>
    `;
    if (window.lucide?.createIcons) window.lucide.createIcons();

    const sessionId = createSessionId(session.userId);
    const aiMessages = document.getElementById("ai-chat-messages");
    const aiForm = document.getElementById("ai-chat-form");
    const aiInput = document.getElementById("ai-chat-input");
    const aiFeedback = document.getElementById("ai-chat-feedback");
    const escalation = document.getElementById("ai-chat-escalation");
    let lastQuestion = "";
    let aiBusy = false;
    let staffLoading = false;
    let staffSending = false;
    let lastMessages = "";

    const appendBubble = (element, text, type) => {
        const bubble = document.createElement("div");
        bubble.className = `chat-bubble ${type}`;
        bubble.textContent = text;
        element.appendChild(bubble);
        element.scrollTop = element.scrollHeight;
        return bubble;
    };

    document.querySelectorAll(".concierge-tab").forEach(button => {
        button.addEventListener("click", () => {
            const isAI = button.dataset.conciergeMode === "ai";
            document.getElementById("ai-concierge-panel").hidden = !isAI;
            document.getElementById("staff-concierge-panel").hidden = isAI;
            document.querySelectorAll(".concierge-tab").forEach(tab => {
                const selected = tab === button;
                tab.classList.toggle("active", selected);
                tab.setAttribute("aria-selected", String(selected));
            });
            if (!isAI) loadStaffChat();
        });
    });

    document.querySelectorAll(".chat-suggestion").forEach(button => {
        button.addEventListener("click", () => {
            aiInput.value = button.dataset.chatPrompt || "";
            aiInput.focus();
        });
    });

    aiForm.addEventListener("submit", async event => {
        event.preventDefault();
        if (aiBusy) return;
        const question = aiInput.value.trim();
        if (!question) return;

        const submit = aiForm.querySelector('button[type="submit"]');
        aiBusy = true;
        lastQuestion = question;
        escalation.hidden = true;
        aiFeedback.textContent = "";
        appendBubble(aiMessages, question, "sent");
        const pending = appendBubble(aiMessages, "Đang kiểm tra thông tin khách sạn…", "received");
        submit.disabled = true;
        try {
            const answer = await api.post("/ai/concierge/chat", { session_id: sessionId, message: question });
            if (!answer || typeof answer.reply !== "string" || !answer.reply.trim()) {
                throw new Error("Trợ lý chưa nhận được câu trả lời xác thực. Bạn có thể chuyển yêu cầu tới Lễ tân.");
            }
            pending.textContent = answer.reply;
            if (answer.escalated && answer.ticket_id) {
                aiFeedback.textContent = `Yêu cầu đã được ghi nhận cho Lễ tân. Mã tiếp nhận: ${answer.ticket_id}`;
                escalation.hidden = true;
            }
            if (Array.isArray(answer.citations) && answer.citations.length) {
                const citationList = answer.citations
                    .filter(citation => citation && typeof citation.doc === "string")
                    .map(citation => citation.section ? `${citation.doc} — ${citation.section}` : citation.doc);
                if (citationList.length) {
                    const citations = document.createElement("small");
                    citations.className = "ai-chat-citations";
                    citations.textContent = `Nguồn: ${citationList.join("; ")}`;
                    pending.appendChild(citations);
                }
            }
            aiMessages.scrollTop = aiMessages.scrollHeight;
            aiInput.value = "";
        } catch (error) {
            pending.textContent = error.message || "Trợ lý đang tạm thời không khả dụng.";
            escalation.hidden = false;
        } finally {
            aiBusy = false;
            submit.disabled = false;
            aiInput.focus();
        }
    });

    document.getElementById("ai-chat-escalate").addEventListener("click", async event => {
        if (!lastQuestion || aiBusy) return;
        const button = event.currentTarget;
        button.disabled = true;
        button.textContent = "Đang chuyển…";
        aiFeedback.textContent = "";
        try {
            const ticket = await api.post("/ai/concierge/escalate", {
                session_id: sessionId,
                reason: lastQuestion
            });
            aiFeedback.textContent = ticket?.message || `Yêu cầu đã được ghi nhận cho Lễ tân. Mã tiếp nhận: ${ticket?.ticket_id || ""}`;
            escalation.hidden = true;
        } catch (error) {
            aiFeedback.textContent = error.message || "Chưa thể chuyển yêu cầu. Vui lòng thử lại.";
        } finally {
            button.disabled = false;
            button.textContent = "Chuyển tới Lễ tân";
        }
    });

    const staffBubbles = document.getElementById("cust-chat-bubbles");
    const staffFeedback = document.getElementById("cust-chat-feedback");
    const loadStaffChat = async () => {
        if (staffLoading || !document.getElementById("cust-chat-bubbles")) return;
        staffLoading = true;
        try {
            const list = await api.get(`/chat/history?u1=${session.userId}&u2=1`);
            const signature = JSON.stringify(list || []);
            if (signature === lastMessages) {
                staffFeedback.textContent = "";
                return;
            }
            lastMessages = signature;
            staffBubbles.innerHTML = "";
            if (!list || list.length === 0) {
                staffBubbles.innerHTML = `
                    <div class="chat-empty-state">
                        <span class="chat-empty-icon"><i data-lucide="messages-square" aria-hidden="true"></i></span>
                        <strong>Bắt đầu cuộc trò chuyện</strong>
                        <span>Gửi câu hỏi của bạn, nhân viên lễ tân sẽ hỗ trợ sớm nhất.</span>
                    </div>`;
                if (window.lucide?.createIcons) window.lucide.createIcons();
                staffFeedback.textContent = "";
                return;
            }
            list.forEach(msg => appendBubble(staffBubbles, msg.content, msg.senderId === session.userId ? "sent" : "received"));
            staffFeedback.textContent = "";
        } catch (error) {
            staffFeedback.textContent = error.message || "Không thể tải tin nhắn. Vui lòng thử lại.";
        } finally {
            staffLoading = false;
        }
    };

    const staffForm = document.getElementById("cust-chat-form");
    staffForm.addEventListener("submit", async event => {
        event.preventDefault();
        if (staffSending) return;
        const input = document.getElementById("cust-chat-input");
        const message = input.value.trim();
        if (!message) return;

        const submit = staffForm.querySelector('button[type="submit"]');
        staffSending = true;
        submit.disabled = true;
        staffFeedback.textContent = "Đang gửi tin nhắn…";
        try {
            await api.post("/chat/send", {
                senderId: session.userId,
                receiverId: 1,
                content: message
            });
            input.value = "";
            staffFeedback.textContent = "Tin nhắn đã được gửi.";
            await loadStaffChat();
        } catch (error) {
            staffFeedback.textContent = error.message || "Không gửi được tin nhắn. Vui lòng thử lại.";
        } finally {
            staffSending = false;
            submit.disabled = false;
        }
    });

    const interval = setInterval(() => {
        if (!document.getElementById("cust-chat-bubbles")) {
            clearInterval(interval);
            return;
        }
        if (!document.getElementById("staff-concierge-panel").hidden) loadStaffChat();
    }, 5000);
}
