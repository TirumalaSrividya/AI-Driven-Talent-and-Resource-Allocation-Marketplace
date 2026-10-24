/**
 * TalentAI - Application Router & Main Controller
 */
const App = {
  currentScreen: 'dashboard',
  screenModules: {
    dashboard: Dashboard,
    demands: Demands,
    employees: Employees,
    search: Search,
    compare: Compare,
    approvals: Approvals,
    'ai-assistant': AiAssistant,
  },

  async init() {
    console.log('⚡ Initializing TalentAI Application...');

    // Setup navigation links
    const navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach(link => {
      link.addEventListener('click', (e) => {
        e.preventDefault();
        const screen = link.getAttribute('data-screen');
        if (screen) App.navigateTo(screen);
      });
    });

    // Sidebar toggle for mobile
    const toggleBtn = document.getElementById('sidebar-toggle');
    const sidebar = document.getElementById('sidebar');
    toggleBtn?.addEventListener('click', () => {
      sidebar?.classList.toggle('open');
    });

    // Close sidebar when clicking outside on mobile
    document.addEventListener('click', (e) => {
      if (window.innerWidth <= 768 && sidebar?.classList.contains('open')) {
        if (!sidebar.contains(e.target) && e.target !== toggleBtn) {
          sidebar.classList.remove('open');
        }
      }
    });

    // Check backend connection
    await App.checkApiConnection();
    setInterval(() => App.checkApiConnection(), 15000);

    // Initial load: Dashboard
    await App.navigateTo('dashboard');
  },

  async checkApiConnection() {
    const statusText = document.getElementById('api-status');
    const statusDot = document.querySelector('.status-dot');

    try {
      const isHealthy = await Api.checkHealth();
      if (isHealthy) {
        if (statusText) statusText.textContent = 'Backend Online (Port 8080)';
        if (statusDot) statusDot.classList.remove('error');
      } else {
        if (statusText) statusText.textContent = 'Backend Disconnected';
        if (statusDot) statusDot.classList.add('error');
      }
    } catch (e) {
      if (statusText) statusText.textContent = 'Backend Offline';
      if (statusDot) statusDot.classList.add('error');
    }
  },

  async navigateTo(screenId) {
    App.currentScreen = screenId;

    // Update active nav link
    document.querySelectorAll('.nav-link').forEach(link => {
      link.classList.toggle('active', link.getAttribute('data-screen') === screenId);
    });

    // Update active screen container
    document.querySelectorAll('.screen').forEach(screen => {
      screen.classList.toggle('active', screen.id === `screen-${screenId}`);
    });

    // Update topbar title
    const titles = {
      dashboard: 'Dashboard & Capacity Overview',
      demands: 'Project Demands & Requirements',
      employees: 'Resource Directory & Skills Matrix',
      search: 'Resource Availability Search',
      compare: 'Candidate Evaluation & Transparent Scoring',
      approvals: 'Resource Allocations & Approvals',
      'ai-assistant': 'AI Staffing Assistant (Agentic Simulation)',
    };
    const topbarTitle = document.getElementById('topbar-title');
    if (topbarTitle) topbarTitle.textContent = titles[screenId] || 'TalentAI';

    // Close mobile sidebar
    document.getElementById('sidebar')?.classList.remove('open');

    // Initialize or refresh target screen
    const module = App.screenModules[screenId];
    if (module && typeof module.init === 'function') {
      try {
        await module.init();
      } catch (err) {
        console.error(`Error initializing screen [${screenId}]:`, err);
      }
    }
  }
};

// Auto-boot application on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  App.init();
});
