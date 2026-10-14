// Admin Views Module Coordinator
import { renderDashboard } from "./admin/dashboard.js";
import { renderRooms } from "./admin/rooms.js";
import { renderUsers } from "./admin/users.js";
import { renderPromotions } from "./admin/promotions.js";
import { renderReviews } from "./admin/reviews.js";
import { renderInvoices } from "./admin/invoices.js";

export function loadAdminView(tab = "dashboard") {
    const container = document.getElementById("view-container");
    const session = JSON.parse(localStorage.getItem("hotel_auth_session"));
    
    if (tab === "dashboard") {
        renderDashboard(container, session);
    } else if (tab === "rooms") {
        renderRooms(container, session);
    } else if (tab === "users") {
        renderUsers(container, session);
    } else if (tab === "promotions") {
        renderPromotions(container, session);
    } else if (tab === "reviews") {
        renderReviews(container, session);
    } else if (tab === "invoices") {
        renderInvoices(container, session);
    }
}
