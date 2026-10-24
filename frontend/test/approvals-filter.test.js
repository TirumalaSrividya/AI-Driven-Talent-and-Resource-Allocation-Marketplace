/**
 * TalentAI - Approvals Status Filter & Badge Unit Tests
 * Tests that the APPROVED filter correctly displays ACTIVE allocations
 * and renders the expected badge and action buttons.
 */
const { test, describe } = require('node:test');
const assert = require('node:assert/strict');

// Utils.renderStatusBadge implementation from frontend/js/utils.js
const Utils = {
  renderStatusBadge(status) {
    if (!status) return '<span class="badge badge-neutral">UNKNOWN</span>';
    const s = status.toUpperCase();
    let badgeClass = 'badge-neutral';
    if (s === 'APPROVED' || s === 'ACTIVE' || s === 'ALLOCATED') badgeClass = 'badge-success';
    else if (s === 'PENDING' || s === 'OPEN') badgeClass = 'badge-warning';
    else if (s === 'CANCELLED' || s === 'REJECTED') badgeClass = 'badge-danger';

    return `<span class="badge ${badgeClass}">${s}</span>`;
  }
};

// Filter logic from frontend/js/approvals.js
function filterAllocations(allocations, statusFilter, query = '') {
  return allocations.filter(a => {
    if (statusFilter) {
      if (statusFilter === 'APPROVED') {
        if (a.status !== 'ACTIVE' && a.status !== 'APPROVED') return false;
      } else if (a.status !== statusFilter) {
        return false;
      }
    }
    if (query) {
      const q = query.toLowerCase();
      const matchesProj = (a.projectName || '').toLowerCase().includes(q);
      const matchesRole = (a.role || '').toLowerCase().includes(q);
      const matchesEmp = (a.employeeName || '').toLowerCase().includes(q);
      const matchesCode = (a.employeeCode || '').toLowerCase().includes(q);
      if (!matchesProj && !matchesRole && !matchesEmp && !matchesCode) return false;
    }
    return true;
  });
}

// Action button rendering check from frontend/js/approvals.js
function getActionButtons(allocation) {
  const isPending = allocation.status === 'PENDING';
  const isApproved = allocation.status === 'ACTIVE' || allocation.status === 'APPROVED';
  const isCancelled = allocation.status === 'CANCELLED';

  if (isPending) {
    return ['Approve Commitment', 'Reject / Cancel'];
  } else if (isApproved) {
    return ['Cancel & Release Capacity'];
  }
  return [];
}

describe('Approvals Filter & Lifecycle Display Tests', () => {
  const sampleAllocations = [
    {
      id: 1,
      employeeId: 1,
      employeeName: 'Alice Johnson',
      employeeCode: 'EMP001',
      demandId: 6,
      projectName: 'AI Customer Support Assistant',
      role: 'Backend Engineer',
      allocationPercent: 50,
      status: 'ACTIVE',
      approvedBy: 'Delivery Director'
    },
    {
      id: 3,
      employeeId: 2,
      employeeName: 'Bob Smith',
      employeeCode: 'EMP002',
      demandId: 2,
      projectName: 'DataVault Analytics',
      role: 'Full Stack Engineer',
      allocationPercent: 80,
      status: 'PENDING',
      approvedBy: null
    },
    {
      id: 4,
      employeeId: 3,
      employeeName: 'Carol Williams',
      employeeCode: 'EMP003',
      demandId: 3,
      projectName: 'CloudMigrate 360',
      role: 'DevOps Engineer',
      allocationPercent: 100,
      status: 'CANCELLED',
      approvedBy: null
    }
  ];

  test('APPROVED filter includes backend ACTIVE allocation', () => {
    const results = filterAllocations(sampleAllocations, 'APPROVED');
    assert.equal(results.length, 1);
    assert.equal(results[0].id, 1);
    assert.equal(results[0].status, 'ACTIVE');
    assert.equal(results[0].employeeName, 'Alice Johnson');
  });

  test('PENDING filter excludes ACTIVE allocation and includes PENDING', () => {
    const results = filterAllocations(sampleAllocations, 'PENDING');
    assert.equal(results.length, 1);
    assert.equal(results[0].id, 3);
    assert.equal(results[0].status, 'PENDING');
  });

  test('CANCELLED filter excludes ACTIVE and PENDING allocations', () => {
    const results = filterAllocations(sampleAllocations, 'CANCELLED');
    assert.equal(results.length, 1);
    assert.equal(results[0].id, 4);
    assert.equal(results[0].status, 'CANCELLED');
  });

  test('Empty status filter (All Statuses) returns all allocations', () => {
    const results = filterAllocations(sampleAllocations, '');
    assert.equal(results.length, 3);
  });

  test('ACTIVE allocation receives badge-success styling', () => {
    const badgeHtml = Utils.renderStatusBadge('ACTIVE');
    assert.ok(badgeHtml.includes('badge-success'), 'ACTIVE badge must have badge-success class');
    assert.ok(badgeHtml.includes('ACTIVE'), 'ACTIVE badge must display ACTIVE text');
  });

  test('ACTIVE allocation exposes Cancel & Release Capacity action button', () => {
    const actions = getActionButtons(sampleAllocations[0]);
    assert.deepEqual(actions, ['Cancel & Release Capacity']);
    assert.ok(!actions.includes('Approve Commitment'), 'Must not show Approve Commitment for already active allocation');
  });
});
