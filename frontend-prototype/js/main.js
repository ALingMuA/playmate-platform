/* ===== Shared interactions for frontend-prototype ===== */

document.addEventListener("DOMContentLoaded", () => {
  initMobileMenu();
  initTabs();
  initAuthTabs();
  initChat();
  initStarRating();
});

/* Mobile menu toggle */
function initMobileMenu() {
  const toggle = document.querySelector(".mobile-menu-btn");
  const nav = document.querySelector(".main-nav");
  if (!toggle || !nav) return;

  toggle.addEventListener("click", () => {
    nav.classList.toggle("open");
    const isOpen = nav.classList.contains("open");
    toggle.setAttribute("aria-expanded", String(isOpen));
  });

  // Close menu when a link is clicked
  nav.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => {
      nav.classList.remove("open");
      toggle.setAttribute("aria-expanded", "false");
    });
  });
}

/* Generic tab switching */
function initTabs() {
  document.querySelectorAll("[data-tab]").forEach((trigger) => {
    trigger.addEventListener("click", () => {
      const group = trigger.dataset.tabGroup || "default";
      const target = trigger.dataset.tab;

      // Update triggers
      document.querySelectorAll(`[data-tab][data-tab-group="${group}"]`).forEach((btn) => {
        btn.classList.remove("active");
      });
      trigger.classList.add("active");

      // Update panels
      document.querySelectorAll(`[data-tab-panel][data-tab-group="${group}"]`).forEach((panel) => {
        panel.classList.remove("active");
      });
      const panel = document.querySelector(`[data-tab-panel="${target}"][data-tab-group="${group}"]`);
      if (panel) panel.classList.add("active");
    });
  });
}

/* Auth page login/register switching */
function initAuthTabs() {
  const authTabs = document.querySelectorAll(".auth-tab");
  if (!authTabs.length) return;

  authTabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      authTabs.forEach((t) => t.classList.remove("active"));
      tab.classList.add("active");

      const target = tab.dataset.target;
      document.querySelectorAll(".auth-panel").forEach((panel) => {
        panel.classList.toggle("active", panel.id === target);
      });
    });
  });
}

/* Chat simulation */
function initChat() {
  const form = document.querySelector(".chat-form");
  const body = document.querySelector(".chat-body");
  const input = document.querySelector(".chat-form input");
  if (!form || !body || !input) return;

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;

    appendMessage("user", text);
    input.value = "";
    scrollToBottom(body);

    setTimeout(() => {
      appendMessage("ai", "收到您的问题，AI 客服正在为您查询，请稍候。如需人工服务，可点击右上角“转人工”按钮。");
      scrollToBottom(body);
    }, 800);
  });
}

function appendMessage(sender, text) {
  const body = document.querySelector(".chat-body");
  const wrapper = document.createElement("div");
  wrapper.className = `chat-message ${sender}`;

  const avatar = sender === "ai"
    ? `<div class="chat-avatar"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2"/><path d="M20 14h2"/><path d="M15 13v2"/><path d="M9 13v2"/></svg></div>`
    : `<div class="chat-avatar"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></div>`;

  const name = sender === "ai" ? `<div class="chat-name">AI 客服</div>` : "";

  wrapper.innerHTML = `${avatar}<div>${name}<div class="chat-bubble">${escapeHtml(text)}</div></div>`;
  body.appendChild(wrapper);
}

function scrollToBottom(el) {
  el.scrollTop = el.scrollHeight;
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

/* Star rating: keep selected stars highlighted */
function initStarRating() {
  const containers = document.querySelectorAll(".star-rating.reverse");
  containers.forEach((container) => {
    const inputs = container.querySelectorAll("input");
    inputs.forEach((input) => {
      input.addEventListener("change", () => {
        container.querySelectorAll("label").forEach((label) => {
          label.style.color = "";
        });
      });
    });
  });
}
