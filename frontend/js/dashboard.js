/**
 * TalentAI - Dashboard Screen Module
 */
const Dashboard = {
  async init() {
    const container = document.getElementById('screen-dashboard');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">Resource Allocation Dashboard</h1>
          <p class="section-subtitle">Real-time capacity tracking, talent matching, and demand pipeline</p>
        </div>
        <div style="display:flex; gap:10px;">
          <button class="btn btn-secondary" id="btn-dash-refresh">🔄 Refresh</button>
          <button class="btn btn-primary" id="btn-dash-new-demand">➕ New Demand</button>
        </div>
      </div>

      <!-- Stat Cards Grid -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon">👥</div>
          <div class="stat-value" id="stat-total-emp">--</div>
          <div class="stat-label">Total Resources</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">📋</div>
          <div class="stat-value" id="stat-open-demands">--</div>
          <div class="stat-label">Open Demands</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">⚡</div>
          <div class="stat-value" id="stat-avg-util">--%</div>
          <div class="stat-label">Avg Utilization</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">⏳</div>
          <div class="stat-value" id="stat-pending-alloc">--</div>
          <div class="stat-label">Pending Allocations</div>
        </div>
      </div>

      <!-- Dashboard Grid: Demands & Skill Gaps -->
      <div class="dashboard-grid">
        <!-- Open Demands by Priority -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">🔥 High Priority Demands</h2>
            <button class="btn btn-sm btn-secondary" id="btn-dash-view-all-demands">View All</button>
          </div>
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Project & Role</th>
                  <th>Timeline</th>
                  <th>Priority</th>
                  <th>Required Skills</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody id="dash-demands-tbody">
                <tr><td colspan="5" class="loading-inline"><div class="spinner-sm"></div> Loading priority demands...</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Skill Gap Analytics -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">📊 Top Skill Gaps</h2>
          </div>
          <div id="dash-skill-gaps-container">
            <div class="loading-inline"><div class="spinner-sm"></div> Evaluating market gaps...</div>
          </div>
        </div>
      </div>

      <!-- Resource Utilization Overview -->
      <div class="card" style="margin-top: 24px;">
        <div class="card-header">
          <h2 class="card-title">💼 Team Capacity & Utilization Overview</h2>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Resource Name</th>
                <th>Department</th>
                <th>Allocation Load</th>
                <th>Available Capacity</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody id="dash-util-tbody">
              <tr><td colspan="5" class="loading-inline"><div class="spinner-sm"></div> Calculating utilization...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;

    document.getElementById('btn-dash-refresh')?.addEventListener('click', () => Dashboard.loadData());
    document.getElementById('btn-dash-new-demand')?.addEventListener('click', () => {
      App.navigateTo('demands');
      Demands.openCreateModal();
    });
    document.getElementById('btn-dash-view-all-demands')?.addEventListener('click', () => {
      App.navigateTo('demands');
    });

    await Dashboard.loadData();
  },

  async loadData() {
    try {
      // Parallel loading of backend endpoints
      const [employees, openDemands, allocations, utilReport, skillGaps] = await Promise.all([
        Api.getEmployees().catch(() => []),
        Api.getOpenDemandsByPriority().catch(() => []),
        Api.getAllocations().catch(() => []),
        Api.getUtilizationReport().catch(() => null),
        Api.getSkillGapReport().catch(() => null)
      ]);

      // Update stat cards
      const totalEmpElem = document.getElementById('stat-total-emp');
      const openDemandsElem = document.getElementById('stat-open-demands');
      const avgUtilElem = document.getElementById('stat-avg-util');
      const pendingAllocElem = document.getElementById('stat-pending-alloc');

      if (totalEmpElem) totalEmpElem.textContent = employees.length;
      if (openDemandsElem) openDemandsElem.textContent = openDemands.length;

      const pendingCount = allocations.filter(a => a.status === 'PENDING').length;
      if (pendingAllocElem) pendingAllocElem.textContent = pendingCount;

      const avgUtil = utilReport ? Math.round(utilReport.averageUtilization) : 0;
      if (avgUtilElem) avgUtilElem.textContent = `${avgUtil}%`;

      // Render Open Demands
      const demandsTbody = document.getElementById('dash-demands-tbody');
      if (demandsTbody) {
        if (!openDemands || openDemands.length === 0) {
          demandsTbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:var(--text-muted); padding:24px;">No open demands at the moment.</td></tr>`;
        } else {
          demandsTbody.innerHTML = openDemands.slice(0, 5).map(d => {
            const skillsHtml = (d.requiredSkills || []).map(s => 
              `<span class="skill-tag ${s.mandatory ? 'expert' : ''}">${Utils.escapeHtml(s.skillName)} (${s.minimumProficiency})</span>`
            ).join(' ') || '<span style="color:var(--text-muted); font-size:12px;">None specified</span>';

            return `
              <tr>
                <td>
                  <div style="font-weight:700;">${Utils.escapeHtml(d.projectName)}</div>
                  <div style="font-size:12px; color:var(--text-secondary);">${Utils.escapeHtml(d.role)} • ${d.allocationPercent}%</div>
                </td>
                <td style="font-size:12px;">
                  ${Utils.formatDate(d.startDate)} &rarr; ${Utils.formatDate(d.endDate)}
                </td>
                <td>${Utils.renderPriorityBadge(d.priority)}</td>
                <td>${skillsHtml}</td>
                <td>
                  <button class="btn btn-sm btn-primary" onclick="Compare.selectDemandAndCompare(${d.id})">
                    ⚡ Match
                  </button>
                </td>
              </tr>
            `;
          }).join('');
        }
      }

      // Render Skill Gaps
      const gapsContainer = document.getElementById('dash-skill-gaps-container');
      if (gapsContainer) {
        if (!skillGaps || !skillGaps.gaps || skillGaps.gaps.length === 0) {
          gapsContainer.innerHTML = `<div style="text-align:center; color:var(--text-muted); padding:32px;">No critical skill gaps identified.</div>`;
        } else {
          gapsContainer.innerHTML = `
            <div style="display:flex; flex-direction:column; gap:12px;">
              ${skillGaps.gaps.slice(0, 5).map(g => `
                <div style="background:var(--bg-glass); padding:12px; border-radius:8px; border:1px solid var(--border);">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                    <span style="font-weight:600; font-size:13px; color:var(--text-primary);">${Utils.escapeHtml(g.skillName)}</span>
                    <span class="badge badge-warning">Gap Score: ${g.gapScore}</span>
                  </div>
                  <div style="display:flex; justify-content:space-between; font-size:11px; color:var(--text-muted);">
                    <span>Demands requesting: <strong>${g.demandCount}</strong></span>
                    <span>Qualified resources: <strong>${g.employeeCount}</strong></span>
                  </div>
                </div>
              `).join('')}
            </div>
          `;
        }
      }

      // Render Utilization Overview
      const utilTbody = document.getElementById('dash-util-tbody');
      if (utilTbody) {
        if (!utilReport || !utilReport.employees || utilReport.employees.length === 0) {
          utilTbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:var(--text-muted); padding:24px;">No utilization data available.</td></tr>`;
        } else {
          utilTbody.innerHTML = utilReport.employees.slice(0, 6).map(e => `
            <tr>
              <td>
                <div style="font-weight:600;">${Utils.escapeHtml(e.employeeName)}</div>
              </td>
              <td>${Utils.escapeHtml(e.department || 'Engineering')}</td>
              <td style="width: 250px;">
                ${Utils.renderUtilizationBar(e.totalAllocatedPercent)}
              </td>
              <td><strong>${e.availablePercent}%</strong> capacity</td>
              <td>
                <span class="badge ${e.utilizationStatus === 'OVER' ? 'badge-danger' : e.utilizationStatus === 'OPTIMAL' ? 'badge-success' : 'badge-info'}">
                  ${e.utilizationStatus}
                </span>
              </td>
            </tr>
          `).join('');
        }
      }

    } catch (err) {
      console.error('Dashboard load error:', err);
      Utils.showToast('Failed to load full dashboard data', 'error');
    }
  }
};
