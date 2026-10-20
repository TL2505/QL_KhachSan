import test from 'node:test';
import assert from 'node:assert/strict';
import { api } from '../public/api.js';
import { renderDashboard } from '../public/js/admin/dashboard.js';

test('late dashboard results do not write into a view removed by navigation', async () => {
  const originalGet = api.get;
  let finish;
  let calls = 0;
  const nodes = new Map(['#stat-revenue','#stat-occupied','#stat-pending','#revenue-chart'].map(id => [id,{textContent:'unchanged'}]));
  const container = {innerHTML:'',querySelector:id=>nodes.get(id),contains:()=>false};
  try {
    api.get = () => { calls++; return new Promise(resolve => { finish = resolve; }); };
    const rendering = renderDashboard(container, {});
    finish({revenueToday:100,occupiedRooms:2,pendingCheckIns:1});
    await rendering;
    assert.equal(calls, 1, 'Removed dashboards must not request their chart');
    for (const node of nodes.values()) assert.equal(node.textContent,'unchanged');
  } finally { api.get = originalGet; }
});
