/**
 * TalentAI - AI Staffing Assistant Screen Module
 * Goal-driven conversational agent with controlled tool calls,
 * evidence grounding in JPA database records, and verifiable outcomes.
 */
const AiAssistant = {
  currentDemandId: null,
  currentStaffingAdvice: null,
  conversationContext: {
    activeCandidateName: '',
    previousCandidateName: '',
    lastGoal: '',
    demandId: null,
  },

  async init() {
    const container = document.getElementById('screen-ai-assistant');
    if (!container) return;

    container.innerHTML = `
      <div class="section-header">
        <div>
          <h1 class="section-title">AI Staffing & Resource Assistant</h1>
          <p class="section-subtitle">Goal-driven agentic workflow with auditable tool invocations and human-in-the-loop approval</p>
        </div>
        <div class="sim-badge" style="font-size:12px; padding:6px 14px; background:rgba(99,102,241,0.15); border:1px solid rgba(99,102,241,0.3); border-radius:20px; color:var(--accent-primary); font-weight:600;">
          🤖 AI MODE: SIMULATED AGENT (Grounded in DB Records)
        </div>
      </div>

      <!-- Main Layout: Chat & Agent Inspector -->
      <div class="ai-container">
        <!-- Chat Pane -->
        <div class="chat-pane">
          <!-- Chat Messages Area -->
          <div class="chat-messages" id="chat-messages-container">
            <div class="chat-message">
              <div class="message-avatar">🤖</div>
              <div class="message-bubble">
                <p><strong>Hello! I am your Goal-Driven AI Staffing Agent.</strong></p>
                <p style="margin-top:6px; font-size:13px; color:var(--text-secondary);">
                  I understand your staffing goals, maintain conversational context, execute controlled database tools, and validate business rules without hallucinating data.
                </p>
                <div style="margin-top:10px; font-size:12px;">
                  <strong style="color:var(--text-primary);">Try asking:</strong>
                  <div style="display:flex; flex-wrap:wrap; gap:6px; margin-top:6px;">
                    <button class="btn btn-sm btn-secondary" onclick="AiAssistant.quickAsk('Find the best employee for Project Phoenix.')">🎯 Best Candidate</button>
                    <button class="btn btn-sm btn-secondary" onclick="AiAssistant.quickAsk('What about Alice?')">👥 What about Alice?</button>
                    <button class="btn btn-sm btn-secondary" onclick="AiAssistant.quickAsk('Why?')">💡 Why?</button>
                    <button class="btn btn-sm btn-secondary" onclick="AiAssistant.quickAsk('Can she take 100%?')">📅 Can she take 100%?</button>
                    <button class="btn btn-sm btn-secondary" onclick="AiAssistant.quickAsk('Why isn\\'t James Davis eligible for Project Phoenix?')">❓ Eligibility Check</button>
                    <button class="btn btn-sm btn-secondary" onclick="AiAssistant.quickAsk('Who is available for 50% on Project Phoenix?')">🔍 50% Capacity Search</button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Chat Input Bar -->
          <div class="chat-input-bar">
            <input type="text" id="ai-chat-input" placeholder="Ask a staffing question (e.g. 'Find the best employee for Project Phoenix.')...">
            <button class="btn btn-primary" id="btn-ai-send">Ask Agent</button>
          </div>
        </div>

        <!-- Right Side: Agent Inspector & Tool Execution Trace -->
        <div style="display:flex; flex-direction:column; gap:16px; overflow-y:auto;">
          <!-- Select Demand Widget -->
          <div class="card" style="padding:16px;">
            <div class="card-header" style="margin-bottom:10px;">
              <h3 class="card-title" style="font-size:14px;">🎯 Target Project Demand</h3>
            </div>
            <select id="ai-demand-select" style="margin-bottom:10px;">
              <option value="">-- Choose Demand --</option>
            </select>
            <button class="btn btn-sm btn-primary" id="btn-run-agent-workflow" style="width:100%;">
              ⚡ Execute Staffing Workflow
            </button>
          </div>

          <!-- Autonomous Tool Trace Inspector -->
          <div class="card" style="padding:16px; flex:1;">
            <div class="card-header" style="margin-bottom:10px; display:flex; justify-content:space-between; align-items:center;">
              <h3 class="card-title" style="font-size:14px;">🛠️ Agent Activity & Tools</h3>
              <span class="badge badge-info" id="tool-trace-badge">Idle</span>
            </div>
            <div id="ai-tool-trace-container" style="font-size:12px; color:var(--text-secondary); max-height:340px; overflow-y:auto;">
              <p style="color:var(--text-muted); font-style:italic;">No agent actions yet. Enter a question or select a demand to observe goal interpretation, planning, tool invocations, and evidence reasoning.</p>
            </div>
          </div>

          <!-- Approval Gate Action Card -->
          <div class="card" id="ai-approval-gate-card" style="display:none; padding:16px; border-color:var(--accent-green); background:rgba(16,185,129,0.05);">
            <div class="card-header" style="margin-bottom:8px;">
              <h3 class="card-title" style="font-size:14px; color:var(--accent-green);">🛡️ Human-in-the-Loop Gate</h3>
            </div>
            <p style="font-size:12px; color:var(--text-secondary); margin-bottom:12px;" id="ai-gate-description">
              Agent recommendation is pending manager sign-off. Approving triggers runtime revalidation.
            </p>
            <button class="btn btn-sm btn-success" id="btn-ai-approve-action" style="width:100%;">
              ✓ Approve AI Recommendation
            </button>
          </div>
        </div>
      </div>
    `;

    // Event listeners
    document.getElementById('btn-ai-send')?.addEventListener('click', () => AiAssistant.handleUserQuery());
    document.getElementById('ai-chat-input')?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') AiAssistant.handleUserQuery();
    });
    // Selecting a demand ONLY sets conversation context — NO recommendation generated
    document.getElementById('ai-demand-select')?.addEventListener('change', (e) => {
      const demandId = e.target.value;
      if (demandId) {
        AiAssistant.currentDemandId = demandId;
        AiAssistant.conversationContext.demandId = demandId;
        AiAssistant.conversationContext.activeCandidateName = '';
        AiAssistant.conversationContext.previousCandidateName = '';
        AiAssistant.conversationContext.lastGoal = '';
      }
    });
    // Execute Staffing Workflow initializes context and waits for user question
    document.getElementById('btn-run-agent-workflow')?.addEventListener('click', () => {
      const demandId = document.getElementById('ai-demand-select')?.value;
      if (demandId) {
        AiAssistant.handleExecuteWorkflow(demandId);
      } else {
        Utils.showToast('Please select a demand first', 'info');
      }
    });
    document.getElementById('btn-ai-approve-action')?.addEventListener('click', () => AiAssistant.handleApproveAdvice());

    await AiAssistant.loadDemands();
  },

  async loadDemands() {
    try {
      const demands = await Api.getDemands();
      const select = document.getElementById('ai-demand-select');
      if (select) {
        select.innerHTML = '<option value="">-- Choose Demand --</option>' +
          (demands || []).map(d => 
            `<option value="${d.id}" ${d.id == AiAssistant.currentDemandId ? 'selected' : ''}>
              #${d.id} - ${Utils.escapeHtml(d.projectName)} (${Utils.escapeHtml(d.role)})
            </option>`
          ).join('');
      }
    } catch (err) {
      console.error('Failed to load demands in AI screen:', err);
    }
  },

  handleExecuteWorkflow(demandId) {
    AiAssistant.currentDemandId = demandId;
    AiAssistant.conversationContext.demandId = demandId;
    AiAssistant.conversationContext.activeCandidateName = '';
    AiAssistant.conversationContext.previousCandidateName = '';
    AiAssistant.conversationContext.lastGoal = 'INITIALIZATION';

    const select = document.getElementById('ai-demand-select');
    let projectName = 'the selected project';
    if (select && select.selectedOptions && select.selectedOptions[0]) {
      const text = select.selectedOptions[0].textContent || '';
      const match = text.match(/#\d+\s*-\s*([^(]+)/);
      if (match) projectName = match[1].trim();
    }

    const traceBadge = document.getElementById('tool-trace-badge');
    const traceContainer = document.getElementById('ai-tool-trace-container');
    const gateCard = document.getElementById('ai-approval-gate-card');

    if (gateCard) gateCard.style.display = 'none';
    if (traceBadge) {
      traceBadge.className = 'badge badge-info';
      traceBadge.textContent = 'Ready';
    }
    if (traceContainer) {
      traceContainer.innerHTML = `
        <div style="display:flex; flex-direction:column; gap:8px;">
          <div style="font-size:12px; display:flex; justify-content:space-between; align-items:center;">
            <div><span style="color:var(--text-muted);">Goal:</span> <strong style="color:var(--accent-primary);">INITIALIZATION</strong></div>
            <span class="badge" style="background:rgba(99,102,241,0.15); color:var(--accent-primary); border:1px solid rgba(99,102,241,0.3); font-size:9px; padding:2px 6px;">AI MODE: SIMULATED AGENT</span>
          </div>
          <div style="background:rgba(255,255,255,0.03); border:1px solid var(--border); border-radius:6px; padding:8px 10px;">
            <div style="font-weight:700; color:var(--accent-primary); font-size:11px; margin-bottom:4px;">📋 AGENT STATUS</div>
            <p style="margin:0; font-size:11px; color:var(--text-secondary);">Context active for <strong>${Utils.escapeHtml(projectName)}</strong>. Awaiting your staffing question.</p>
          </div>
          <div style="font-size:11px; color:var(--accent-green); font-weight:600;">
            ✓ Agent initialized. Waiting for user question.
          </div>
        </div>
      `;
    }

    AiAssistant.appendAgentMessage(`
      <div style="font-size:13.5px; line-height:1.5; color:var(--text-primary);">
        I’m ready to help with <strong>${Utils.escapeHtml(projectName)}</strong>. What would you like to know?
      </div>
    `);
  },

  async consultDemand(demandId) {
    AiAssistant.currentDemandId = demandId;
    AiAssistant.conversationContext.demandId = demandId;
    App.navigateTo('ai-assistant');
    const select = document.getElementById('ai-demand-select');
    if (select) select.value = demandId;
    AiAssistant.handleExecuteWorkflow(demandId);
  },

  quickAsk(questionText) {
    const input = document.getElementById('ai-chat-input');
    if (input) {
      input.value = questionText;
      AiAssistant.handleUserQuery();
    }
  },

  async handleUserQuery() {
    const input = document.getElementById('ai-chat-input');
    const query = input?.value.trim();
    if (!query) return;

    input.value = '';
    AiAssistant.appendUserMessage(query);

    let demandId = document.getElementById('ai-demand-select')?.value;
    if (!demandId && AiAssistant.conversationContext.demandId) demandId = AiAssistant.conversationContext.demandId;
    if (!demandId && AiAssistant.currentDemandId) demandId = AiAssistant.currentDemandId;

    if (!demandId) {
      // Find the first open demand to assist
      try {
        const open = await Api.getOpenDemandsByPriority();
        if (open && open.length > 0) {
          demandId = open[0].id;
          const select = document.getElementById('ai-demand-select');
          if (select) select.value = demandId;
        }
      } catch (e) {}
    }

    if (!demandId) {
      AiAssistant.appendAgentMessage(`I need an active project demand to query skills and availability. Please select or create a demand first.`);
      return;
    }

    await AiAssistant.runStaffingAgent(demandId, query);
  },

  async runStaffingAgent(demandId, customQuestion = '') {
    AiAssistant.currentDemandId = demandId;
    const traceBadge = document.getElementById('tool-trace-badge');
    const traceContainer = document.getElementById('ai-tool-trace-container');
    const gateCard = document.getElementById('ai-approval-gate-card');

    if (traceBadge) {
      traceBadge.className = 'badge badge-warning';
      traceBadge.textContent = 'Planning...';
    }

    if (traceContainer) {
      traceContainer.innerHTML = `
        <div class="loading-inline" style="padding:16px;">
          <div class="spinner-sm"></div> Interpreting goal and selecting controlled tools...
        </div>
      `;
    }

    const typingMsg = AiAssistant.appendTypingIndicator();

    try {
      const advice = await Api.getAiStaffingAdvice(
        demandId,
        customQuestion,
        'Staffing Manager',
        AiAssistant.conversationContext.activeCandidateName || '',
        AiAssistant.conversationContext.previousCandidateName || '',
        AiAssistant.conversationContext.lastGoal || ''
      );
      AiAssistant.currentStaffingAdvice = advice;

      // Update conversation context dynamically
      if (advice.activeCandidateName) {
        if (AiAssistant.conversationContext.activeCandidateName &&
            AiAssistant.conversationContext.activeCandidateName !== advice.activeCandidateName) {
          AiAssistant.conversationContext.previousCandidateName = AiAssistant.conversationContext.activeCandidateName;
        }
        AiAssistant.conversationContext.activeCandidateName = advice.activeCandidateName;
      }
      if (advice.comparedCandidateName) {
        AiAssistant.conversationContext.previousCandidateName = advice.comparedCandidateName;
      }
      if (advice.userGoal && advice.userGoal !== 'OUT_OF_DOMAIN') {
        AiAssistant.conversationContext.lastGoal = advice.userGoal;
      }
      if (advice.demandId) {
        AiAssistant.conversationContext.demandId = advice.demandId;
        AiAssistant.currentDemandId = advice.demandId;
        const select = document.getElementById('ai-demand-select');
        if (select && select.value != advice.demandId) {
          select.value = advice.demandId;
        }
      }

      typingMsg.remove();

      // Render activity and tool trace in inspector panel
      AiAssistant.renderToolInspector(advice);

      // Render conversational response in chat pane
      AiAssistant.renderAdviceInChat(advice);

      // Show approval gate if token exists (Candidate Recommendation workflow)
      if (gateCard) {
        if (advice.approvalToken && advice.topCandidate && advice.approvalStatus === 'PENDING_APPROVAL') {
          gateCard.style.display = 'block';
          const gateDesc = document.getElementById('ai-gate-description');
          if (gateDesc) {
            gateDesc.innerHTML = `Ready to allocate <strong>${Utils.escapeHtml(advice.topCandidate.employeeName)}</strong> to <strong>${Utils.escapeHtml(advice.demandSummary)}</strong>.`;
          }
        } else {
          gateCard.style.display = 'none';
        }
      }

    } catch (err) {
      console.error('AI Agent execution error:', err);
      typingMsg.remove();
      if (traceBadge) {
        traceBadge.className = 'badge badge-danger';
        traceBadge.textContent = 'Error';
      }
      AiAssistant.appendAgentMessage(`⚠️ An error occurred while executing the staffing workflow: ${err.message || 'Service unavailable'}`);
    }
  },

  renderToolInspector(advice) {
    const traceBadge = document.getElementById('tool-trace-badge');
    const traceContainer = document.getElementById('ai-tool-trace-container');
    if (!traceContainer) return;

    if (traceBadge) {
      traceBadge.className = 'badge badge-success';
      traceBadge.textContent = `${advice.userGoal || 'Goal Completed'}`;
    }

    const planHtml = advice.plan && advice.plan.length > 0
      ? `<div style="background:rgba(255,255,255,0.03); border:1px solid var(--border); border-radius:6px; padding:8px 10px; margin-bottom:10px;">
          <div style="font-weight:700; color:var(--accent-primary); font-size:11px; margin-bottom:4px;">📋 AGENT PLAN</div>
          <ul style="margin:0 0 0 16px; padding:0; font-size:11px; color:var(--text-secondary); line-height:1.4;">
            ${advice.plan.map(p => `<li>${Utils.escapeHtml(p)}</li>`).join('')}
          </ul>
        </div>`
      : '';

    const validationHtml = advice.validationResult
      ? `<div style="background:rgba(16,185,129,0.06); border-left:3px solid var(--accent-green); padding:6px 10px; border-radius:0 4px 4px 0; margin-bottom:10px; font-size:11px;">
          <strong style="color:var(--accent-green);">🛡️ VALIDATION:</strong> ${Utils.escapeHtml(advice.validationResult)}
        </div>`
      : '';

    const evidenceHtml = advice.evidenceSummary
      ? `<div style="background:rgba(99,102,241,0.06); border-left:3px solid var(--accent-primary); padding:6px 10px; border-radius:0 4px 4px 0; margin-bottom:10px; font-size:11px;">
          <strong style="color:var(--accent-primary);">📊 EVIDENCE:</strong> ${Utils.escapeHtml(advice.evidenceSummary)}
        </div>`
      : '';

    const outcomeHtml = advice.outcome
      ? `<div style="margin-bottom:8px; font-size:11.5px; display:flex; align-items:center; gap:6px;">
          <span style="color:var(--text-muted); font-weight:600;">Outcome:</span>
          <span class="badge badge-success" style="font-size:10px; font-weight:600;">${Utils.escapeHtml(advice.outcome)}</span>
        </div>`
      : '';

    const toolsHtml = (advice.toolCallTrace || []).map(t => `
      <div class="tool-call-item" style="margin-bottom:8px; padding:8px; background:rgba(6,182,212,0.04); border-left:3px solid var(--accent-cyan); border-radius:0 6px 6px 0;">
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <strong style="color:var(--accent-cyan); font-family:monospace;">✓ ${Utils.escapeHtml(t.toolName)}</strong>
          <span style="font-size:10px; color:var(--accent-green); font-weight:600;">EXECUTED</span>
        </div>
        ${t.summary ? `<div style="font-size:11px; color:var(--text-secondary); margin-top:2px;">${Utils.escapeHtml(t.summary)}</div>` : ''}
        ${t.parameters ? `<div style="font-size:10px; color:var(--text-muted); font-family:monospace; margin-top:3px;">args: ${Utils.escapeHtml(JSON.stringify(t.parameters))}</div>` : ''}
      </div>
    `).join('');

    traceContainer.innerHTML = `
      <div style="display:flex; flex-direction:column;">
        <div style="margin-bottom:8px; font-size:12px; display:flex; justify-content:space-between; align-items:center;">
          <div><span style="color:var(--text-muted);">Goal:</span> <strong style="color:var(--text-primary);">${Utils.escapeHtml(advice.userGoal || 'Staffing Analysis')}</strong></div>
          <span class="badge" style="background:rgba(99,102,241,0.15); color:var(--accent-primary); border:1px solid rgba(99,102,241,0.3); font-size:9px; padding:2px 6px;">AI MODE: SIMULATED AGENT</span>
        </div>
        ${outcomeHtml}
        ${planHtml}
        <div style="font-weight:700; color:var(--accent-cyan); font-size:11px; margin-bottom:6px;">🛠️ CONTROLLED TOOL CALLS (${advice.toolCallTrace?.length || 0})</div>
        ${toolsHtml}
        ${evidenceHtml}
        ${validationHtml}
      </div>
    `;
  },

  renderAdviceInChat(advice) {
    const isFirstRecommendation = advice.userGoal === 'CANDIDATE_RECOMMENDATION';

    // Format tools executed list
    const toolsExecuted = (advice.toolCallTrace || []).map(t => t.toolName).join(', ') || 'get_demand';

    // Format conversational answer (supports markdown bold and line breaks)
    const formattedAnswer = (advice.conversationalAnswer || advice.recommendation || '')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/• (.*?)(?=\n|$)/g, '<div style="margin:3px 0 3px 12px;">&bull; $1</div>')
      .replace(/\n\n/g, '<div style="height:8px;"></div>')
      .replace(/\n/g, '<br>');

    // Search results table (if candidate search)
    let searchTableHtml = '';
    if (advice.searchResults && advice.searchResults.length > 0) {
      searchTableHtml = `
        <div style="margin-top:10px; overflow-x:auto;">
          <table style="width:100%; border-collapse:collapse; font-size:12px;">
            <thead>
              <tr style="border-bottom:1px solid var(--border); color:var(--text-muted); text-align:left;">
                <th style="padding:6px;">Candidate</th>
                <th style="padding:6px;">Location</th>
                <th style="padding:6px;">Available</th>
                <th style="padding:6px;">Skill Match</th>
                <th style="padding:6px;">Eligibility</th>
              </tr>
            </thead>
            <tbody>
              ${advice.searchResults.map(c => `
                <tr style="border-bottom:1px solid rgba(255,255,255,0.04);">
                  <td style="padding:6px; font-weight:600;">${Utils.escapeHtml(c.employeeName)}</td>
                  <td style="padding:6px; color:var(--text-secondary);">${Utils.escapeHtml(c.location || 'Any')}</td>
                  <td style="padding:6px;"><span class="badge ${c.availablePercent >= 50 ? 'badge-success' : 'badge-warning'}">${c.availablePercent}%</span></td>
                  <td style="padding:6px;">${Math.round(c.skillScore)}/100</td>
                  <td style="padding:6px;">
                    <span class="badge ${c.eligible ? 'badge-success' : 'badge-danger'}">
                      ${c.eligible ? 'ELIGIBLE' : 'INELIGIBLE'}
                    </span>
                  </td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    // Follow-up suggestion chips
    let followUpsHtml = '';
    if (advice.suggestedFollowUps && advice.suggestedFollowUps.length > 0 && isFirstRecommendation) {
      followUpsHtml = `
        <div style="margin-top:10px; padding-top:8px; border-top:1px dashed var(--border);">
          <span style="font-size:11px; color:var(--text-muted); font-weight:600;">Suggested follow-ups:</span>
          <div style="display:flex; flex-wrap:wrap; gap:6px; margin-top:6px;">
            ${advice.suggestedFollowUps.map(fu => `
              <button class="btn btn-sm btn-secondary" style="font-size:11px; padding:3px 8px;" onclick="AiAssistant.quickAsk('${Utils.escapeHtml(fu).replace(/'/g, "\\'")}')">
                ${Utils.escapeHtml(fu)}
              </button>
            `).join('')}
          </div>
        </div>
      `;
    }

    let html;
    if (isFirstRecommendation) {
      // First recommendation: show compact Agent Activity section + conversational answer
      const agentActivityHtml = `
        <div class="agent-activity-box" style="margin-bottom:10px; padding:10px 12px; background:rgba(15,23,42,0.6); border:1px solid rgba(99,102,241,0.25); border-radius:8px; font-size:11px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px; border-bottom:1px solid rgba(255,255,255,0.06); padding-bottom:5px;">
            <span style="font-weight:700; color:var(--accent-primary); letter-spacing:0.5px;">⚙️ AGENT ACTIVITY</span>
            <span class="badge" style="background:rgba(99,102,241,0.15); color:var(--accent-primary); border:1px solid rgba(99,102,241,0.3); font-size:9px; padding:2px 8px; border-radius:12px;">AI MODE: SIMULATED AGENT</span>
          </div>
          <div style="display:grid; grid-template-columns: 80px 1fr; row-gap:5px; font-size:11.5px; line-height:1.4;">
            <div style="color:var(--text-muted); font-weight:600;">Goal:</div>
            <div style="color:var(--accent-cyan); font-weight:600;">${Utils.escapeHtml(advice.userGoal || 'CANDIDATE_RECOMMENDATION')}</div>

            <div style="color:var(--text-muted); font-weight:600;">Tools:</div>
            <div style="font-family:monospace; color:var(--text-secondary);">${Utils.escapeHtml(toolsExecuted)}</div>

            <div style="color:var(--text-muted); font-weight:600;">Evidence:</div>
            <div style="color:var(--text-secondary);">${Utils.escapeHtml(advice.evidenceSummary || 'Live JPA database records evaluated.')}</div>

            <div style="color:var(--text-muted); font-weight:600;">Validation:</div>
            <div style="color:var(--text-secondary);">${Utils.escapeHtml(advice.validationResult || 'Rules validated against existing business logic.')}</div>

            <div style="color:var(--text-muted); font-weight:600;">Outcome:</div>
            <div><span class="badge badge-success" style="font-size:10px; font-weight:600;">${Utils.escapeHtml(advice.outcome || 'RECOMMENDED')}</span></div>
          </div>
        </div>
      `;

      html = `
        <div style="display:flex; flex-direction:column; gap:6px;">
          ${agentActivityHtml}
          <div style="font-size:13.5px; line-height:1.6; color:var(--text-primary);">
            ${formattedAnswer}
          </div>
          ${followUpsHtml}
        </div>
      `;
    } else {
      // Follow-up question: Short, natural conversational answer without report-like behavior
      // Agent Activity and tool evidence are updated in the right-hand supporting UI
      html = `
        <div style="display:flex; flex-direction:column; gap:6px;">
          <div style="font-size:11px; color:var(--text-muted); display:flex; justify-content:space-between; align-items:center; margin-bottom:2px;">
            <span style="color:var(--accent-cyan); font-weight:600; font-size:11px;">⚡ ${Utils.escapeHtml(advice.userGoal || 'STAFFING_WORKFLOW')}</span>
            <span style="font-size:10px; color:var(--accent-primary); font-weight:500;">AI MODE: SIMULATED AGENT</span>
          </div>
          <div style="font-size:13.5px; line-height:1.6; color:var(--text-primary);">
            ${formattedAnswer}
          </div>
          ${searchTableHtml}
        </div>
      `;
    }

    AiAssistant.appendAgentMessage(html);
  },

  async handleApproveAdvice() {
    if (!AiAssistant.currentStaffingAdvice) return;
    const advice = AiAssistant.currentStaffingAdvice;
    const token = advice.approvalToken;
    const demandId = advice.demandId;
    const employeeId = advice.topCandidate?.employeeId;

    if (!token || !employeeId) {
      Utils.showToast('No approved candidate selected', 'error');
      return;
    }

    Utils.setLoading(true);
    try {
      const response = await Api.approveAiRecommendation(token, demandId, employeeId, 'Delivery Director');
      Utils.showToast(`Allocation created via AI approval workflow!`, 'success');

      AiAssistant.appendAgentMessage(`
        <div style="color:var(--accent-green);">
          <strong>✓ Allocation Confirmed & Revalidated!</strong><br>
          ${Utils.escapeHtml(response.message)}<br>
          <span style="font-size:12px; color:var(--text-secondary);">
            Commitment status: <strong>${response.allocation?.status || 'APPROVED'}</strong> &bull; Revalidation check: PASS.
          </span>
        </div>
      `);

      document.getElementById('ai-approval-gate-card').style.display = 'none';
    } catch (err) {
      console.error('Failed to approve AI advice:', err);
      Utils.showToast(err.message || 'Approval revalidation failed', 'error');
      AiAssistant.appendAgentMessage(`
        <div style="color:var(--accent-red);">
          <strong>✕ Approval Revalidation Failed:</strong> ${Utils.escapeHtml(err.message || 'Capacity conflict detected at commit time')}
        </div>
      `);
    } finally {
      Utils.setLoading(false);
    }
  },

  appendUserMessage(text) {
    const container = document.getElementById('chat-messages-container');
    if (!container) return;

    const msg = document.createElement('div');
    msg.className = 'chat-message user';
    msg.innerHTML = `
      <div class="message-avatar user-avatar">👤</div>
      <div class="message-bubble user-bubble">${Utils.escapeHtml(text)}</div>
    `;
    container.appendChild(msg);
    container.scrollTop = container.scrollHeight;
  },

  appendAgentMessage(htmlContent) {
    const container = document.getElementById('chat-messages-container');
    if (!container) return;

    const msg = document.createElement('div');
    msg.className = 'chat-message';
    msg.innerHTML = `
      <div class="message-avatar">🤖</div>
      <div class="message-bubble">${htmlContent}</div>
    `;
    container.appendChild(msg);
    container.scrollTop = container.scrollHeight;
  },

  appendTypingIndicator() {
    const container = document.getElementById('chat-messages-container');
    const msg = document.createElement('div');
    msg.className = 'chat-message';
    msg.innerHTML = `
      <div class="message-avatar">🤖</div>
      <div class="message-bubble" style="display:flex; align-items:center; gap:8px;">
        <div class="spinner-sm"></div>
        <span style="font-size:12px; color:var(--text-muted);">Agent is interpreting goal and executing tools...</span>
      </div>
    `;
    container.appendChild(msg);
    container.scrollTop = container.scrollHeight;
    return msg;
  }
};
