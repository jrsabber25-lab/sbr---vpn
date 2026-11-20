let apiBase = "";
let authToken = "";

const loginView = document.getElementById("login-view");
const dashboardView = document.getElementById("dashboard-view");
const loginError = document.getElementById("login-error");

function formatBytes(bytes) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`;
}

function formatUptime(sec) {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  return `${h}h ${m}m`;
}

async function apiFetch(path, options = {}) {
  const res = await fetch(`${apiBase}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...(options.headers || {}),
    },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(err.detail || "Request failed");
  }
  return res.json();
}

document.getElementById("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  loginError.textContent = "";
  apiBase = document.getElementById("api-url").value.trim().replace(/\/+$/, "");
  const email = document.getElementById("admin-email").value.trim();
  const password = document.getElementById("admin-password").value;

  try {
    const data = await apiFetch("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (data.user.role !== "admin") {
      throw new Error("Access denied: Administrator role required.");
    }
    authToken = data.access_token;
    loginView.classList.add("hidden");
    dashboardView.classList.remove("hidden");
    await refreshAll();
  } catch (err) {
    loginError.textContent = err.message;
  }
});

document.getElementById("logout-btn").addEventListener("click", () => {
  authToken = "";
  dashboardView.classList.add("hidden");
  loginView.classList.remove("hidden");
});

document.querySelectorAll(".nav-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".nav-btn").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const tab = btn.dataset.tab;
    document.querySelectorAll(".tab-panel").forEach((p) => p.classList.add("hidden"));
    document.getElementById(`tab-${tab}`).classList.remove("hidden");
  });
});

document.getElementById("refresh-all-btn").addEventListener("click", refreshAll);

document.getElementById("add-server-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const [country_code, country_name] = document.getElementById("srv-region").value.split("|");
  await apiFetch("/api/v1/admin/servers", {
    method: "POST",
    body: JSON.stringify({
      country_code,
      country_name,
      city: document.getElementById("srv-city").value.trim(),
      hostname: document.getElementById("srv-host").value.trim(),
      wg_port: Number(document.getElementById("srv-port").value),
      agent_url: document.getElementById("srv-agent").value.trim(),
      public_key: document.getElementById("srv-pubkey").value.trim(),
      tier: document.getElementById("srv-tier").value,
    }),
  });
  e.target.reset();
  await refreshAll();
});

async function refreshAll() {
  const [overview, servers, sessions, users, errors] = await Promise.all([
    apiFetch("/api/v1/admin/overview"),
    apiFetch("/api/v1/admin/servers"),
    apiFetch("/api/v1/admin/sessions"),
    apiFetch("/api/v1/admin/users"),
    apiFetch("/api/v1/admin/errors"),
  ]);

  document.getElementById("kpi-online").textContent = `${overview.online_servers} / ${overview.total_servers}`;
  document.getElementById("kpi-sessions").textContent = overview.active_sessions;
  document.getElementById("kpi-users").textContent = overview.total_users;
  document.getElementById("kpi-bandwidth").textContent = formatBytes(overview.total_bandwidth_bytes);

  const srvBody = document.getElementById("servers-tbody");
  srvBody.innerHTML = "";
  servers.forEach((s) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${s.country_name} (${s.country_code}) — ${s.city}</td>
      <td>${s.hostname}:${s.wg_port}</td>
      <td>${s.tier.toUpperCase()}</td>
      <td class="${s.is_online && s.is_enabled ? "badge-online" : "badge-offline"}">
        ${s.is_enabled ? (s.is_online ? "ONLINE" : "UNREACHABLE") : "DISABLED"}
      </td>
      <td>${s.cpu_percent}% / ${s.ram_percent}%</td>
      <td>${formatUptime(s.uptime_seconds)}</td>
      <td>${formatBytes(s.total_bandwidth_bytes)}</td>
      <td>
        <button class="btn-outline btn-sm" onclick="pollServer('${s.id}')">Poll</button>
        <button class="btn-outline btn-sm" onclick="toggleServer('${s.id}', ${!s.is_enabled})">${s.is_enabled ? "Disable" : "Enable"}</button>
        <button class="btn-danger" onclick="deleteServer('${s.id}')">Remove</button>
      </td>`;
    srvBody.appendChild(tr);
  });

  const sesBody = document.getElementById("sessions-tbody");
  sesBody.innerHTML = sessions
    .map((x) => `<tr><td>${x.id.slice(0, 8)}</td><td>${x.user_id.slice(0, 8)}</td><td>${x.server_id.slice(0, 8)}</td><td>${x.assigned_ip}</td><td>${x.protocol}</td><td>${x.started_at}</td></tr>`)
    .join("");

  const usrBody = document.getElementById("users-tbody");
  usrBody.innerHTML = users
    .map((u) => `
      <tr>
        <td>${u.display_name}</td>
        <td>${u.email || "Guest"}</td>
        <td>${u.subscription_tier.toUpperCase()}</td>
        <td>${u.max_devices}</td>
        <td>${u.role}</td>
        <td>
          <button class="btn-outline btn-sm" onclick="setTier('${u.id}', '${u.subscription_tier === "premium" ? "free" : "premium"}')">
            Toggle Plan
          </button>
        </td>
      </tr>`)
    .join("");

  const errBody = document.getElementById("errors-tbody");
  errBody.innerHTML = errors
    .map((er) => `<tr><td>${er.created_at}</td><td>${er.component}</td><td>${er.severity}</td><td>${er.message}</td></tr>`)
    .join("");
}

window.pollServer = async (id) => {
  await apiFetch(`/api/v1/admin/servers/${id}/poll-health`, { method: "POST" });
  await refreshAll();
};

window.toggleServer = async (id, is_enabled) => {
  await apiFetch(`/api/v1/admin/servers/${id}`, {
    method: "PATCH",
    body: JSON.stringify({ is_enabled }),
  });
  await refreshAll();
};

window.deleteServer = async (id) => {
  await apiFetch(`/api/v1/admin/servers/${id}`, { method: "DELETE" });
  await refreshAll();
};

window.setTier = async (id, subscription_tier) => {
  await apiFetch(`/api/v1/admin/users/${id}`, {
    method: "PATCH",
    body: JSON.stringify({
      subscription_tier,
      max_devices: subscription_tier === "premium" ? 5 : 1,
    }),
  });
  await refreshAll();
};
