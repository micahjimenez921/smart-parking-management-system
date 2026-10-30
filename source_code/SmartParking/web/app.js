/* ---------- state ---------- */

const state = {
  slots: [],
  filter: "ALL",
  selectedId: null
};

const VIEWS = ["dashboard", "entry", "exit", "tickets", "reports"];

const TYPE_LABEL = { REGULAR: "Regular", HANDICAPPED: "Handicapped", VIP: "VIP" };
const TYPE_CLASS = { REGULAR: "type-regular", HANDICAPPED: "type-handicapped", VIP: "type-vip" };

const ICON_MOON =
  '<svg class="ico" viewBox="0 0 24 24"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>';
const ICON_SUN =
  '<svg class="ico" viewBox="0 0 24 24"><circle cx="12" cy="12" r="5"/>' +
  '<line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/>' +
  '<line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>' +
  '<line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/>' +
  '<line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>';

/* top-down vehicle drawings, one per vehicle type */

const VEHICLE_SVG = {
  CAR:
    '<svg viewBox="0 0 40 72" aria-hidden="true">' +
    '<rect x="2" y="12" width="5" height="13" rx="2" fill="var(--wheel)"/>' +
    '<rect x="33" y="12" width="5" height="13" rx="2" fill="var(--wheel)"/>' +
    '<rect x="2" y="47" width="5" height="13" rx="2" fill="var(--wheel)"/>' +
    '<rect x="33" y="47" width="5" height="13" rx="2" fill="var(--wheel)"/>' +
    '<rect x="6" y="3" width="28" height="66" rx="12" fill="currentColor"/>' +
    '<rect x="10" y="15" width="20" height="13" rx="5" fill="#ffffff" opacity="0.9"/>' +
    '<rect x="11" y="46" width="18" height="10" rx="4" fill="#ffffff" opacity="0.65"/>' +
    '</svg>',
  MOTORCYCLE:
    '<svg viewBox="0 0 40 72" aria-hidden="true">' +
    '<rect x="16" y="2" width="8" height="16" rx="4" fill="var(--wheel)"/>' +
    '<rect x="16" y="54" width="8" height="16" rx="4" fill="var(--wheel)"/>' +
    '<rect x="15" y="12" width="10" height="46" rx="5" fill="currentColor"/>' +
    '<rect x="6" y="18" width="28" height="5" rx="2.5" fill="var(--wheel)"/>' +
    '<rect x="13" y="32" width="14" height="14" rx="6" fill="#ffffff" opacity="0.8"/>' +
    '</svg>',
  TRUCK:
    '<svg viewBox="0 0 40 72" aria-hidden="true">' +
    '<rect x="2" y="8" width="5" height="12" rx="2" fill="var(--wheel)"/>' +
    '<rect x="33" y="8" width="5" height="12" rx="2" fill="var(--wheel)"/>' +
    '<rect x="2" y="50" width="5" height="14" rx="2" fill="var(--wheel)"/>' +
    '<rect x="33" y="50" width="5" height="14" rx="2" fill="var(--wheel)"/>' +
    '<rect x="6" y="2" width="28" height="22" rx="9" fill="currentColor"/>' +
    '<rect x="10" y="7" width="20" height="9" rx="3" fill="#ffffff" opacity="0.9"/>' +
    '<rect x="7" y="27" width="26" height="42" rx="4" fill="currentColor" opacity="0.72"/>' +
    '</svg>'
};

/* ---------- small helpers ---------- */

