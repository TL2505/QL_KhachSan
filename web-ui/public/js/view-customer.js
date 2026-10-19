// Customer Views Module Coordinator
import { renderDashboard, renderBookings, renderInvoices } from "./customer/dashboard.js";
import { renderDiscovery } from "./customer/discovery.js";
import { renderLoyalty } from "./customer/loyalty.js";
import { renderPromotions } from "./customer/promotions.js";
import { renderChat } from "./customer/chat.js";

export function loadCustomerView(tab = "dashboard") {
    const container = document.getElementById("view-container");
    const session = JSON.parse(localStorage.getItem("hotel_auth_session"));
    
    if (tab === "dashboard") {
        renderDashboard(container, session);
    } else if (tab === "discovery") {
        renderDiscovery(container, session);
    } else if (tab === "loyalty") {
        renderLoyalty(container, session);
    } else if (tab === "promotions") {
        renderPromotions(container, session);
    } else if (tab === "chat") {
        renderChat(container, session);
    } else if (tab === "bookings") {
        renderBookings(container);
    } else if (tab === "invoices") {
        renderInvoices(container);
    }
}
