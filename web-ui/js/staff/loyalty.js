import { renderAdminMembership } from '../admin/loyalty.js';

export function renderLoyalty(container) {
    return renderAdminMembership(container, { readOnly: true });
}
