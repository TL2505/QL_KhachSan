import { getAuthSession } from '../api.js';
import { renderHousekeepingQueue } from './housekeeping/queue.js?v=1.1.1';

export function loadHousekeepingView() {
  const container = document.getElementById('view-container');
  renderHousekeepingQueue(container, getAuthSession());
}
