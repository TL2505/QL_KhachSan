import { getAuthSession } from '../api.js';
import { renderAccountSettings } from './shared/account-settings.js?v=2';
export function loadProfileView() {
    return renderAccountSettings(document.getElementById('view-container'), getAuthSession());
}
