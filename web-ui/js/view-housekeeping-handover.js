import { getAuthSession } from '../api.js';
import { renderHousekeepingHandover } from './housekeeping/handover.js?v=1.0.0';

export function loadHousekeepingHandoverView() {
  const container = document.getElementById('view-container');
  renderHousekeepingHandover(container, getAuthSession());
}
