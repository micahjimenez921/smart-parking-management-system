/* Tickets page and Reports page. Uses helpers from app.js, flows.js and charts.js. */

const PAGE_SIZE = 10;
const pageState = {
  range: 7,
  receipts: [],
  tickets: [],
  allPayments: [],
  page: 1
};

const METHOD_COLOR = { CASH: "var(--primary)", CARD: "var(--violet)", GCASH: "#38a3f5", PAYMAYA: "#22c55e", ONLINE: "var(--teal)" };
const METHOD_CHIP = { CASH: "type-regular", CARD: "type-vip", GCASH: "pay-gcash", PAYMAYA: "pay-maya", ONLINE: "type-handicapped" };

/* ---------- helpers ---------- */

function currentView() {
  return location.hash.replace("#", "") || "dashboard";
}

function dayKey(date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return date.getFullYear() + "-" + month + "-" + day;
}

function compactMoney(value) {
  if (value >= 1000) {
    const thousands = value / 1000;
    return "\u20B1" + (Number.isInteger(thousands) ? thousands : thousands.toFixed(1)) + "k";
  }
  return "\u20B1" + Math.round(value);
}

function cell(rowNode, text, className) {
  const td = make("td", className || "", text);
  rowNode.appendChild(td);
  return td;
}

function emptyRow(body, columns, text) {
  const tr = make("tr");
  const td = make("td", "empty", text);
  td.colSpan = columns;
  tr.appendChild(td);
  body.appendChild(tr);
}

function methodChip(method) {
  return make("span", "type-chip " + (METHOD_CHIP[method] || ""), METHOD_LABEL[method] || method);
}

/* ---------- tickets page ---------- */

/* Text search that ignores case, spaces and dashes, like the lost-ticket plate search. */
function squash(text) {
  return String(text).toLowerCase().replace(/[\s-]/g, "");
}

function matchesSearch(fields, query) {
  const wanted = squash(query);
  if (wanted === "") return true;
  return fields.some(field => squash(field).includes(wanted));
}

function filteredTickets() {
  const query = document.getElementById("tf-search").value;
  const type = document.getElementById("tf-type").value;
  return pageState.tickets.filter(t =>
    (type === "" || t.vehicleType === type) &&
    matchesSearch([t.ticketId, t.plate, t.slotId, t.owner || ""], query));
}

function filteredPayments() {
  const query = document.getElementById("pf-search").value;
  const method = document.getElementById("pf-method").value;
  const from = document.getElementById("pf-from").value;
  const to = document.getElementById("pf-to").value;
  return pageState.allPayments.filter(r => {
    const day = r.time.slice(0, 10);
    return (method === "" || r.method === method) &&
      (from === "" || day >= from) &&
      (to === "" || day <= to) &&
      matchesSearch([r.reference || "", r.ticketId, r.plate, r.slotId], query);
  });
}

function renderTickets() {
  const body = document.getElementById("tickets-body");
  body.innerHTML = "";

  const all = pageState.tickets;
  const tickets = filteredTickets();

  const parked = all.length === 1 ? "1 vehicle parked right now" : all.length + " vehicles parked right now";
  document.getElementById("tickets-count").textContent =
    tickets.length === all.length ? parked : "Showing " + tickets.length + " of " + all.length + " parked vehicles";

  if (tickets.length === 0) {
    emptyRow(body, 8, all.length === 0 ? "No vehicles are parked right now." : "No tickets match your search.");
    return;
  }

  for (const t of tickets) {
    const tr = make("tr");
    cell(tr, t.ticketId, "strong");
    cell(tr, t.plate);
    cell(tr, t.vehicleType.charAt(0) + t.vehicleType.slice(1).toLowerCase());
    cell(tr, t.slotId);
    cell(tr, formatEntry(t.entryTime));
    cell(tr, String(t.hours));
    cell(tr, money(t.fee), "strong");

    const action = make("td");
    const button = make("button", "btn-small", "Check out");
    button.addEventListener("click", () => openCheckout(t.ticketId));
    action.appendChild(button);
    tr.appendChild(action);

    body.appendChild(tr);
  }
}

function anyPaymentFilter() {
  return ["pf-search", "pf-method", "pf-from", "pf-to"].some(id => document.getElementById(id).value !== "");
}

/* Page numbers to show: the first, the last, and the pages around the current one. */
function pageNumbers(current, last) {
  const wanted = new Set([1, last, current - 1, current, current + 1]);
  const numbers = [...wanted].filter(n => n >= 1 && n <= last).sort((x, y) => x - y);
  const result = [];
  for (let i = 0; i < numbers.length; i++) {
    if (i > 0 && numbers[i] - numbers[i - 1] > 1) result.push("...");
    result.push(numbers[i]);
  }
  return result;
}

