// Admin Views Module Coordinator
import { renderDashboard } from "./admin/dashboard.js?v=3.6.4";
import { renderRooms } from "./admin/rooms.js?v=3.6.4";
import { renderUsers } from "./admin/users.js?v=3.6.4";
import { renderPromotions } from "./admin/promotions.js?v=3.6.4";
import { renderReviews } from "./admin/reviews.js?v=3.6.4";
import { renderInvoices } from "./admin/invoices.js?v=3.6.4";
import { renderAdminCustomers } from "./admin/customers.js?v=3.6.4";
import { renderMembershipPlaceholder } from "./admin/loyalty.js";

export function loadAdminView(tab = "dashboard") {
    const container = document.getElementById("view-container");
    const session = JSON.parse(localStorage.getItem("hotel_auth_session"));
    
    if (tab === "dashboard") {
        renderDashboard(container, session);
    } else if (tab === "rooms") {
        renderRooms(container, session);
    } else if (tab === "users") {
        renderUsers(container, session);
    } else if (tab === "customers") {
        renderAdminCustomers(container, session);
    } else if (tab === "loyalty") {
        renderMembershipPlaceholder(container);
    } else if (tab === "promotions") {
        renderPromotions(container, session);
    } else if (tab === "reviews") {
        renderReviews(container, session);
    } else if (tab === "invoices") {
        renderInvoices(container, session);
    }
}
