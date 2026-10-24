/**
 * TalentAI - Project Demands Screen Module
 */
const Demands = {
  allDemands: [],
  availableSkills: [],

  async init() {
    const container = document.getElementById('screen-demands');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">Project Resource Demands</h1>
          <p class="section-subtitle">Manage project staffing requirements, skill criteria, and timelines</p>
        </div>
        <button class="btn btn-primary" id="btn-create-demand-modal">➕ Create Demand</button>
      </div>

      <!-- Filters Bar -->
      <div class="card" style="margin-bottom: 24px; padding: 16px 20px;">
        <div style="display:flex; flex-wrap:wrap; gap:16px; align-items:center;">
          <div style="flex:1; min-width:200px;">
            <input type="text" id="demand-search" placeholder="Search by project or role..." style="margin:0;">
          </div>
          <div style="width:160px;">
            <select id="filter-demand-status" style="margin:0;">
              <option value="">All Statuses</option>
              <option value="OPEN">OPEN</option>
              <option value="ALLOCATED">ALLOCATED</option>
              <option value="CANCELLED">CANCELLED</option>
            </select>
          </div>
          <div style="width:160px;">
            <select id="filter-demand-priority" style="margin:0;">
              <option value="">All Priorities</option>
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="LOW">LOW</option>
            </select>
          </div>
          <button class="btn btn-secondary" id="btn-reset-demand-filters">Reset</button>
        </div>
      </div>

      <!-- Demands Table -->
      <div class="card">
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Demand ID</th>
                <th>Project & Role</th>
                <th>Timeline</th>
                <th>Location</th>
                <th>Load</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Required Skills</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="demands-table-body">
              <tr><td colspan="9" class="loading-inline"><div class="spinner-sm"></div> Loading demands...</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Create Demand Modal -->
      <div id="demand-modal" class="loading-overlay" style="display:none;">
        <div class="card" style="width: 100%; max-width: 650px; max-height: 90vh; overflow-y: auto; background: var(--bg-secondary);">
          <div class="card-header">
            <h2 class="card-title">➕ Create Project Demand</h2>
            <button class="btn btn-sm btn-secondary" id="btn-close-demand-modal">✕</button>
          </div>
          <form id="form-create-demand">
            <div class="form-grid">
              <div class="form-group">
                <label>Project Name <span class="required">*</span></label>
                <input type="text" id="input-proj-name" required placeholder="e.g. NextGen Core Cloud Banking">
              </div>
              <div class="form-group">
                <label>Role <span class="required">*</span></label>
                <input type="text" id="input-proj-role" required placeholder="e.g. Senior Backend Engineer">
              </div>
              <div class="form-group">
                <label>Start Date <span class="required">*</span></label>
                <input type="date" id="input-proj-start" required>
              </div>
              <div class="form-group">
                <label>End Date <span class="required">*</span></label>
                <input type="date" id="input-proj-end" required>
              </div>
              <div class="form-group">
                <label>Allocation % <span class="required">*</span></label>
                <input type="number" id="input-proj-alloc" min="1" max="100" value="100" required>
              </div>
              <div class="form-group">
                <label>Location Preference</label>
                <input type="text" id="input-proj-loc" placeholder="e.g. Bangalore, Remote, Hyderabad">
              </div>
              <div class="form-group">
                <label>Priority</label>
                <select id="input-proj-priority">
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="LOW">LOW</option>
                </select>
              </div>
              <div class="form-group">
                <label>Created By</label>
                <input type="text" id="input-proj-creator" value="Staffing Manager">
              </div>
              <div class="form-group full-width">
                <label>Description</label>
                <textarea id="input-proj-desc" placeholder="Scope, responsibilities, and architectural tech stack..."></textarea>
              </div>
            </div>

            <div class="divider"></div>

            <!-- Required Skills Selector -->
            <div>
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                <label style="margin:0;">Required Skills & Competencies</label>
                <button type="button" class="btn btn-sm btn-secondary" id="btn-add-skill-row">➕ Add Skill</button>
              </div>
              <div id="demand-skills-rows" style="display:flex; flex-direction:column; gap:8px;">
                <!-- Dynamic skill rows injected here -->
              </div>
            </div>

            <div style="display:flex; justify-content:flex-end; gap:12px; margin-top:24px;">
              <button type="button" class="btn btn-secondary" id="btn-cancel-demand">Cancel</button>
              <button type="submit" class="btn btn-primary">Save Demand</button>
            </div>
          </form>
        </div>
      </div>
    `;

    // Event handlers
    document.getElementById('btn-create-demand-modal')?.addEventListener('click', () => Demands.openCreateModal());
    document.getElementById('btn-close-demand-modal')?.addEventListener('click', () => Demands.closeCreateModal());
    document.getElementById('btn-cancel-demand')?.addEventListener('click', () => Demands.closeCreateModal());
    document.getElementById('btn-add-skill-row')?.addEventListener('click', () => Demands.addSkillRow());
    document.getElementById('form-create-demand')?.addEventListener('submit', (e) => Demands.handleSubmit(e));

    document.getElementById('demand-search')?.addEventListener('input', () => Demands.renderTable());
    document.getElementById('filter-demand-status')?.addEventListener('change', () => Demands.renderTable());
    document.getElementById('filter-demand-priority')?.addEventListener('change', () => Demands.renderTable());
    document.getElementById('btn-reset-demand-filters')?.addEventListener('click', () => {
      document.getElementById('demand-search').value = '';
      document.getElementById('filter-demand-status').value = '';
      document.getElementById('filter-demand-priority').value = '';
      Demands.renderTable();
    });

    await Demands.loadData();
  },

  async loadData() {
    try {
      const [demands, skills] = await Promise.all([
        Api.getDemands().catch(() => []),
        Api.getSkills().catch(() => [])
      ]);
      Demands.allDemands = demands || [];
      Demands.availableSkills = skills || [];
      Demands.renderTable();
    } catch (err) {
      console.error('Failed to load demands:', err);
      Utils.showToast('Failed to load demands list', 'error');
    }
  },

  renderTable() {
    const tbody = document.getElementById('demands-table-body');
    if (!tbody) return;

    const query = (document.getElementById('demand-search')?.value || '').toLowerCase();
    const statusFilter = document.getElementById('filter-demand-status')?.value || '';
    const priorityFilter = document.getElementById('filter-demand-priority')?.value || '';

    let filtered = Demands.allDemands.filter(d => {
      if (statusFilter && d.status !== statusFilter) return false;
      if (priorityFilter && d.priority !== priorityFilter) return false;
      if (query) {
        const matchesProj = (d.projectName || '').toLowerCase().includes(query);
        const matchesRole = (d.role || '').toLowerCase().includes(query);
        const matchesLoc = (d.location || '').toLowerCase().includes(query);
        if (!matchesProj && !matchesRole && !matchesLoc) return false;
      }
      return true;
    });

    if (filtered.length === 0) {
      tbody.innerHTML = `<tr><td colspan="9" style="text-align:center; color:var(--text-muted); padding:32px;">No demands match the selected criteria.</td></tr>`;
      return;
    }

    tbody.innerHTML = filtered.map(d => {
      const skillsHtml = (d.requiredSkills || []).map(s => 
        `<span class="skill-tag ${s.mandatory ? 'expert' : ''}" title="${s.mandatory ? 'Mandatory' : 'Preferred'}">
          ${Utils.escapeHtml(s.skillName)} (${s.minimumProficiency})${s.mandatory ? ' *' : ''}
        </span>`
      ).join(' ') || '<span style="color:var(--text-muted); font-size:12px;">None</span>';

      return `
        <tr>
          <td><span style="font-family:monospace; color:var(--accent-primary); font-weight:700;">#${d.id}</span></td>
          <td>
            <div style="font-weight:700; color:var(--text-primary);">${Utils.escapeHtml(d.projectName)}</div>
            <div style="font-size:12px; color:var(--text-secondary);">${Utils.escapeHtml(d.role)}</div>
          </td>
          <td style="font-size:12px; white-space:nowrap;">
            ${Utils.formatDate(d.startDate)} &rarr;<br>${Utils.formatDate(d.endDate)}
          </td>
          <td style="font-size:13px;">${Utils.escapeHtml(d.location || 'Any')}</td>
          <td style="font-weight:600;">${d.allocationPercent}%</td>
          <td>${Utils.renderPriorityBadge(d.priority)}</td>
          <td>${Utils.renderStatusBadge(d.status)}</td>
          <td style="max-width:280px;">${skillsHtml}</td>
          <td>
            <div style="display:flex; gap:6px; align-items:center;">
              <button class="btn btn-sm btn-primary" onclick="Compare.selectDemandAndCompare(${d.id})" title="Score & Match Candidates">
                ⚡ Match
              </button>
              <button class="btn btn-sm btn-secondary" onclick="AiAssistant.consultDemand(${d.id})" title="Consult AI Agent">
                🤖 AI
              </button>
              <button class="btn btn-sm btn-danger" onclick="Demands.handleDeleteDemand(${d.id}, '${Utils.escapeHtml(d.projectName).replace(/'/g, "\\'")}')" title="Delete Demand" style="padding:6px 10px; font-size:12px;">
                🗑️
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  },

  openCreateModal() {
    const modal = document.getElementById('demand-modal');
    if (!modal) return;
    modal.style.display = 'flex';

    // Set default dates: today to +3 months
    const today = new Date();
    const future = new Date();
    future.setMonth(future.getMonth() + 3);

    const startInput = document.getElementById('input-proj-start');
    const endInput = document.getElementById('input-proj-end');
    if (startInput) startInput.value = today.toISOString().split('T')[0];
    if (endInput) endInput.value = future.toISOString().split('T')[0];

    const container = document.getElementById('demand-skills-rows');
    if (container && container.children.length === 0) {
      Demands.addSkillRow();
    }
  },

  closeCreateModal() {
    const modal = document.getElementById('demand-modal');
    if (modal) modal.style.display = 'none';
  },

  addSkillRow() {
    const container = document.getElementById('demand-skills-rows');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'demand-skill-item';
    row.style.cssText = 'display:flex; gap:10px; align-items:center; background:var(--bg-glass); padding:8px 12px; border-radius:6px;';

    const skillOptions = Demands.availableSkills.map(s => 
      `<option value="${s.id}">${Utils.escapeHtml(s.name)} (${Utils.escapeHtml(s.category)})</option>`
    ).join('');

    row.innerHTML = `
      <select class="row-skill-id" style="flex:2; margin:0;" required>
        <option value="">-- Select Skill --</option>
        ${skillOptions}
      </select>
      <select class="row-proficiency" style="flex:1.5; margin:0;">
        <option value="INTERMEDIATE">INTERMEDIATE</option>
        <option value="ADVANCED">ADVANCED</option>
        <option value="EXPERT">EXPERT</option>
        <option value="BEGINNER">BEGINNER</option>
      </select>
      <label style="display:flex; align-items:center; gap:4px; font-size:12px; margin:0; cursor:pointer;">
        <input type="checkbox" class="row-mandatory" checked style="width:auto; margin:0;"> Mandatory
      </label>
      <button type="button" class="btn btn-sm btn-danger" style="padding:4px 8px;" onclick="this.parentElement.remove()">✕</button>
    `;

    container.appendChild(row);
  },

  async handleSubmit(e) {
    e.preventDefault();
    Utils.setLoading(true);

    try {
      const projectName = document.getElementById('input-proj-name').value.trim();
      const role = document.getElementById('input-proj-role').value.trim();
      const startDate = document.getElementById('input-proj-start').value;
      const endDate = document.getElementById('input-proj-end').value;
      const allocationPercent = parseInt(document.getElementById('input-proj-alloc').value, 10);
      const location = document.getElementById('input-proj-loc').value.trim();
      const priority = document.getElementById('input-proj-priority').value;
      const createdBy = document.getElementById('input-proj-creator').value.trim() || 'Manager';
      const description = document.getElementById('input-proj-desc').value.trim();

      // Collect required skills
      const skillRows = document.querySelectorAll('#demand-skills-rows .demand-skill-item');
      const requiredSkills = [];

      skillRows.forEach(r => {
        const skillId = r.querySelector('.row-skill-id')?.value;
        const minimumProficiency = r.querySelector('.row-proficiency')?.value;
        const mandatory = r.querySelector('.row-mandatory')?.checked ?? true;

        if (skillId) {
          requiredSkills.push({
            skillId: parseInt(skillId, 10),
            minimumProficiency,
            mandatory
          });
        }
      });

      const payload = {
        projectName,
        role,
        startDate,
        endDate,
        allocationPercent,
        location,
        priority,
        createdBy,
        description,
        requiredSkills
      };

      const created = await Api.createDemand(payload);
      Utils.showToast(`Demand #${created.id} created successfully!`, 'success');
      Demands.closeCreateModal();
      document.getElementById('form-create-demand').reset();
      document.getElementById('demand-skills-rows').innerHTML = '';
      await Demands.loadData();
    } catch (err) {
      console.error('Failed to create demand:', err);
      Utils.showToast(err.message || 'Error creating demand', 'error');
    } finally {
      Utils.setLoading(false);
    }
  },

  async handleDeleteDemand(id, projectName) {
    const confirmed = confirm(`Are you sure you want to delete demand #${id} ("${projectName}")?`);
    if (!confirmed) return;

    try {
      Utils.setLoading(true);
      await Api.deleteDemand(id);
      Utils.showToast(`Demand #${id} deleted successfully`, 'success');
      await Demands.loadData();
    } catch (err) {
      console.error('Failed to delete demand:', err);
      Utils.showToast(`Failed to delete demand: ${err.message || 'Error'}`, 'error');
    } finally {
      Utils.setLoading(false);
    }
  }
};