/* Previous / page numbers / Next, drawn into the given element. Used by the payments table and the driver history. */
function buildPager(pager, current, totalPages, go) {
  pager.innerHTML = "";
  pager.hidden = totalPages <= 1;
  if (totalPages <= 1) return;

  const prev = make("button", "pager-btn", "Previous");
  prev.type = "button";
  prev.disabled = current === 1;
  prev.addEventListener("click", () => go(current - 1));
  pager.appendChild(prev);

  for (const n of pageNumbers(current, totalPages)) {
    if (n === "...") {
      pager.appendChild(make("span", "pager-gap", "\u2026"));
      continue;
    }
    const button = make("button", "pager-btn" + (n === current ? " current" : ""), String(n));
    button.type = "button";
    if (n === current) button.setAttribute("aria-current", "page");
    button.setAttribute("aria-label", "Page " + n);
    button.addEventListener("click", () => go(n));
    pager.appendChild(button);
  }

  const next = make("button", "pager-btn", "Next");
  next.type = "button";
  next.disabled = current === totalPages;
  next.addEventListener("click", () => go(current + 1));
  pager.appendChild(next);
}

function renderPayments() {
  const body = document.getElementById("payments-body");
  body.innerHTML = "";

  const all = pageState.allPayments;
  const matches = filteredPayments();
  const totalPages = Math.max(1, Math.ceil(matches.length / PAGE_SIZE));
  pageState.page = Math.min(Math.max(1, pageState.page), totalPages);

  const start = (pageState.page - 1) * PAGE_SIZE;
  const visible = matches.slice(start, start + PAGE_SIZE);

  document.getElementById("pf-clear").disabled = !anyPaymentFilter();

  const filtered = matches.length === all.length ? "" : " (filtered from " + all.length + ")";
  document.getElementById("payments-count").textContent = matches.length === 0
    ? (all.length === 0 ? "No payments yet" : "No payments match your filters")
    : "Showing " + (start + 1) + "\u2013" + (start + visible.length) + " of " + matches.length + " payments" + filtered;

  buildPager(document.getElementById("payments-pager"), pageState.page, totalPages, (page) => {
    pageState.page = page;
    renderPayments();
  });

  if (visible.length === 0) {
    emptyRow(body, 7, all.length === 0 ? "No payments yet." : "No payments match your filters.");
    return;
  }

  for (const r of visible) {
    const tr = make("tr");
    cell(tr, r.reference || "-", "strong");
    cell(tr, r.ticketId);
    cell(tr, r.plate);
    cell(tr, r.slotId);

    const method = make("td");
    method.appendChild(methodChip(r.method));
    tr.appendChild(method);

    cell(tr, money(r.amount), "strong");
    cell(tr, formatEntry(r.time));
    body.appendChild(tr);
  }
}

async function loadTicketsPage() {
  try {
    const results = await Promise.all([getJson("/api/tickets"), getJson("/api/receipts?limit=1000")]);
    pageState.tickets = results[0];
    pageState.allPayments = results[1];
    renderTickets();
    renderPayments();
  } catch (error) {
    console.error(error);
    document.getElementById("offline").hidden = false;
  }
}

/* Filters: typing or choosing redraws the table from the data already loaded. */
for (const id of ["tf-search", "tf-type"]) {
  const input = document.getElementById(id);
  input.addEventListener("input", renderTickets);
  input.addEventListener("change", renderTickets);
}

for (const id of ["pf-search", "pf-method", "pf-from", "pf-to"]) {
  const input = document.getElementById(id);
  const redraw = () => {
    pageState.page = 1;
    renderPayments();
  };
  input.addEventListener("input", redraw);
  input.addEventListener("change", redraw);
}

document.getElementById("pf-clear").addEventListener("click", () => {
  for (const id of ["pf-search", "pf-method", "pf-from", "pf-to"]) document.getElementById(id).value = "";
  pageState.page = 1;
  renderPayments();
});

/* ---------- reports page ---------- */

function sumAmount(list) {
  return list.reduce((total, r) => total + r.amount, 0);
}

const SLOT_TYPE_COLOR = { REGULAR: "var(--primary)", HANDICAPPED: "var(--teal)", VIP: "var(--violet)" };

