/**
 * TalentAI - Utility Functions & UI Helpers
 */
const Utils = {
  // Toast notifications
  showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ';
    toast.innerHTML = `<span style="font-weight:700;">${icon}</span><span>${Utils.escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 3500);
  },

  // Loading overlay
  setLoading(isLoading) {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
      overlay.style.display = isLoading ? 'flex' : 'none';
    }
  },

  // Date formatting
  formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      return isNaN(d.getTime()) ? dateStr : d.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    } catch (e) {
      return dateStr;
    }
  },

  // Escape HTML to prevent XSS
  escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  },

  // Proficiency Badge
  renderProficiencyBadge(level) {
    if (!level) return '<span class="badge badge-neutral">UNKNOWN</span>';
    const lvl = level.toUpperCase();
    let badgeClass = 'badge-neutral';
    if (lvl === 'EXPERT') badgeClass = 'badge-success';
    else if (lvl === 'ADVANCED') badgeClass = 'badge-info';
    else if (lvl === 'INTERMEDIATE') badgeClass = 'badge-primary';
    else if (lvl === 'BEGINNER') badgeClass = 'badge-warning';

    return `<span class="badge ${badgeClass}">${lvl}</span>`;
  },

  // Priority Badge
  renderPriorityBadge(priority) {
    if (!priority) return '<span class="badge badge-neutral">NORMAL</span>';
    const p = priority.toUpperCase();
    let badgeClass = 'badge-neutral';
    if (p === 'CRITICAL') badgeClass = 'badge-danger';
    else if (p === 'HIGH') badgeClass = 'badge-warning';
    else if (p === 'MEDIUM') badgeClass = 'badge-info';
    else if (p === 'LOW') badgeClass = 'badge-neutral';

    return `<span class="badge ${badgeClass}">${p}</span>`;
  },

  // Status Badge
  renderStatusBadge(status) {
    if (!status) return '<span class="badge badge-neutral">UNKNOWN</span>';
    const s = status.toUpperCase();
    let badgeClass = 'badge-neutral';
    let label = s;
    if (s === 'ACTIVE') {
      badgeClass = 'badge-success';
      label = 'APPROVED (Active)';
    } else if (s === 'APPROVED' || s === 'ALLOCATED') {
      badgeClass = 'badge-success';
    } else if (s === 'PENDING' || s === 'OPEN') {
      badgeClass = 'badge-warning';
    } else if (s === 'CANCELLED' || s === 'REJECTED') {
      badgeClass = 'badge-danger';
    }

    return `<span class="badge ${badgeClass}">${label}</span>`;
  },

  // Utilization Progress Bar
  renderUtilizationBar(allocatedPercent) {
    const pct = Math.min(Math.max(allocatedPercent || 0, 0), 100);
    let fillClass = 'util-low';
    if (pct > 90) fillClass = 'util-high';
    else if (pct > 60) fillClass = 'util-med';

    return `
      <div style="display:flex; align-items:center; gap:8px;">
        <div class="utilization-bar" style="flex:1;">
          <div class="utilization-fill ${fillClass}" style="width: ${pct}%;"></div>
        </div>
        <span style="font-size:12px; font-weight:600; min-width:35px; text-align:right;">${pct}%</span>
      </div>
    `;
  },

  // Score Bar
  renderScoreBar(score) {
    const s = Math.round(score || 0);
    const widthPct = Math.min(Math.max(s, 0), 100);
    return `
      <div class="score-bar">
        <div class="score-track">
          <div class="score-fill" style="width: ${widthPct}%;"></div>
        </div>
        <div class="score-value">${s}/100</div>
      </div>
    `;
  }
};
