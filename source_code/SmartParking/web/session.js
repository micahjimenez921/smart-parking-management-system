/* Who is signed in: the menus each role sees, logging out, and the My account page.
   This file loads first. It uses helpers from app.js, flows.js, pages.js, gates.js and settings.js,
   but only after the page has finished loading. */

const session = { user: null };

/* each menu page and the permission it needs (null means any signed-in user) */
const VIEW_PERMISSION = {
  dashboard: "DASHBOARD",
  entry: "ENTRY",
  exit: "EXIT",
  tickets: "TICKETS",
  reports: "REPORTS",
  settings: "SETTINGS",
  account: null
};
const VIEW_ORDER = ["dashboard", "entry", "exit", "tickets", "reports", "settings", "account"];
const VIEW_LABEL = {
  dashboard: "Dashboard",
  entry: "Vehicle entry",
  exit: "Exit and payment",
  tickets: "Tickets",
  reports: "Reports",
  settings: "Settings"
};

const ROLE_LABEL = { ADMINISTRATOR: "Administrator", ATTENDANT: "Parking attendant", DRIVER: "Driver" };
const SHIFT_LABEL = { DAY: "Day shift", EVENING: "Evening shift", NIGHT: "Night shift" };

/* the browser signs out by itself after this long without a click or a key press */
const IDLE_LIMIT_MS = 30 * 60 * 1000;

/* ---------- who may do what ---------- */

function canUse(permission) {
  return !!session.user && session.user.permissions.includes(permission);
}

function canSeeView(name) {
  if (!session.user) return true; /* not known yet, the page is still loading */
  if (!(name in VIEW_PERMISSION)) return false;
  const needed = VIEW_PERMISSION[name];
  return needed === null || canUse(needed);
}

function firstAllowedView() {
  return VIEW_ORDER.find(name => canSeeView(name)) || "account";
}

/* ---------- going to the sign-in page ---------- */

function goToLogin() {
  location.replace("login.html");
}

/* any answer of "please sign in" (an ended session) sends the person to the sign-in page */
const realFetch = window.fetch.bind(window);

window.fetch = async function (input, init) {
  const response = await realFetch(input, init);
  const url = typeof input === "string" ? input : input.url;
  if (response.status === 401 && !url.startsWith("/api/login")) goToLogin();
  return response;
};

/* ---------- finding out who is signed in ---------- */

async function loadSession() {
  const response = await realFetch("/api/me");
  if (response.status === 401) {
    goToLogin();
    return null;
  }
  if (!response.ok) throw new Error("Could not check who is signed in (" + response.status + ").");
  return response.json();
}

const pageLoaded = new Promise(resolve => {
  if (document.readyState !== "loading") {
    resolve();
  } else {
    document.addEventListener("DOMContentLoaded", resolve);
  }
});

Promise.all([loadSession(), pageLoaded])
  .then(results => {
    if (!results[0]) return;
    session.user = results[0];
    applySession();
  })
  .catch(error => {
    console.error(error);
    document.body.classList.remove("pending");
    document.getElementById("offline").hidden = false;
  });

/* ---------- showing the right menus ---------- */

function initials(name) {
  const words = name.trim().split(/\s+/).filter(word => word !== "");
  const letters = words.slice(0, 2).map(word => word[0].toUpperCase()).join("");
  return letters || "?";
}

function applySession() {
  const user = session.user;

  document.getElementById("user-name").textContent = user.name;
  document.getElementById("user-avatar").textContent = initials(user.name);

  for (const button of document.querySelectorAll(".nav-item")) {
    button.hidden = !canSeeView(button.dataset.view);
  }
  document.getElementById("search-box").hidden = !canUse("DASHBOARD");

  renderAccount();
  watchIdle();

  document.body.classList.remove("pending");
  showView(location.hash.replace("#", ""));

  /* start the pages this person may use; each one skips itself when the permission is missing */
  refresh();
  refreshGates();
  loadCurrentPage();
  if (currentView() === "settings") loadSettingsPage();
}

/* ---------- My account page ---------- */

function renderAccount() {
  const user = session.user;
  const rows = document.getElementById("account-rows");
  rows.innerHTML = "";

  rows.appendChild(row("Name", user.name));
  rows.appendChild(row("Username", user.username));
  rows.appendChild(row("Role", ROLE_LABEL[user.role] || user.role));
  if (user.role === "ATTENDANT") {
    rows.appendChild(row("Shift", SHIFT_LABEL[user.detail] || user.detail));
  }
  if (user.role === "DRIVER") {
    rows.appendChild(row("Plate numbers", user.detail ? user.detail.split(";").join(", ") : "None yet"));
  }
  rows.appendChild(row("Account ID", user.id));

  const menus = make("ul", "menu-list");
  for (const name of VIEW_ORDER) {
    if (name !== "account" && canSeeView(name)) menus.appendChild(make("li", "", VIEW_LABEL[name]));
  }
  if (menus.children.length === 0) menus.appendChild(make("li", "", "My account only"));

  const line = make("div", "row");
  line.appendChild(make("span", "k", "Can open"));
  line.appendChild(menus);
  rows.appendChild(line);
}

async function changePassword(event) {
  event.preventDefault();
  const message = document.getElementById("password-msg");
  const current = document.getElementById("pw-current").value;
  const wanted = document.getElementById("pw-new").value;
  const again = document.getElementById("pw-again").value;

  if (current === "" || wanted === "") {
    setMessage(message, "Enter your current password and a new one.", "error");
    return;
  }
  if (wanted !== again) {
    setMessage(message, "The two new passwords are not the same.", "error");
    return;
  }

  try {
    await postForm("/api/password", { current: current, newPassword: wanted });
    document.getElementById("password-form").reset();
    setMessage(message, "Password changed. Your other sessions were signed out.", "success");
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

/* ---------- logging out ---------- */

async function logout() {
  try {
    await postForm("/api/logout", {});
  } catch (error) {
    /* signing out must always work for the person, even if the server cannot be reached */
  }
  goToLogin();
}

let lastActivity = Date.now();

function watchIdle() {
  for (const name of ["pointerdown", "keydown"]) {
    document.addEventListener(name, () => { lastActivity = Date.now(); });
  }
  setInterval(() => {
    if (Date.now() - lastActivity > IDLE_LIMIT_MS) logout();
  }, 30 * 1000);
}

document.getElementById("logout-button").addEventListener("click", logout);
document.getElementById("account-logout").addEventListener("click", logout);
document.getElementById("password-form").addEventListener("submit", changePassword);