/** The receipts inside the chosen period (the last N days, today included). */
function periodReceipts() {
  const first = new Date();
  first.setDate(first.getDate() - (pageState.range - 1));
  const firstKey = dayKey(first);
  return pageState.receipts.filter(r => r.time.slice(0, 10) >= firstKey);
}

function slotTypeOf(slotId) {
  const known = (state.slots || []).find(s => s.id === slotId);
  if (known) return known.type;
  return { R: "REGULAR", H: "HANDICAPPED", V: "VIP" }[String(slotId).charAt(0)] || "REGULAR";
}

function periodText() {
  return "last " + pageState.range + " days";
}

function renderReports() {
  const all = pageState.receipts;
  const receipts = periodReceipts();
  const todayKey = dayKey(new Date());
  const today = all.filter(r => r.time.slice(0, 10) === todayKey);

  const totalHours = receipts.reduce((total, r) => total + r.hours, 0);
  const average = receipts.length > 0 ? totalHours / receipts.length : 0;

  document.getElementById("rpt-today").textContent = money(sumAmount(today));
  document.getElementById("rpt-today-meta").textContent =
    today.length === 1 ? "1 payment today" : today.length + " payments today";
  document.getElementById("rpt-total").textContent = money(sumAmount(receipts));
  document.getElementById("rpt-total-label").textContent = "Revenue";
  document.getElementById("rpt-total-meta").textContent = "In the " + periodText();
  document.getElementById("rpt-count").textContent = String(receipts.length);
  document.getElementById("rpt-count-meta").textContent = receipts.length > 0
    ? "Average " + money(sumAmount(receipts) / receipts.length) + " each"
    : "In the " + periodText();
  document.getElementById("rpt-avg").textContent = average.toFixed(1) + " hrs";
  document.getElementById("rpt-avg-meta").textContent = "Per parked vehicle, " + periodText();

  renderRevenueChart(receipts);
  renderHoursChart(receipts);
  renderSlotTypeChart(receipts);
  renderDurationChart(receipts);
  renderMethodChart(receipts);
}

function renderRevenueChart(receipts) {
  const items = [];
  for (let back = pageState.range - 1; back >= 0; back--) {
    const date = new Date();
    date.setDate(date.getDate() - back);
    const key = dayKey(date);
    const forDay = receipts.filter(r => r.time.slice(0, 10) === key);
    const total = sumAmount(forDay);

    const label = pageState.range <= 7
      ? date.toLocaleDateString("en-PH", { weekday: "short" })
      : date.toLocaleDateString("en-PH", { month: "short", day: "numeric" });
    const full = date.toLocaleDateString("en-PH", { month: "short", day: "numeric" });

    items.push({
      label: label,
      value: total,
      tip: full + ": " + money(total) + " (" + forDay.length + (forDay.length === 1 ? " payment)" : " payments)")
    });
  }

  if (receipts.length === 0) {
    emptyChart(document.getElementById("chart-revenue"), "No payments in the " + periodText() + ".");
    return;
  }
  lineChart(document.getElementById("chart-revenue"), items, {
    formatAxis: compactMoney,
    ariaLabel: "Revenue per day"
  });
}

function hourLabel(hour) {
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return h + (hour < 12 ? "a" : "p");
}

/** Payments by hour of the day, to show when the lot is busiest. */
function hoursBreakdown(receipts) {
  const counts = new Array(24).fill(0);
  for (const r of receipts) counts[Number(r.time.slice(11, 13))]++;
  return counts;
}

function renderHoursChart(receipts) {
  const counts = hoursBreakdown(receipts);
  const items = counts.map((count, hour) => ({
    label: hourLabel(hour),
    value: count,
    tip: hourLabel(hour) + " (" + String(hour).padStart(2, "0") + ":00): " + count + (count === 1 ? " payment" : " payments")
  }));

  const biggest = Math.max(...counts);
  document.getElementById("hours-note").textContent = biggest > 0
    ? "Busiest at " + busiestHourText(counts) + ". Payments taken at each hour, " + periodText() + "."
    : "Payments taken at each hour of the day";
  barChart(document.getElementById("chart-hours"), items, { ariaLabel: "Payments by hour of the day" });
}

function busiestHourText(counts) {
  const biggest = Math.max(...counts);
  const hour = counts.indexOf(biggest);
  const next = (hour + 1) % 24;
  return hourLabel(hour).replace("a", " AM").replace("p", " PM") + " to " + hourLabel(next).replace("a", " AM").replace("p", " PM");
}

