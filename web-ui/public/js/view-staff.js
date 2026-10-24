// Staff Views Module Coordinator
import { renderRoomMap } from "./staff/room-map.js";
import { renderBookings } from "./staff/bookings.js?v=3.6.51";
import { renderCustomers } from "./staff/customers.js";
import { renderInvoices } from "./staff/invoices.js";
import { renderCheckout } from "./staff/checkout.js";
import { renderLoyalty } from "./staff/loyalty.js";
import { renderChat } from "./staff/chat.js";
import { renderFrontDesk } from "./staff/front-desk.js?v=3.6.62";
import { renderPaymentOperations } from './shared/payment-operations.js?v=3.6.66';

export function loadStaffView(tab = "room-map") {
    const container = document.getElementById("view-container");
    const session = JSON.parse(localStorage.getItem("hotel_auth_session"));
    
    if (tab === 'payment-operations') {
        renderPaymentOperations(container, session);
    } else if (tab === "room-map") {
        renderRoomMap(container, session);
    } else if (tab === "front-desk") {
        renderFrontDesk(container, session);
    } else if (tab === "bookings") {
        renderBookings(container, session);
    } else if (tab === "customers") {
        renderCustomers(container, session);
    } else if (tab === "invoices") {
        renderInvoices(container, session);
    } else if (tab === "checkout") {
        renderCheckout(container, session);
    } else if (tab === "loyalty") {
        renderLoyalty(container, session);
    } else if (tab === "chat") {
        renderChat(container, session);
    }
}
