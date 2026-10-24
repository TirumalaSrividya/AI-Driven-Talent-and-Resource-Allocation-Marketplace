/**
 * TalentAI - Centralized API Service
 * Interacts with Spring Boot REST Backend at http://localhost:8080/api
 */
const API_BASE = 'http://localhost:8080/api';

const Api = {
  async request(endpoint, options = {}) {
    const url = `${API_BASE}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    try {
      const response = await fetch(url, { ...options, headers });
      if (!response.ok) {
        let errData;
        try {
          errData = await response.json();
        } catch (e) {
          errData = { message: `HTTP ${response.status}: ${response.statusText}` };
        }
        const error = new Error(errData.message || 'API request failed');
        error.status = response.status;
        error.data = errData;
        throw error;
      }
      if (response.status === 204) return null;
      return await response.json();
    } catch (err) {
      console.error(`API Request Error [${endpoint}]:`, err);
      throw err;
    }
  },

  // Health check
  async checkHealth() {
    try {
      await fetch(`${API_BASE}/skills`, { method: 'GET' });
      return true;
    } catch (e) {
      return false;
    }
  },

  // Demands
  async getDemands(params = {}) {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.skillId) query.append('skillId', params.skillId);
    if (params.startDate) query.append('startDate', params.startDate);
    if (params.endDate) query.append('endDate', params.endDate);
    const qs = query.toString() ? `?${query.toString()}` : '';
    return this.request(`/demands${qs}`);
  },

  async getDemand(id) {
    return this.request(`/demands/${id}`);
  },

  async getOpenDemandsByPriority() {
    return this.request('/demands/open-by-priority');
  },

  async getUnallocatedDemands() {
    return this.request('/demands/unallocated');
  },

  async createDemand(data) {
    return this.request('/demands', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async deleteDemand(id) {
    return this.request(`/demands/${id}`, {
      method: 'DELETE',
    });
  },

  async getRecommendations(demandId) {
    return this.request(`/demands/${demandId}/recommendations`, {
      method: 'POST',
    });
  },

  // Employees
  async getEmployees() {
    return this.request('/employees');
  },

  async getEmployee(id) {
    return this.request(`/employees/${id}`);
  },

  async createEmployee(data) {
    return this.request('/employees', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async getEmployeeSkills(id) {
    return this.request(`/employees/${id}/skills`);
  },

  async addEmployeeSkill(employeeId, data) {
    return this.request(`/employees/${employeeId}/skills`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  // Skills
  async getSkills(category) {
    const qs = category ? `?category=${encodeURIComponent(category)}` : '';
    return this.request(`/skills${qs}`);
  },

  async createSkill(data) {
    return this.request('/skills', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  // Resources & Availability
  async getAvailableResources(params) {
    const query = new URLSearchParams({
      startDate: params.startDate,
      endDate: params.endDate,
    });
    if (params.skillId) query.append('skillId', params.skillId);
    if (params.location) query.append('location', params.location);
    return this.request(`/resources/available?${query.toString()}`);
  },

  async getEmployeeAvailability(employeeId, startDate, endDate, requestedPercent = 100) {
    const query = new URLSearchParams({
      startDate,
      endDate,
      requestedPercent: requestedPercent.toString(),
    });
    return this.request(`/resources/availability/${employeeId}?${query.toString()}`);
  },

  // Allocations
  async getAllocations() {
    return this.request('/allocations');
  },

  async getAllocation(id) {
    return this.request(`/allocations/${id}`);
  },

  async createAllocation(data) {
    return this.request('/allocations', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async approveAllocation(id, approvedBy) {
    return this.request(`/allocations/${id}/approve`, {
      method: 'PUT',
      body: JSON.stringify({ approvedBy }),
    });
  },

  async cancelAllocation(id, cancelledBy, cancellationReason) {
    return this.request(`/allocations/${id}/cancel`, {
      method: 'PUT',
      body: JSON.stringify({ cancelledBy, cancellationReason }),
    });
  },

  // Reports
  async getUtilizationReport(startDate, endDate) {
    const query = new URLSearchParams();
    if (startDate) query.append('startDate', startDate);
    if (endDate) query.append('endDate', endDate);
    const qs = query.toString() ? `?${query.toString()}` : '';
    return this.request(`/reports/utilization${qs}`);
  },

  async getSkillGapReport() {
    return this.request('/reports/skill-gaps');
  },

  // AI Agent Simulation
  async getAiStaffingAdvice(demandId, question = '', requestedBy = 'Staffing Manager', contextCandidateName = '', previousCandidateName = '', lastGoal = '') {
    return this.request('/ai/staffing-advice', {
      method: 'POST',
      body: JSON.stringify({ demandId, question, requestedBy, contextCandidateName, previousCandidateName, lastGoal }),
    });
  },

  async approveAiRecommendation(approvalToken, demandId, employeeId, approvedBy = 'Staffing Lead') {
    return this.request('/ai/approve-allocation', {
      method: 'POST',
      body: JSON.stringify({ approvalToken, demandId, employeeId, approvedBy }),
    });
  },
};
