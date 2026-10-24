/**
 * TalentAI - Employees & Skills Screen Module
 */
const Employees = {
  allEmployees: [],
  availableSkills: [],
  currentSelectedEmployeeId: null,

  async init() {
    const container = document.getElementById('screen-employees');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">Resource Directory & Skills Matrix</h1>
          <p class="section-subtitle">Manage internal talent profiles, certified skills, and workload allocations</p>
        </div>
        <div style="display:flex; gap:10px;">
          <button class="btn btn-primary" id="btn-create-emp-modal">➕ Add Employee</button>
        </div>
      </div>

      <!-- Filters Bar -->
      <div class="card" style="margin-bottom: 24px; padding: 16px 20px;">
        <div style="display:flex; flex-wrap:wrap; gap:16px; align-items:center;">
          <div style="flex:1; min-width:200px;">
            <input type="text" id="emp-search" placeholder="Search by name, code, skill, or location..." style="margin:0;">
          </div>
          <div style="width:160px;">
            <select id="filter-emp-dept" style="margin:0;">
              <option value="">All Departments</option>
              <option value="Backend Engineering">Backend Engineering</option>
              <option value="Frontend Engineering">Frontend Engineering</option>
              <option value="Cloud Architecture">Cloud Architecture</option>
              <option value="Data & Analytics">Data & Analytics</option>
              <option value="Mobile Engineering">Mobile Engineering</option>
            </select>
          </div>
          <div style="width:160px;">
            <select id="filter-emp-status" style="margin:0;">
              <option value="">All Statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="ON_LEAVE">ON_LEAVE</option>
            </select>
          </div>
          <button class="btn btn-secondary" id="btn-reset-emp-filters">Reset</button>
        </div>
      </div>

      <!-- Employee Cards Grid -->
      <div class="skill-matrix-grid" id="emp-cards-container">
        <div class="loading-inline" style="grid-column: 1 / -1;"><div class="spinner-sm"></div> Loading employee directory...</div>
      </div>

      <!-- Add Employee Modal -->
      <div id="emp-modal" class="loading-overlay" style="display:none;">
        <div class="card" style="width: 100%; max-width: 550px; background: var(--bg-secondary);">
          <div class="card-header">
            <h2 class="card-title">➕ Add New Resource</h2>
            <button class="btn btn-sm btn-secondary" id="btn-close-emp-modal">✕</button>
          </div>
          <form id="form-create-emp">
            <div class="form-grid">
              <div class="form-group">
                <label>Employee Code <span class="required">*</span></label>
                <input type="text" id="input-emp-code" required placeholder="e.g. EMP-901">
              </div>
              <div class="form-group">
                <label>Full Name <span class="required">*</span></label>
                <input type="text" id="input-emp-name" required placeholder="e.g. Priyanshu Sharma">
              </div>
              <div class="form-group">
                <label>Email Address <span class="required">*</span></label>
                <input type="email" id="input-emp-email" required placeholder="priyanshu.s@talentai.io">
              </div>
              <div class="form-group">
                <label>Location <span class="required">*</span></label>
                <input type="text" id="input-emp-loc" required placeholder="e.g. Bangalore">
              </div>
              <div class="form-group">
                <label>Department</label>
                <input type="text" id="input-emp-dept" placeholder="e.g. Backend Engineering">
              </div>
              <div class="form-group">
                <label>Seniority Level</label>
                <select id="input-emp-seniority">
                  <option value="Senior">Senior</option>
                  <option value="Lead">Lead</option>
                  <option value="Staff">Staff</option>
                  <option value="Mid-Level">Mid-Level</option>
                  <option value="Junior">Junior</option>
                </select>
              </div>
            </div>
            <div style="display:flex; justify-content:flex-end; gap:12px; margin-top:24px;">
              <button type="button" class="btn btn-secondary" id="btn-cancel-emp">Cancel</button>
              <button type="submit" class="btn btn-primary">Create Profile</button>
            </div>
          </form>
        </div>
      </div>

      <!-- Add Skill Modal -->
      <div id="add-skill-modal" class="loading-overlay" style="display:none;">
        <div class="card" style="width: 100%; max-width: 450px; background: var(--bg-secondary);">
          <div class="card-header">
            <h2 class="card-title">⚡ Endorse Skill Profile</h2>
            <button class="btn btn-sm btn-secondary" id="btn-close-skill-modal">✕</button>
          </div>
          <form id="form-add-skill">
            <div class="form-grid" style="grid-template-columns: 1fr;">
              <div class="form-group">
                <label>Select Skill <span class="required">*</span></label>
                <select id="input-skill-select" required>
                  <option value="">-- Choose Skill --</option>
                </select>
              </div>
              <div class="form-group">
                <label>Proficiency Level <span class="required">*</span></label>
                <select id="input-skill-prof" required>
                  <option value="INTERMEDIATE">INTERMEDIATE</option>
                  <option value="ADVANCED">ADVANCED</option>
                  <option value="EXPERT">EXPERT</option>
                  <option value="BEGINNER">BEGINNER</option>
                </select>
              </div>
              <div class="form-group">
                <label>Years of Experience</label>
                <input type="number" id="input-skill-exp" step="0.5" min="0" value="3.0">
              </div>
              <div class="form-group">
                <label style="display:flex; align-items:center; gap:8px; cursor:pointer;">
                  <input type="checkbox" id="input-skill-cert" style="width:auto; margin:0;"> Certified in this competency
                </label>
              </div>
            </div>
            <div style="display:flex; justify-content:flex-end; gap:12px; margin-top:20px;">
              <button type="button" class="btn btn-secondary" id="btn-cancel-skill">Cancel</button>
              <button type="submit" class="btn btn-primary">Save Skill</button>
            </div>
          </form>
        </div>
      </div>
    `;

    // Modal controls
    document.getElementById('btn-create-emp-modal')?.addEventListener('click', () => {
      document.getElementById('emp-modal').style.display = 'flex';
    });
    document.getElementById('btn-close-emp-modal')?.addEventListener('click', () => {
      document.getElementById('emp-modal').style.display = 'none';
    });
    document.getElementById('btn-cancel-emp')?.addEventListener('click', () => {
      document.getElementById('emp-modal').style.display = 'none';
    });
    document.getElementById('form-create-emp')?.addEventListener('submit', (e) => Employees.handleCreateEmployee(e));

    // Skill modal controls
    document.getElementById('btn-close-skill-modal')?.addEventListener('click', () => {
      document.getElementById('add-skill-modal').style.display = 'none';
    });
    document.getElementById('btn-cancel-skill')?.addEventListener('click', () => {
      document.getElementById('add-skill-modal').style.display = 'none';
    });
    document.getElementById('form-add-skill')?.addEventListener('submit', (e) => Employees.handleAddSkill(e));

    // Filters
    document.getElementById('emp-search')?.addEventListener('input', () => Employees.renderCards());
    document.getElementById('filter-emp-dept')?.addEventListener('change', () => Employees.renderCards());
    document.getElementById('filter-emp-status')?.addEventListener('change', () => Employees.renderCards());
    document.getElementById('btn-reset-emp-filters')?.addEventListener('click', () => {
      document.getElementById('emp-search').value = '';
      document.getElementById('filter-emp-dept').value = '';
      document.getElementById('filter-emp-status').value = '';
      Employees.renderCards();
    });

    await Employees.loadData();
  },

  async loadData() {
    try {
      const [employees, skills] = await Promise.all([
        Api.getEmployees().catch(() => []),
        Api.getSkills().catch(() => [])
      ]);
      Employees.allEmployees = employees || [];
      Employees.availableSkills = skills || [];

      // Populate skills dropdown
      const select = document.getElementById('input-skill-select');
      if (select) {
        select.innerHTML = '<option value="">-- Choose Skill --</option>' +
          Employees.availableSkills.map(s => 
            `<option value="${s.id}">${Utils.escapeHtml(s.name)} (${Utils.escapeHtml(s.category)})</option>`
          ).join('');
      }

      Employees.renderCards();
    } catch (err) {
      console.error('Failed to load employees:', err);
      Utils.showToast('Failed to load employee list', 'error');
    }
  },

  renderCards() {
    const container = document.getElementById('emp-cards-container');
    if (!container) return;

    const query = (document.getElementById('emp-search')?.value || '').toLowerCase();
    const deptFilter = document.getElementById('filter-emp-dept')?.value || '';
    const statusFilter = document.getElementById('filter-emp-status')?.value || '';

    let filtered = Employees.allEmployees.filter(e => {
      if (deptFilter && e.department !== deptFilter) return false;
      if (statusFilter && e.employmentStatus !== statusFilter) return false;
      if (query) {
        const matchesName = (e.name || '').toLowerCase().includes(query);
        const matchesCode = (e.employeeCode || '').toLowerCase().includes(query);
        const matchesLoc = (e.location || '').toLowerCase().includes(query);
        const matchesSkill = (e.skills || []).some(s => (s.skillName || '').toLowerCase().includes(query));
        if (!matchesName && !matchesCode && !matchesLoc && !matchesSkill) return false;
      }
      return true;
    });

    if (filtered.length === 0) {
      container.innerHTML = `<div class="empty-state" style="grid-column: 1 / -1;">
        <div class="empty-state-icon">👥</div>
        <h3>No employees found</h3>
        <p>Try modifying your search or adding a new employee to the directory.</p>
      </div>`;
      return;
    }

    container.innerHTML = filtered.map(e => {
      const initials = (e.name || 'EM').split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
      const skillsHtml = (e.skills || []).map(s => {
        let cls = 'intermediate';
        if (s.proficiencyLevel === 'EXPERT') cls = 'expert';
        else if (s.proficiencyLevel === 'ADVANCED') cls = 'advanced';
        else if (s.proficiencyLevel === 'BEGINNER') cls = 'beginner';
        return `<span class="skill-tag ${cls}" title="${s.yearsExperience || 0} yrs experience${s.certified ? ' • Certified' : ''}">${Utils.escapeHtml(s.skillName)} (${s.proficiencyLevel.substring(0,3)})</span>`;
      }).join(' ') || '<span style="color:var(--text-muted); font-size:12px;">No skills added yet</span>';

      return `
        <div class="employee-card">
          <div style="display:flex; gap:14px; align-items:center; margin-bottom:14px;">
            <div class="employee-avatar">${initials}</div>
            <div class="employee-info">
              <div class="employee-name">${Utils.escapeHtml(e.name)}</div>
              <div class="employee-meta">${Utils.escapeHtml(e.seniorityLevel || 'Engineer')} • ${Utils.escapeHtml(e.department || 'Tech')}</div>
            </div>
            <span class="badge ${e.employmentStatus === 'ACTIVE' ? 'badge-success' : 'badge-warning'}">
              ${e.employmentStatus}
            </span>
          </div>

          <div style="font-size:12px; color:var(--text-secondary); margin-bottom:12px; display:flex; justify-content:space-between;">
            <span>📍 ${Utils.escapeHtml(e.location)}</span>
            <span style="font-family:monospace;">${Utils.escapeHtml(e.employeeCode)}</span>
          </div>

          <div style="margin-bottom:14px;">
            <div style="font-size:11px; color:var(--text-muted); margin-bottom:4px; font-weight:600;">ACTIVE LOAD:</div>
            ${Utils.renderUtilizationBar(e.currentAllocationPercent || 0)}
          </div>

          <div style="margin-bottom:16px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <span style="font-size:11px; color:var(--text-muted); font-weight:600;">SKILLS:</span>
              <button class="btn btn-sm btn-secondary" style="padding:2px 8px; font-size:11px;" onclick="Employees.openAddSkillModal(${e.id})">➕ Add Skill</button>
            </div>
            <div style="display:flex; flex-wrap:wrap; gap:4px;">
              ${skillsHtml}
            </div>
          </div>
        </div>
      `;
    }).join('');
  },

  openAddSkillModal(employeeId) {
    Employees.currentSelectedEmployeeId = employeeId;
    const modal = document.getElementById('add-skill-modal');
    if (modal) modal.style.display = 'flex';
  },

  async handleCreateEmployee(e) {
    e.preventDefault();
    Utils.setLoading(true);

    try {
      const payload = {
        employeeCode: document.getElementById('input-emp-code').value.trim(),
        name: document.getElementById('input-emp-name').value.trim(),
        email: document.getElementById('input-emp-email').value.trim(),
        location: document.getElementById('input-emp-loc').value.trim(),
        department: document.getElementById('input-emp-dept').value.trim() || 'Engineering',
        seniorityLevel: document.getElementById('input-emp-seniority').value,
        employmentStatus: 'ACTIVE'
      };

      const created = await Api.createEmployee(payload);
      Utils.showToast(`Employee ${created.name} (${created.employeeCode}) added!`, 'success');
      document.getElementById('emp-modal').style.display = 'none';
      document.getElementById('form-create-emp').reset();
      await Employees.loadData();
    } catch (err) {
      console.error('Error creating employee:', err);
      Utils.showToast(err.message || 'Failed to create employee', 'error');
    } finally {
      Utils.setLoading(false);
    }
  },

  async handleAddSkill(e) {
    e.preventDefault();
    if (!Employees.currentSelectedEmployeeId) return;

    Utils.setLoading(true);
    try {
      const payload = {
        skillId: parseInt(document.getElementById('input-skill-select').value, 10),
        proficiencyLevel: document.getElementById('input-skill-prof').value,
        yearsExperience: parseFloat(document.getElementById('input-skill-exp').value || '1'),
        certified: document.getElementById('input-skill-cert').checked
      };

      await Api.addEmployeeSkill(Employees.currentSelectedEmployeeId, payload);
      Utils.showToast('Skill attached successfully to profile!', 'success');
      document.getElementById('add-skill-modal').style.display = 'none';
      document.getElementById('form-add-skill').reset();
      await Employees.loadData();
    } catch (err) {
      console.error('Error adding skill:', err);
      Utils.showToast(err.message || 'Failed to add skill', 'error');
    } finally {
      Utils.setLoading(false);
    }
  }
};
