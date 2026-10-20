// Customer Views Module Coordinator
import { renderDashboard } from "./customer/dashboard.js?v=3.6.18";
import { renderBookings } from "./customer/bookings.js";
import { renderProfile } from "./customer/profile.js";
import { renderSecurity } from "./customer/security.js";
import { renderDiscovery } from "./customer/discovery.js";
import { renderLoyalty } from "./customer/loyalty.js";
import { renderPromotions } from "./customer/promotions.js";
import { renderChat } from "./customer/chat.js";
import { renderConcierge } from "./customer/concierge.js";
import { api } from "../api.js";
import { getLegacyTierInfo, summarizeLoyaltyHistory } from "./customer/tier-helper.js";

export async function loadCustomerView(tab = "dashboard") {
  const container = document.getElementById("view-container");
  if (!container) return;

  let session = null;
  try {
    session = JSON.parse(localStorage.getItem("hotel_auth_session"));
  } catch {}

  if (!session) {
    session = {
      token: (typeof localStorage !== 'undefined' ? localStorage.getItem("auth_token") : null) || "customer-token",
      role: "CUSTOMER",
      userId: 1,
      username: "member",
      fullName: "Quý Hội Viên"
    };
  }

  // Load the member tier for every customer view so shared panels keep the
  // correct accent even when the customer opens a page other than dashboard.
  if (tab !== "dashboard") {
    document.body.dataset.customerTier = "member";
    const customerId = session.customerId || session.userId || session.id || 1;
    try {
      const history = await api.get(`/loyalty/history/${customerId}`);
      const { totalPoints: points } = summarizeLoyaltyHistory(history);
      document.body.dataset.customerTier = getLegacyTierInfo(Math.max(0, points)).name.toLowerCase();
    } catch {
      // Keep the neutral member palette when loyalty data is unavailable.
    }
  }

  try {
    if (tab === "dashboard") {
      await renderDashboard(container, session);
    } else if (tab === "bookings") {
      await renderBookings(container, session);
    } else if (tab === "concierge") {
      await renderConcierge(container, session);
    } else if (tab === "profile") {
      await renderProfile(container, session);
    } else if (tab === "security") {
      await renderSecurity(container, session);
    } else if (tab === "discovery") {
      await renderDiscovery(container, session);
    } else if (tab === "loyalty") {
      await renderLoyalty(container, session);
    } else if (tab === "promotions") {
      await renderPromotions(container, session);
    } else if (tab === "chat") {
      await renderChat(container, session);
    } else {
      await renderDashboard(container, session);
    }
  } catch (error) {
    console.error(`Lỗi render tab ${tab}:`, error);
    container.innerHTML = `
      <div class="alert alert-error bg-rose-500/10 border border-rose-500/30 text-rose-300 p-6 rounded-2xl">
        <h3 class="font-bold text-lg mb-2">Không thể tải nội dung</h3>
        <p class="text-sm">${error.message || 'Đã có sự cố khi kết nối máy chủ.'}</p>
      </div>
    `;
  }
}
