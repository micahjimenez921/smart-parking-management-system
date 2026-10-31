/* Tickets page and Reports page. Uses helpers from app.js, flows.js and charts.js. */

const pageState = { range: 7, receipts: [] };

const METHOD_COLOR = { CASH: "var(--primary)", CARD: "var(--violet)", ONLINE: "var(--teal)" };
const METHOD_CHIP = { CASH: "type-regular", CARD: "type-vip", ONLINE: "type-handicapped" };

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

function renderTickets(tickets) {
  const body = document.getElementById("tickets-body");
  body.innerHTML = "";

  document.getElementById("tickets-count").textContent =
    tickets.length === 1 ? "1 vehicle parked right now" : tickets.length + " vehicles parked right now";

  if (tickets.length === 0) {
    emptyRow(body, 8, "No vehicles are parked right now.");
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

function renderPayments(receipts) {
  const body = document.getElementById("payments-body");
  body.innerHTML = "";

  if (receipts.length === 0) {
    emptyRow(body, 6, "No payments yet.");
    return;
  }

  for (const r of receipts) {
    const tr = make("tr");
    cell(tr, r.ticketId, "strong");
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
    const results = await Promise.all([getJson("/api/tickets"), getJson("/api/receipts?limit=10")]);
    renderTickets(results[0]);
    renderPayments(results[1]);
  } catch (error) {
    console.error(error);
    document.getElementById("offline").hidden = false;
  }
}

/* ---------- reports page ---------- */

function sumAmount(list) {
  return list.reduce((total, r) => total + r.amount, 0);
}

function renderReports() {
  const receipts = pageState.receipts;
  const todayKey = dayKey(new Date());
  const today = receipts.filter(r => r.time.slice(0, 10) === todayKey);

  const totalHours = receipts.reduce((total, r) => total + r.hours, 0);
  const average = receipts.length > 0 ? totalHours / receipts.length : 0;

  document.getElementById("rpt-today").textContent = money(sumAmount(today));
  document.getElementById("rpt-today-meta").textContent =
    today.length === 1 ? "1 payment today" : today.length + " payments today";
  document.getElementById("rpt-total").textContent = money(sumAmount(receipts));
  document.getElementById("rpt-total-meta").textContent = "All recorded payments";
  document.getElementById("rpt-count").textContent = String(receipts.length);
  document.getElementById("rpt-count-meta").textContent = "Completed checkouts";
  document.getElementById("rpt-avg").textContent = average.toFixed(1) + " hrs";
  document.getElementById("rpt-avg-meta").textContent = "Per parked vehicle";

  renderRevenueChart(receipts);
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
    emptyChart(document.getElementById("chart-revenue"), "No payments yet.");
    return;
  }
  lineChart(document.getElementById("chart-revenue"), items, {
    formatAxis: compactMoney,
    ariaLabel: "Revenue per day"
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
    renderRevenueChart(pageState.receipts);
  });
}

window.addEventListener("hashchange", loadCurrentPage);
setInterval(loadCurrentPage, 5000);
loadCurrentPage();