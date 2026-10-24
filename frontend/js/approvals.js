/**
 * TalentAI - Allocations & Approvals Screen Module
 * Manages allocation lifecycle: Pending, Approval (with revalidation), and Cancellation
 */
const Approvals = {
  allAllocations: [],
  selectedAllocId: null,

  async init() {
    const container = document.getElementById('screen-approvals');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">Resource Allocations & Approval Gate</h1>
          <p class="section-subtitle">Review staffing requests, authorize commitments, and manage allocation lifecycle</p>
        </div>
        <button class="btn btn-secondary" id="btn-refresh-approvals">🔄 Refresh</button>
      </div>

      <!-- Filter Controls -->
      <div class="card" style="margin-bottom: 24px; padding: 16px 20px;">
        <div style="display:flex; flex-wrap:wrap; gap:16px; align-items:center;">
          <div style="flex:1; min-width:200px;">
            <input type="text" id="alloc-search" placeholder="Search by resource, project, or role..." style="margin:0;">
          </div>
          <div style="width:160px;">
            <select id="filter-alloc-status" style="margin:0;">
              <option value="">All Statuses</option>
              <option value="PENDING" selected>PENDING (Needs Action)</option>
              <option value="APPROVED">APPROVED (Active)</option>
              <option value="CANCELLED">CANCELLED</option>
            </select>
          </div>
          <button class="btn btn-secondary" id="btn-reset-alloc-filters">Reset</button>
        </div>
      </div>

      <!-- Allocations List -->
      <div id="allocations-list-container">
        <div class="loading-inline"><div class="spinner-sm"></div> Loading allocation records...</div>
      </div>

      <!-- Cancel Modal -->
      <div id="cancel-alloc-modal" class="loading-overlay" style="display:none;">
        <div class="card" style="width: 100%; max-width: 480px; background: var(--bg-secondary);">
          <div class="card-header">
            <h2 class="card-title">⚠️ Cancel Resource Allocation</h2>
            <button class="btn btn-sm btn-secondary" id="btn-close-cancel-modal">✕</button>
          </div>
          <form id="form-cancel-alloc">
            <p style="font-size:13px; color:var(--text-secondary); margin-bottom:16px;">
              Cancelling will release the allocated capacity back to the resource while preserving complete audit history.
            </p>
            <div class="form-group" style="margin-bottom:14px;">
              <label>Cancelled By <span class="required">*</span></label>
              <input type="text" id="input-cancel-by" value="Delivery Head" required>
            </div>
            <div class="form-group" style="margin-bottom:20px;">
              <label>Cancellation Reason <span class="required">*</span></label>
              <textarea id="input-cancel-reason" required placeholder="e.g. Project scope reduced, client timeline postponed..."></textarea>
            </div>
            <div style="display:flex; justify-content:flex-end; gap:12px;">
              <button type="button" class="btn btn-secondary" id="btn-cancel-cancel-modal">Back</button>
              <button type="submit" class="btn btn-danger">Confirm Cancellation</button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.getElementById('btn-refresh-approvals')?.addEventListener('click', () => Approvals.loadData());
    document.getElementById('alloc-search')?.addEventListener('input', () => Approvals.renderList());
    document.getElementById('filter-alloc-status')?.addEventListener('change', () => Approvals.renderList());
    document.getElementById('btn-reset-alloc-filters')?.addEventListener('click', () => {
      document.getElementById('alloc-search').value = '';
      document.getElementById('filter-alloc-status').value = '';
      Approvals.renderList();
    });

    // Cancel modal
    document.getElementById('btn-close-cancel-modal')?.addEventListener('click', () => {
      document.getElementById('cancel-alloc-modal').style.display = 'none';
    });
    document.getElementById('btn-cancel-cancel-modal')?.addEventListener('click', () => {
      document.getElementById('cancel-alloc-modal').style.display = 'none';
    });
    document.getElementById('form-cancel-alloc')?.addEventListener('submit', (e) => Approvals.handleConfirmCancel(e));

    await Approvals.loadData();
  },

  async loadData() {
    try {
      const list = await Api.getAllocations();
      Approvals.allAllocations = list || [];
      Approvals.renderList();
    } catch (err) {
      console.error('Failed to load allocations:', err);
      Utils.showToast('Failed to load allocations', 'error');
    }
  },

  renderList() {
    const container = document.getElementById('allocations-list-container');
    if (!container) return;

    const query = (document.getElementById('alloc-search')?.value || '').toLowerCase();
    const statusFilter = document.getElementById('filter-alloc-status')?.value || '';

    let filtered = Approvals.allAllocations.filter(a => {
      if (statusFilter) {
        if (statusFilter === 'APPROVED') {
          if (a.status !== 'ACTIVE' && a.status !== 'APPROVED') return false;
        } else if (a.status !== statusFilter) {
          return false;
        }
      }
      if (query) {
        const matchesProj = (a.projectName || '').toLowerCase().includes(query);
        const matchesRole = (a.role || '').toLowerCase().includes(query);
        const matchesEmp = (a.employeeName || '').toLowerCase().includes(query);
        const matchesCode = (a.employeeCode || '').toLowerCase().includes(query);
        if (!matchesProj && !matchesRole && !matchesEmp && !matchesCode) return false;
      }
      return true;
    });

    if (filtered.length === 0) {
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-icon">📋</div>
          <h3>No Allocations Found</h3>
          <p>There are no allocation requests matching the current filter parameters.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div style="display:flex; flex-direction:column; gap:16px;">
        ${filtered.map(a => {
          const isPending = a.status === 'PENDING';
          const isApproved = a.status === 'ACTIVE' || a.status === 'APPROVED';
          const isCancelled = a.status === 'CANCELLED';

          return `
            <div class="approval-card">
              <div class="approval-header">
                <div>
                  <div style="display:flex; align-items:center; gap:10px; margin-bottom:6px;">
                    <span style="font-size:18px; font-weight:800; color:var(--text-primary);">${Utils.escapeHtml(a.projectName)}</span>
                    <span style="font-size:13px; color:var(--text-secondary);">&bull; ${Utils.escapeHtml(a.role)}</span>
                    ${Utils.renderStatusBadge(a.status)}
                  </div>
                  <div style="font-size:13px; color:var(--text-secondary);">
                    Resource: <strong style="color:var(--text-primary);">${Utils.escapeHtml(a.employeeName)}</strong>
                    (<span style="font-family:monospace;">${a.employeeCode}</span>) &bull; Demand ID: <strong>#${a.demandId}</strong>
                  </div>
                </div>

                <!-- Action buttons -->
                <div class="approval-actions">
                  ${isPending ? `
                    <button class="btn btn-sm btn-success" onclick="Approvals.handleApprove(${a.id})">
                      ✓ Approve Commitment
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="Approvals.openCancelModal(${a.id})">
                      ✕ Reject / Cancel
                    </button>
                  ` : isApproved ? `
                    <button class="btn btn-sm btn-secondary" onclick="Approvals.openCancelModal(${a.id})" style="color:var(--accent-red);">
                      Cancel & Release Capacity
                    </button>
                  ` : ''}
                </div>
              </div>

              <!-- Allocation details grid -->
              <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(180px, 1fr)); gap:14px; background:var(--bg-glass); padding:14px; border-radius:8px; font-size:12px;">
                <div>
                  <span style="color:var(--text-muted); display:block; margin-bottom:2px;">TIMELINE:</span>
                  <span style="font-weight:600;">${Utils.formatDate(a.startDate)} &rarr; ${Utils.formatDate(a.endDate)}</span>
                </div>
                <div>
                  <span style="color:var(--text-muted); display:block; margin-bottom:2px;">COMMITTED LOAD:</span>
                  <span style="font-weight:700; color:var(--accent-primary); font-size:14px;">${a.allocationPercent}%</span>
                </div>
                <div>
                  <span style="color:var(--text-muted); display:block; margin-bottom:2px;">CREATED:</span>
                  <span>${Utils.formatDate(a.createdAt)}</span>
                </div>
                <div>
                  <span style="color:var(--text-muted); display:block; margin-bottom:2px;">AUDIT TRAIL:</span>
                  ${a.approvedBy ? `
                    <span style="color:var(--accent-green);">Approved by: ${Utils.escapeHtml(a.approvedBy)}</span>
                  ` : a.cancelledBy ? `
                    <span style="color:var(--accent-red);">Cancelled by: ${Utils.escapeHtml(a.cancelledBy)}</span>
                  ` : `
                    <span style="color:var(--accent-orange);">Pending Manager Approval</span>
                  `}
                </div>
              </div>

              ${a.cancellationReason ? `
                <div style="margin-top:10px; font-size:12px; color:var(--text-muted); background:rgba(239,68,68,0.06); padding:8px 12px; border-radius:6px; border-left:3px solid var(--accent-red);">
                  <strong>Cancellation Note:</strong> ${Utils.escapeHtml(a.cancellationReason)}
                </div>
              ` : ''}
            </div>
          `;
        }).join('')}
      </div>
    `;
  },

  async handleApprove(allocationId) {
    const approver = prompt('Enter your name / role to authorize this allocation commitment:', 'Delivery Director');
    if (!approver) return;

    Utils.setLoading(true);
    try {
      const updated = await Api.approveAllocation(allocationId, approver);
      Utils.showToast(`Allocation #${updated.id} approved successfully!`, 'success');
      await Approvals.loadData();
    } catch (err) {
      console.error('Failed to approve allocation:', err);
      Utils.showToast(err.message || 'Approval failed (capacity conflict or invalid state)', 'error');
    } finally {
      Utils.setLoading(false);
    }
  },

  openCancelModal(allocationId) {
    Approvals.selectedAllocId = allocationId;
    const modal = document.getElementById('cancel-alloc-modal');
    if (modal) modal.style.display = 'flex';
  },

  async handleConfirmCancel(e) {
    e.preventDefault();
    if (!Approvals.selectedAllocId) return;

    Utils.setLoading(true);
    try {
      const cancelledBy = document.getElementById('input-cancel-by').value.trim() || 'Delivery Head';
      const reason = document.getElementById('input-cancel-reason').value.trim();

      const result = await Api.cancelAllocation(Approvals.selectedAllocId, cancelledBy, reason);
      Utils.showToast(`Allocation #${result.id} cancelled. Capacity released!`, 'success');
      document.getElementById('cancel-alloc-modal').style.display = 'none';
      document.getElementById('form-cancel-alloc').reset();
      await Approvals.loadData();
    } catch (err) {
      console.error('Failed to cancel allocation:', err);
      Utils.showToast(err.message || 'Cancellation failed', 'error');
    } finally {
      Utils.setLoading(false);
    }
  }
};
