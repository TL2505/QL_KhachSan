// Admin Views Module Coordinator
import { renderDashboard } from "./admin/dashboard.js?v=3.6.4";
import { renderRooms } from "./admin/rooms.js?v=3.6.4";
import { renderBookings } from "./staff/bookings.js";
import { renderUsers } from "./admin/users.js?v=3.6.4";
import { renderPromotions } from "./admin/promotions.js?v=3.6.4";
import { renderReviews } from "./admin/reviews.js?v=3.6.4";
import { renderInvoices } from "./admin/invoices.js?v=3.6.4";
import { renderAdminCustomers } from "./admin/customers.js?v=3.6.4";
import { renderAdminMembership } from "./admin/loyalty.js?v=3.6.21";
import { renderAiSettings } from "./admin/ai-settings.js?v=7";
import { renderPaymentOperations } from './shared/payment-operations.js';
import { renderRentalPolicy } from './admin/rental-policy.js';
import { renderCatalogContent } from './admin/catalog-content.js?v=1';

export function loadAdminView(tab = "dashboard") {
    const container = document.getElementById("view-container");
    const session = JSON.parse(localStorage.getItem("hotel_auth_session"));
    
    if (tab === 'payment-operations') {
        renderPaymentOperations(container, session);
    } else if (tab === "rental-policy") {
        renderRentalPolicy(container);
    } else if (tab === "catalog-content") {
        renderCatalogContent(container, session);
    } else if (tab === "ai-settings") {
        renderAiSettings(container, session);
    } else if (tab === "dashboard") {
        renderDashboard(container, session);
    } else if (tab === "rooms") {
        renderRooms(container, session);
    } else if (tab === "bookings") {
        renderBookings(container, session);
    } else if (tab === "users") {
        renderUsers(container, session);
    } else if (tab === "customers") {
        renderAdminCustomers(container, session);
    } else if (tab === "loyalty") {
        renderAdminMembership(container, session);
    } else if (tab === "promotions") {
        renderPromotions(container, session);
    } else if (tab === "reviews") {
        renderReviews(container, session);
    } else if (tab === "invoices") {
        renderInvoices(container, session);
    }
}
