/**
 * TalentAI - Resource Search Screen Module
 * Searches available resources for specific date ranges, skills, and locations
 */
const Search = {
  availableSkills: [],

  async init() {
    const container = document.getElementById('screen-search');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">Resource Availability Search</h1>
          <p class="section-subtitle">Find team members with free capacity and verified competencies across project timelines</p>
        </div>
      </div>

      <!-- Search Criteria Form -->
      <div class="card" style="margin-bottom: 24px;">
        <form id="form-search-resources">
          <div class="form-grid">
            <div class="form-group">
              <label>Start Date <span class="required">*</span></label>
              <input type="date" id="search-start-date" required>
            </div>
            <div class="form-group">
              <label>End Date <span class="required">*</span></label>
              <input type="date" id="search-end-date" required>
            </div>
            <div class="form-group">
              <label>Skill Requirement</label>
              <select id="search-skill-id">
                <option value="">Any Skill</option>
              </select>
            </div>
            <div class="form-group">
              <label>Location</label>
              <input type="text" id="search-location" placeholder="e.g. Bangalore, Hyderabad, Remote">
            </div>
          </div>
          <div style="display:flex; justify-content:flex-end; gap:12px; margin-top:20px;">
            <button type="button" class="btn btn-secondary" id="btn-reset-search">Reset Dates</button>
            <button type="submit" class="btn btn-primary">🔍 Find Available Resources</button>
          </div>
        </form>
      </div>

      <!-- Search Results Area -->
      <div class="card">
        <div class="card-header">
          <h2 class="card-title">Matching Available Talent</h2>
          <span id="search-results-count" class="badge badge-info" style="display:none;">0 Candidates</span>
        </div>
        <div id="search-results-container">
          <div class="empty-state">
            <div class="empty-state-icon">🔎</div>
            <h3>Specify Date Range & Search Criteria</h3>
            <p>Select your project dates and required skills above to evaluate free capacity.</p>
          </div>
        </div>
      </div>
    `;

    // Initialize default dates
    const today = new Date();
    const future = new Date();
    future.setMonth(future.getMonth() + 2);

    const startInput = document.getElementById('search-start-date');
    const endInput = document.getElementById('search-end-date');
    if (startInput) startInput.value = today.toISOString().split('T')[0];
    if (endInput) endInput.value = future.toISOString().split('T')[0];

    document.getElementById('form-search-resources')?.addEventListener('submit', (e) => Search.handleSearch(e));
    document.getElementById('btn-reset-search')?.addEventListener('click', () => {
      if (startInput) startInput.value = today.toISOString().split('T')[0];
      if (endInput) endInput.value = future.toISOString().split('T')[0];
      document.getElementById('search-skill-id').value = '';
      document.getElementById('search-location').value = '';
    });

    await Search.loadSkills();
  },

  async loadSkills() {
    try {
      Search.availableSkills = await Api.getSkills();
      const select = document.getElementById('search-skill-id');
      if (select) {
        select.innerHTML = '<option value="">Any Skill</option>' +
          Search.availableSkills.map(s => 
            `<option value="${s.id}">${Utils.escapeHtml(s.name)} (${Utils.escapeHtml(s.category)})</option>`
          ).join('');
      }
    } catch (err) {
      console.error('Failed to load skills for search:', err);
    }
  },

  async handleSearch(e) {
    e.preventDefault();
    const container = document.getElementById('search-results-container');
    const countBadge = document.getElementById('search-results-count');
    if (!container) return;

    container.innerHTML = `<div class="loading-inline"><div class="spinner-sm"></div> Calculating date-range capacity...</div>`;

    const startDate = document.getElementById('search-start-date').value;
    const endDate = document.getElementById('search-end-date').value;
    const skillId = document.getElementById('search-skill-id').value;
    const location = document.getElementById('search-location').value.trim();

    try {
      const results = await Api.getAvailableResources({
        startDate,
        endDate,
        skillId: skillId || undefined,
        location: location || undefined
      });

      if (!results || results.length === 0) {
        if (countBadge) countBadge.style.display = 'none';
        container.innerHTML = `
          <div class="empty-state">
            <div class="empty-state-icon">⚠️</div>
            <h3>No Available Resources Found</h3>
            <p>No resources with unallocated capacity match the requested timeline and filters.</p>
          </div>
        `;
        return;
      }

      if (countBadge) {
        countBadge.style.display = 'inline-block';
        countBadge.textContent = `${results.length} Available`;
      }

      container.innerHTML = `
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Resource Name</th>
                <th>Employee Code</th>
                <th>Location</th>
                <th>Department</th>
                <th>Current Allocation</th>
                <th>Skills & Competencies</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              ${results.map(emp => {
                const skillsHtml = (emp.skills || []).map(s => 
                  `<span class="skill-tag ${s.proficiencyLevel === 'EXPERT' ? 'expert' : ''}">${Utils.escapeHtml(s.skillName)} (${s.proficiencyLevel.substring(0,3)})</span>`
                ).join(' ') || '<span style="color:var(--text-muted); font-size:12px;">None</span>';

                return `
                  <tr>
                    <td>
                      <div style="font-weight:700;">${Utils.escapeHtml(emp.name)}</div>
                      <div style="font-size:12px; color:var(--text-secondary);">${Utils.escapeHtml(emp.email)}</div>
                    </td>
                    <td><span style="font-family:monospace;">${Utils.escapeHtml(emp.employeeCode)}</span></td>
                    <td>${Utils.escapeHtml(emp.location)}</td>
                    <td>${Utils.escapeHtml(emp.department || 'Engineering')}</td>
                    <td style="width:180px;">${Utils.renderUtilizationBar(emp.currentAllocationPercent || 0)}</td>
                    <td style="max-width:280px;">${skillsHtml}</td>
                    <td>
                      <button class="btn btn-sm btn-secondary" onclick="App.navigateTo('compare')" title="Check Fit for Demand">
                        Match with Demand
                      </button>
                    </td>
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      `;

    } catch (err) {
      console.error('Error during resource search:', err);
      container.innerHTML = `
        <div class="data-disclosure">
          <strong>Search Error:</strong> ${Utils.escapeHtml(err.message || 'Unable to fetch availability records')}
        </div>
      `;
    }
  }
};