function make(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function money(value) {
  return "\u20B1" + Number(value).toLocaleString("en-PH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function formatEntry(isoText) {
  const date = new Date(isoText.replace(/(\.\d{3})\d+/, "$1"));
  if (isNaN(date.getTime())) return isoText;
  const day = date.toLocaleDateString("en-PH", { month: "short", day: "numeric" });
  const time = date.toLocaleTimeString("en-PH", { hour: "numeric", minute: "2-digit" });
  return day + ", " + time;
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(url + " returned " + response.status);
  return response.json();
}

function typeChip(type) {
  return make("span", "type-chip " + (TYPE_CLASS[type] || ""), TYPE_LABEL[type] || type);
}

/* ---------- rendering ---------- */

function renderStats(stats) {
  document.getElementById("stat-total").textContent = stats.total;
  document.getElementById("stat-available").textContent = stats.available;
  document.getElementById("stat-occupied").textContent = stats.occupied;
  document.getElementById("stat-revenue").textContent = money(stats.revenue);

  const percent = stats.total > 0 ? Math.round((stats.occupied / stats.total) * 100) : 0;
  const kinds = new Set(state.slots.map(s => s.type)).size;

  document.getElementById("meta-total").textContent = "Across " + kinds + " slot types";
  document.getElementById("meta-available").textContent = "Ready for parking";
  document.getElementById("meta-occupied").textContent = percent + "% of the lot";
  document.getElementById("meta-revenue").textContent = "Collected payments";

  if (stats.lotName) {
    document.getElementById("lot-subtitle").textContent = "Live overview of " + stats.lotName;
  }
}

function renderBays() {
  const container = document.getElementById("bays");
  container.innerHTML = "";

  const visible = state.slots.filter(s => state.filter === "ALL" || s.type === state.filter);

  if (visible.length === 0) {
    container.appendChild(make("p", "empty", "No slots of this type."));
    return;
  }

  for (const slot of visible) {
    const bay = make("button", "bay " + (slot.available ? "free" : "busy"));
    bay.dataset.id = slot.id;
    if (slot.id === state.selectedId) bay.classList.add("selected");

    const top = make("div", "bay-top");
    top.appendChild(make("span", "bay-id", slot.id));
    top.appendChild(typeChip(slot.type));
    bay.appendChild(top);

    const art = make("div", "bay-art");
    if (slot.available) {
      art.appendChild(make("span", "bay-p", "P"));
    } else {
      art.innerHTML = VEHICLE_SVG[slot.vehicleType] || VEHICLE_SVG.CAR;
    }
    bay.appendChild(art);

    bay.appendChild(make("div", "bay-foot", slot.available ? "Available" : slot.plate));

    bay.addEventListener("click", () => selectSlot(slot.id));
    container.appendChild(bay);
  }
}

function row(key, value) {
  const line = make("div", "row");
  line.appendChild(make("span", "k", key));
  line.appendChild(make("span", "v", value));
  return line;
}

function renderDetail() {
  const card = document.getElementById("detail");
  card.innerHTML = "";

  const slot = state.slots.find(s => s.id === state.selectedId);
  if (!slot) {
    card.appendChild(make("h2", "", "Slot details"));
    card.appendChild(make("p", "detail-note", "Select a slot on the map."));
    return;
  }

  const head = make("div", "detail-head");
  head.appendChild(make("h2", "", "Slot " + slot.id));
  head.appendChild(make("span", "pill " + (slot.available ? "free" : "busy"),
    slot.available ? "Available" : "Occupied"));
  card.appendChild(head);

  const rows = make("div", "rows");
  rows.appendChild(row("Type", TYPE_LABEL[slot.type] || slot.type));
  rows.appendChild(row("Rate", money(slot.rate) + " / hour"));

  if (slot.available) {
    card.appendChild(rows);
    card.appendChild(make("p", "detail-note", "This slot is free. Register an arriving vehicle to assign it."));
    const button = make("button", "btn", "Park a vehicle");
    button.addEventListener("click", () => showView("entry"));
    card.appendChild(button);
    return;
  }

  rows.appendChild(row("Vehicle", slot.plate + " (" + slot.vehicleType.toLowerCase() + ")"));
  rows.appendChild(row("Owner", slot.owner));
  rows.appendChild(row("Ticket", slot.ticketId));
  rows.appendChild(row("Entered", formatEntry(slot.entryTime)));
  rows.appendChild(row("Hours charged", String(slot.hours)));
  card.appendChild(rows);

  const fee = make("div", "fee-box");
  fee.appendChild(make("div", "k", "Fee so far"));
  fee.appendChild(make("div", "v", money(slot.fee)));
  card.appendChild(fee);
}

function renderRates() {
  const list = document.getElementById("rates");
  list.innerHTML = "";

  const seen = new Map();
  for (const slot of state.slots) {
    if (!seen.has(slot.type)) seen.set(slot.type, slot.rate);
  }

  for (const [type, rate] of seen) {
    const item = make("li");
    item.appendChild(typeChip(type));
    item.appendChild(make("span", "amount", money(rate) + " / hr"));
    list.appendChild(item);
  }
}

/* ---------- actions ---------- */

function chooseDefaultSelection() {
  const stillThere = state.slots.some(s => s.id === state.selectedId);
  if (stillThere) return;
  const firstBusy = state.slots.find(s => !s.available);
  const first = firstBusy || state.slots[0];
  state.selectedId = first ? first.id : null;
}

function selectSlot(id) {
  state.selectedId = id;
  renderBays();
  renderDetail();
}

async function refresh() {
  try {
    const results = await Promise.all([getJson("/api/stats"), getJson("/api/slots")]);
    state.slots = results[1];
    chooseDefaultSelection();

    renderStats(results[0]);
    renderRates();
    renderBays();
    renderDetail();

    document.getElementById("offline").hidden = true;
  } catch (error) {
    console.error(error);
    document.getElementById("offline").hidden = false;
  }
}

function showView(name) {
  if (!VIEWS.includes(name)) name = "dashboard";

  for (const view of VIEWS) {
    document.getElementById("view-" + view).classList.toggle("active", view === name);
  }
  for (const button of document.querySelectorAll(".nav-item")) {
    button.classList.toggle("active", button.dataset.view === name);
  }
  window.scrollTo(0, 0);

  if (location.hash !== "#" + name) {
    location.hash = name;
  }
}

/* ---------- search ---------- */

function runSearch(text) {
  const box = document.getElementById("search-box");
  const query = text.trim().toLowerCase();

  if (query === "") {
    box.classList.remove("nomatch");
    return;
  }

  const found = state.slots.find(s =>
    s.id.toLowerCase().includes(query) ||
    (s.plate && s.plate.toLowerCase().includes(query)) ||
    (s.ticketId && s.ticketId.toLowerCase().includes(query)));

  box.classList.toggle("nomatch", !found);

  if (found) {
    state.filter = "ALL";
    updateFilterButtons();
    showView("dashboard");
    selectSlot(found.id);
  }
}

/* ---------- filters and theme ---------- */

function updateFilterButtons() {
  for (const chip of document.querySelectorAll("#filters .chip")) {
    chip.classList.toggle("active", chip.dataset.filter === state.filter);
  }
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  document.getElementById("theme-toggle").innerHTML = theme === "dark" ? ICON_SUN : ICON_MOON;
  try {
    localStorage.setItem("smartpark-theme", theme);
  } catch (error) {
    /* storage can be unavailable; the theme still works for this visit */
  }
}

function loadSavedTheme() {
  try {
    return localStorage.getItem("smartpark-theme") === "dark" ? "dark" : "light";
  } catch (error) {
    return "light";
  }
}

/* ---------- start ---------- */

for (const button of document.querySelectorAll(".nav-item")) {
  button.addEventListener("click", () => showView(button.dataset.view));
}

for (const chip of document.querySelectorAll("#filters .chip")) {
  chip.addEventListener("click", () => {
    state.filter = chip.dataset.filter;
    updateFilterButtons();
    renderBays();
  });
}

document.getElementById("search").addEventListener("input", event => runSearch(event.target.value));

document.getElementById("theme-toggle").addEventListener("click", () => {
  applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark");
});

window.addEventListener("hashchange", () => showView(location.hash.replace("#", "")));

applyTheme(loadSavedTheme());
showView(location.hash.replace("#", ""));
refresh();
setInterval(refresh, 5000);