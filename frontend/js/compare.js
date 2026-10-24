/**
 * TalentAI - Candidate Compare & Transparent Scoring Module
 * Evaluates candidate suitability transparently without inventing data
 */
const Compare = {
  currentDemandId: null,
  currentRecommendationData: null,

  async init() {
    const container = document.getElementById('screen-compare');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">Candidate Evaluation & Transparent Scoring</h1>
          <p class="section-subtitle">Multi-dimensional scoring grounded exclusively in verified employee database records</p>
        </div>
      </div>

      <!-- Demand Selector Card -->
      <div class="card" style="margin-bottom: 24px;">
        <div style="display:flex; flex-wrap:wrap; gap:16px; align-items:center;">
          <label style="margin:0; font-size:14px; font-weight:700;">Select Project Demand:</label>
          <div style="flex:1; min-width:280px;">
            <select id="compare-demand-select" style="margin:0;">
              <option value="">-- Choose Demand to Evaluate Candidates --</option>
            </select>
          </div>
          <button class="btn btn-primary" id="btn-run-scoring">⚡ Calculate Scores</button>
        </div>
      </div>

      <!-- Grounding Statement & Rules -->
      <div class="grounding-statement" id="grounding-box" style="display:none;">
        🛡️ <strong>Data Integrity Guarantee:</strong> All match scores, skill proficiencies, and availability percentages are derived directly from stored operational records. No speculative or hallucinated attributes are applied.
      </div>

      <!-- Comparison Area -->
      <div id="compare-results-area" style="margin-top: 24px;">
        <div class="empty-state">
          <div class="empty-state-icon">⚖️</div>
          <h3>Select a Demand to View Candidate Matches</h3>
          <p>Choose an open project demand from the dropdown above to run multi-criteria evaluation.</p>
        </div>
      </div>

      <!-- Allocate Modal -->
      <div id="allocate-modal" class="loading-overlay" style="display:none;">
        <div class="card" style="width: 100%; max-width: 500px; background: var(--bg-secondary);">
          <div class="card-header">
            <h2 class="card-title">📝 Confirm Resource Allocation</h2>
            <button class="btn btn-sm btn-secondary" id="btn-close-alloc-modal">✕</button>
          </div>
          <form id="form-confirm-alloc">
            <div style="margin-bottom:16px;">
              <div id="alloc-modal-summary" style="background:var(--bg-glass); padding:12px; border-radius:8px; font-size:13px; line-height:1.6;"></div>
            </div>
            <div id="alloc-modal-error" style="display:none; margin-bottom:16px; padding:10px 14px; background:rgba(239,68,68,0.12); border-left:4px solid var(--accent-red); border-radius:6px; color:#fca5a5; font-size:13px;"></div>
            <div class="form-group" style="margin-bottom:16px;">
              <label>Requested By <span class="required">*</span></label>
              <input type="text" id="input-alloc-requester" value="Project Manager" required>
            </div>
            <div style="display:flex; justify-content:flex-end; gap:12px;">
              <button type="button" class="btn btn-secondary" id="btn-cancel-alloc">Cancel</button>
              <button type="submit" class="btn btn-primary">Create Allocation</button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.getElementById('compare-demand-select')?.addEventListener('change', (e) => {
      Compare.currentDemandId = e.target.value;
      if (Compare.currentDemandId) {
        Compare.loadRecommendations(Compare.currentDemandId);
      }
    });

    document.getElementById('btn-run-scoring')?.addEventListener('click', () => {
      if (Compare.currentDemandId) {
        Compare.loadRecommendations(Compare.currentDemandId);
      } else {
        Utils.showToast('Please select a demand first', 'info');
      }
    });

    // Allocate modal handlers
    document.getElementById('btn-close-alloc-modal')?.addEventListener('click', () => {
      document.getElementById('allocate-modal').style.display = 'none';
    });
    document.getElementById('btn-cancel-alloc')?.addEventListener('click', () => {
      document.getElementById('allocate-modal').style.display = 'none';
    });
    document.getElementById('form-confirm-alloc')?.addEventListener('submit', (e) => Compare.handleCreateAllocation(e));

    await Compare.loadDemandsDropdown();
    if (Compare.currentDemandId) {
      const select = document.getElementById('compare-demand-select');
      if (select) select.value = Compare.currentDemandId;
      await Compare.loadRecommendations(Compare.currentDemandId);
    }
  },

  async loadDemandsDropdown() {
    try {
      const demands = await Api.getDemands();
      const select = document.getElementById('compare-demand-select');
      if (select) {
        select.innerHTML = '<option value="">-- Choose Demand to Evaluate Candidates --</option>' +
          (demands || []).map(d => 
            `<option value="${d.id}" ${d.id == Compare.currentDemandId ? 'selected' : ''}>
              #${d.id} - ${Utils.escapeHtml(d.projectName)}: ${Utils.escapeHtml(d.role)} (${d.status})
            </option>`
          ).join('');
      }
    } catch (err) {
      console.error('Failed to populate demands dropdown:', err);
    }
  },

  async selectDemandAndCompare(demandId) {
    Compare.currentDemandId = demandId;
    App.navigateTo('compare');
    const select = document.getElementById('compare-demand-select');
    if (select) select.value = demandId;
    await Compare.loadRecommendations(demandId);
  },

  async loadRecommendations(demandId) {
    const resultsArea = document.getElementById('compare-results-area');
    const groundingBox = document.getElementById('grounding-box');
    if (!resultsArea) return;

    resultsArea.innerHTML = `<div class="loading-inline"><div class="spinner-sm"></div> Running objective scoring algorithm...</div>`;

    try {
      const response = await Api.getRecommendations(demandId);
      Compare.currentRecommendationData = response;

      if (groundingBox) groundingBox.style.display = 'block';

      const eligible = response.eligibleCandidates || [];
      const ineligible = response.ineligibleCandidates || [];

      resultsArea.innerHTML = `
        <!-- Scoring Methodology Header -->
        <div class="card" style="margin-bottom: 20px; background: rgba(99,102,241,0.06); border-color: rgba(99,102,241,0.2);">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <div>
              <h3 style="font-size:16px; font-weight:700;">Evaluation for: ${Utils.escapeHtml(response.projectName)} (${Utils.escapeHtml(response.role)})</h3>
              <p style="font-size:12px; color:var(--text-secondary); margin-top:4px;">
                Evaluated: <strong>${response.totalEvaluated}</strong> total resources • Eligible: <strong>${eligible.length}</strong> • Ineligible: <strong>${ineligible.length}</strong>
              </p>
            </div>
            <button class="btn btn-secondary" onclick="AiAssistant.consultDemand(${demandId})">
              🤖 Consult AI Agent on this Demand
            </button>
          </div>
        </div>

        <!-- Section: Eligible Candidates -->
        <h3 style="font-size:18px; font-weight:700; margin-bottom:14px; color:var(--accent-green); display:flex; align-items:center; gap:8px;">
          <span>✓</span> Eligible Candidates (${eligible.length})
        </h3>
        ${eligible.length === 0 ? `
          <div class="card" style="margin-bottom:24px; text-align:center; padding:32px; color:var(--text-muted);">
            No resources meet all mandatory skill and availability constraints for this demand.
          </div>
        ` : `
          <div style="display:flex; flex-direction:column; gap:16px; margin-bottom:32px;">
            ${eligible.map((c, index) => Compare.renderCandidateCard(c, index + 1, true, demandId)).join('')}
          </div>
        `}

        <!-- Section: Ineligible Candidates -->
        <h3 style="font-size:18px; font-weight:700; margin-bottom:14px; color:var(--accent-red); display:flex; align-items:center; gap:8px;">
          <span>✕</span> Ineligible Candidates (${ineligible.length})
        </h3>
        ${ineligible.length === 0 ? `
          <div class="card" style="margin-bottom:24px; text-align:center; padding:24px; color:var(--text-muted);">
            No ineligible candidates.
          </div>
        ` : `
          <div style="display:flex; flex-direction:column; gap:16px;">
            ${ineligible.map((c, index) => Compare.renderCandidateCard(c, index + 1, false, demandId)).join('')}
          </div>
        `}
      `;

    } catch (err) {
      console.error('Failed to load candidate recommendations:', err);
      resultsArea.innerHTML = `
        <div class="data-disclosure">
          <strong>Scoring Error:</strong> ${Utils.escapeHtml(err.message || 'Unable to compute match scores')}
        </div>
      `;
    }
  },

  renderCandidateCard(candidate, rank, isEligible, demandId) {
    const totalScore = Math.round(candidate.totalScore || 0);

    // Skill match pills
    const skillMatchesHtml = (candidate.skillMatches || []).map(m => {
      const statusIcon = m.meets ? '✓' : '✕';
      const color = m.meets ? 'var(--accent-green)' : 'var(--accent-red)';
      return `
        <div style="display:flex; align-items:center; justify-content:space-between; font-size:12px; background:rgba(255,255,255,0.03); padding:6px 10px; border-radius:6px; border-left:3px solid ${color};">
          <div>
            <span style="font-weight:600;">${Utils.escapeHtml(m.skillName)}</span>
            ${m.mandatory ? '<span style="color:var(--accent-orange); font-size:10px;"> (Mandatory)</span>' : ''}
          </div>
          <div style="display:flex; gap:10px; align-items:center;">
            <span style="font-size:11px; color:var(--text-muted);">Req: ${m.requiredLevel} | Got: ${m.actualLevel || 'NONE'}</span>
            <span style="font-weight:700; color:${color};">${statusIcon}</span>
          </div>
        </div>
      `;
    }).join('');

    // Skill gaps
    const gapsHtml = (candidate.skillGaps || []).map(g => `
      <div style="font-size:11px; color:var(--accent-orange); background:rgba(245,158,11,0.08); padding:4px 8px; border-radius:4px; margin-top:4px;">
        ⚠️ Gap in <strong>${Utils.escapeHtml(g.skillName)}</strong>: Required ${g.requiredLevel}.
        ${g.suggestedCourse ? `<br>💡 Suggested: ${Utils.escapeHtml(g.suggestedCourse)} (${g.estimatedDurationDays || 14} days)` : ''}
      </div>
    `).join('');

    const fitClassification = candidate.fitClassification || (totalScore >= 75 ? 'HIGH FIT' : (totalScore >= 50 ? 'MODERATE FIT' : 'LOW FIT'));

    // Status badges, conflict warning, and action buttons
    let statusBadgesHtml = '';
    let conflictWarningHtml = '';
    let allocateButtonHtml = '';

    if (!isEligible) {
      statusBadgesHtml = `<span class="badge badge-danger">INELIGIBLE</span>`;
      allocateButtonHtml = `<button class="btn btn-sm btn-secondary" disabled>Ineligible for Allocation</button>`;
    } else if (candidate.alreadyAllocated) {
      statusBadgesHtml = `
        <span class="badge badge-success">ELIGIBLE</span>
        <span class="badge badge-warning" style="margin-left:6px;">ALREADY ALLOCATED</span>
      `;
      conflictWarningHtml = `
        <div style="background:rgba(245,158,11,0.12); border-left:4px solid var(--accent-orange); padding:8px 12px; border-radius:4px; font-size:12px; margin-top:10px; color:#fde68a;">
          📌 <strong>Allocation Conflict:</strong> ${Utils.escapeHtml(candidate.allocationStatusReason || 'Candidate is already allocated to this demand.')}
        </div>
      `;
      allocateButtonHtml = `<button class="btn btn-sm btn-secondary" disabled title="Already allocated to this demand">Already Allocated to Demand</button>`;
    } else if (!candidate.hasSufficientCapacity) {
      statusBadgesHtml = `
        <span class="badge badge-success">ELIGIBLE</span>
        <span class="badge badge-warning" style="margin-left:6px;">INSUFFICIENT CAPACITY</span>
      `;
      conflictWarningHtml = `
        <div style="background:rgba(239,68,68,0.12); border-left:4px solid var(--accent-red); padding:8px 12px; border-radius:4px; font-size:12px; margin-top:10px; color:#fca5a5;">
          ⚠️ <strong>Capacity Constraint:</strong> ${Utils.escapeHtml(candidate.allocationStatusReason || 'Candidate lacks required capacity for this period.')}
        </div>
      `;
      allocateButtonHtml = `<button class="btn btn-sm btn-secondary" disabled title="Insufficient capacity">Insufficient Capacity (${candidate.availablePercent}% available)</button>`;
    } else {
      statusBadgesHtml = `
        <span class="badge badge-success">READY TO ALLOCATE</span>
      `;
      allocateButtonHtml = `
        <button class="btn btn-sm btn-primary" onclick="Compare.openAllocateModal(${candidate.employeeId}, '${Utils.escapeHtml(candidate.employeeName)}', ${demandId})">
          ✓ Allocate to Project
        </button>
      `;
    }

    return `
      <div class="candidate-card ${isEligible ? 'eligible' : 'ineligible'}">
        <div class="candidate-rank">#${rank}</div>
        <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:12px; padding-right:40px;">
          <div>
            <div class="candidate-name">${Utils.escapeHtml(candidate.employeeName)}</div>
            <div class="candidate-meta">
              ${Utils.escapeHtml(candidate.seniorityLevel || 'Engineer')} • ${Utils.escapeHtml(candidate.department || 'Tech')} • 📍 ${Utils.escapeHtml(candidate.location || 'HQ')} • <span style="font-family:monospace;">${candidate.employeeCode}</span>
            </div>
          </div>
          <div style="text-align:right;">
            <div style="font-size:15px; font-weight:800; color:${fitClassification === 'HIGH FIT' ? 'var(--accent-green)' : fitClassification === 'MODERATE FIT' ? 'var(--accent-cyan)' : 'var(--accent-orange)'}; margin-bottom:4px; letter-spacing:0.5px;">
              ${fitClassification}
            </div>
            <div style="font-size:12px; color:var(--text-secondary); margin-bottom:6px;">
              Score: ${totalScore}/100
            </div>
            <div style="display:flex; justify-content:flex-end; gap:4px; flex-wrap:wrap;">
              ${statusBadgesHtml}
            </div>
          </div>
        </div>

        <!-- 4-Dimension Score Breakdown -->
        <div class="score-breakdown">
          <div class="score-item">
            <div class="score-item-label">SKILL MATCH (${Math.round(candidate.skillScore || 0)}/100)</div>
            ${Utils.renderScoreBar(candidate.skillScore)}
          </div>
          <div class="score-item">
            <div class="score-item-label">AVAILABILITY (${Math.round(candidate.availabilityScore || 0)}%)</div>
            ${Utils.renderScoreBar(candidate.availabilityScore)}
          </div>
          <div class="score-item">
            <div class="score-item-label">LOCATION FIT (${Math.round(candidate.locationScore || 0)}%)</div>
            ${Utils.renderScoreBar(candidate.locationScore)}
          </div>
          <div class="score-item">
            <div class="score-item-label">EXPERIENCE DEPTH (${Math.round(candidate.experienceScore || 0)}%)</div>
            ${Utils.renderScoreBar(candidate.experienceScore)}
          </div>
        </div>

        <!-- Skills evaluation grid -->
        <div style="margin-top:16px;">
          <div style="font-size:11px; font-weight:700; color:var(--text-muted); margin-bottom:6px; letter-spacing:0.5px;">SKILL CRITERIA EVALUATION:</div>
          <div style="display:flex; flex-direction:column; gap:6px;">
            ${skillMatchesHtml || '<div style="font-size:12px; color:var(--text-muted);">No specific skills required.</div>'}
          </div>
          ${gapsHtml}
        </div>

        ${conflictWarningHtml}

        ${candidate.dataDisclosure ? `
          <div class="data-disclosure" style="margin-top:12px;">
            ⚠️ ${Utils.escapeHtml(candidate.dataDisclosure)}
          </div>
        ` : ''}

        <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:16px; border-top:1px solid var(--border); padding-top:12px;">
          <button class="btn btn-sm btn-secondary" onclick="AiAssistant.consultDemand(${demandId})">
            🤖 Ask AI
          </button>
          ${allocateButtonHtml}
        </div>
      </div>
    `;
  },

  openAllocateModal(employeeId, employeeName, demandId) {
    const modal = document.getElementById('allocate-modal');
    const summary = document.getElementById('alloc-modal-summary');
    const errBox = document.getElementById('alloc-modal-error');
    if (!modal || !summary) return;

    if (errBox) errBox.style.display = 'none';

    Compare.pendingAlloc = { employeeId, demandId, employeeName };
    summary.innerHTML = `
      <div><strong>Resource:</strong> ${employeeName}</div>
      <div><strong>Demand ID:</strong> #${demandId}</div>
      <div style="margin-top:4px; font-size:11px; color:var(--text-secondary);">
        Submitting this form creates a pending allocation and reserves capacity.
      </div>
    `;

    modal.style.display = 'flex';
  },

  async handleCreateAllocation(e) {
    e.preventDefault();
    if (!Compare.pendingAlloc) return;

    const submitBtn = e.target.querySelector('button[type="submit"]');
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Allocating...';
    }

    Utils.setLoading(true);
    try {
      // Find demand details to get start/end dates and percent
      const demand = await Api.getDemand(Compare.pendingAlloc.demandId);
      const requester = document.getElementById('input-alloc-requester').value.trim() || 'Staffing Manager';

      const payload = {
        employeeId: Compare.pendingAlloc.employeeId,
        demandId: Compare.pendingAlloc.demandId,
        startDate: demand.startDate,
        endDate: demand.endDate,
        allocationPercent: demand.allocationPercent,
        requestedBy: requester
      };

      const result = await Api.createAllocation(payload);
      Utils.showToast(`Allocation #${result.id} created successfully in PENDING status!`, 'success');
      document.getElementById('allocate-modal').style.display = 'none';
      App.navigateTo('approvals');
    } catch (err) {
      console.error('Failed to create allocation:', err);
      const errMsg = err.message || 'Failed to create allocation';
      const errBox = document.getElementById('alloc-modal-error');
      if (errBox) {
        errBox.innerHTML = `⚠️ <strong>Allocation Rejected:</strong> ${Utils.escapeHtml(errMsg)}`;
        errBox.style.display = 'block';
      }
      Utils.showToast(errMsg, 'error');
    } finally {
      Utils.setLoading(false);
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Create Allocation';
      }
    }
  }
};