function renderSlotTypeChart(receipts) {
  const items = Object.keys(TYPE_LABEL).map(type => {
    const forType = receipts.filter(r => slotTypeOf(r.slotId) === type);
    return {
      label: TYPE_LABEL[type],
      value: sumAmount(forType),
      color: SLOT_TYPE_COLOR[type],
      detail: forType.length + (forType.length === 1 ? " payment" : " payments")
    };
  });
  donutChart(document.getElementById("chart-slottypes"), items, {
    centerLabel: "revenue",
    formatTotal: compactMoney,
    ariaLabel: "Revenue by slot type"
  });
}

function renderDurationChart(receipts) {
  const labels = ["1 hr", "2 hrs", "3 hrs", "4 hrs", "5+ hrs"];
  const counts = [0, 0, 0, 0, 0];
  for (const r of receipts) {
    counts[Math.min(Math.max(r.hours, 1), 5) - 1]++;
  }

  const items = labels.map((label, index) => ({
    label: label,
    value: counts[index],
    tip: counts[index] + (counts[index] === 1 ? " vehicle" : " vehicles") + " parked " + label
  }));

  barChart(document.getElementById("chart-duration"), items, { ariaLabel: "Vehicles by hours parked" });
}

function renderMethodChart(receipts) {
  const items = Object.keys(METHOD_LABEL).map(method => {
    const forMethod = receipts.filter(r => r.method === method);
    return {
      label: METHOD_LABEL[method],
      value: forMethod.length,
      color: METHOD_COLOR[method],
      detail: money(sumAmount(forMethod))
    };
  });

  donutChart(document.getElementById("chart-methods"), items, {
    centerLabel: "payments",
    ariaLabel: "Payments by method"
  });
}

/* ---------- download and print ---------- */

function csvCell(value) {
  const text = String(value);
  return /[",\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}

function reportCsv(receipts) {
  const lines = [["Reference", "Ticket", "Plate", "Slot", "Slot type", "Hours", "Method", "Amount", "Date", "Time"].join(",")];
  for (const r of receipts) {
    lines.push([
      r.reference, r.ticketId, r.plate, r.slotId, TYPE_LABEL[slotTypeOf(r.slotId)],
      r.hours, METHOD_LABEL[r.method] || r.method, Number(r.amount).toFixed(2),
      r.time.slice(0, 10), r.time.slice(11, 16)
    ].map(csvCell).join(","));
  }
  return lines.join("\r\n");
}

function downloadReport() {
  const receipts = periodReceipts();
  const blob = new Blob(["\uFEFF" + reportCsv(receipts)], { type: "text/csv;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = "smartpark-payments-" + dayKey(new Date()) + ".csv";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(link.href);
}

function printReport() {
  const receipts = periodReceipts();
  const counts = hoursBreakdown(receipts);
  const rows = [
    { label: "Period", value: "Last " + pageState.range + " days" },
    { label: "Payments", value: String(receipts.length) },
    { label: "Revenue", value: money(sumAmount(receipts)), strong: true },
    { label: "Average stay", value: (receipts.length > 0 ? receipts.reduce((t, r) => t + r.hours, 0) / receipts.length : 0).toFixed(1) + " hrs" }
  ];
  if (Math.max(...counts) > 0) rows.push({ label: "Busiest hour", value: busiestHourText(counts) });
  for (const method of Object.keys(METHOD_LABEL)) {
    const forMethod = receipts.filter(r => r.method === method);
    if (forMethod.length > 0) rows.push({ label: METHOD_LABEL[method], value: money(sumAmount(forMethod)) + " (" + forMethod.length + ")" });
  }
  printSlip("Revenue report", rows, "Totals come from the recorded payments.");
}

async function loadReportsPage() {
  try {
    pageState.receipts = await getJson("/api/receipts?limit=1000");
    renderReports();
  } catch (error) {
    console.error(error);
    document.getElementById("offline").hidden = false;
  }
}

/* ---------- start ---------- */

function loadCurrentPage() {
  if (!canUse("TICKETS")) return;
  const view = currentView();
  if (view === "tickets") loadTicketsPage();
  if (view === "reports") loadReportsPage();
}

for (const chip of document.querySelectorAll("#range-chips .chip")) {
  chip.addEventListener("click", () => {
    pageState.range = Number(chip.dataset.range);
    for (const other of document.querySelectorAll("#range-chips .chip")) {
      other.classList.toggle("active", other === chip);
    }
    renderReports();
  });
}

document.getElementById("rpt-csv").addEventListener("click", downloadReport);
document.getElementById("rpt-print").addEventListener("click", printReport);

window.addEventListener("hashchange", loadCurrentPage);
setInterval(loadCurrentPage, 5000);
loadCurrentPage();