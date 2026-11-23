/* Driver page: my parked vehicles, paying, my plate numbers, and my payment history.
   Uses helpers from app.js (make, row, money, formatEntry, getJson, currentView) and
   from flows.js (postForm, setMessage, markChoice, plainRow, METHOD_LABEL, ICON_CHECK, and buildCheckout from checkout.js). */

const driverState = { data: null, paying: null, receipt: null, query: "", history: { page: 1, method: "", open: null } };
const HISTORY_PAGE_SIZE = 5;

/* drivers pay by card, GCash, or PayMaya; cash is taken by the attendant */
const DRIVER_METHODS = ["CARD", "GCASH", "PAYMAYA"];

async function loadDriverPage(force) {
  if (!canUse("SELF_SERVICE")) return;

  try {
    const data = await getJson("/api/my/ticket");
    driverState.data = data;
    if (typeof loadReservations === "function") loadReservations(force);

    /* the automatic refresh must never wipe a payment form or a plate number someone is typing */
    if (!force && (driverState.paying || plateInputBusy())) return;
    renderDriverPage();
  } catch (error) {
    if (!driverState.data) {
      const card = document.getElementById("driver-parked");
      card.innerHTML = "";
      card.appendChild(make("h2", "", "Parked now"));
      card.appendChild(make("p", "empty", "Could not load your vehicles. Is the SmartPark server running?"));
    }
  }
}

function plateInputBusy() {
  const input = document.getElementById("plate-input");
  return !!input && input.value !== "";
}

function renderDriverPage() {
  if (!driverState.data) return;
  renderDriverParked();
  renderDriverPlates();
  renderDriverPayments();
  applyDriverSearch(false);
}

/* ---------- the search box (a driver searches only their own things) ---------- */

function runDriverSearch(text) {
  driverState.query = text.trim().toLowerCase();
  if (driverState.query !== "" && currentView() !== "driver" && canSeeView("driver")) showView("driver");
  applyDriverSearch(true);
}

function applyDriverSearch(scroll) {
  const query = driverState.query || "";
  const loose = query.replace(/[\s-]/g, "");
  const items = document.querySelectorAll(
    "#driver-parked .my-ticket, #driver-reserve .my-reservation, #driver-plates .plate-chip, #driver-payments tbody tr.pay-row");

  let first = null;
  for (const item of items) {
    const text = item.textContent.toLowerCase();
    const hit = query !== "" && (text.includes(query) || text.replace(/[\s-]/g, "").includes(loose));
    item.classList.toggle("search-hit", hit);
    if (hit && !first) first = item;
  }

  document.getElementById("search-box").classList.toggle("nomatch", query !== "" && !first);
  if (first && scroll) first.scrollIntoView({ behavior: "smooth", block: "center" });
}

/* ---------- parked now ---------- */

function renderDriverParked() {
  const card = document.getElementById("driver-parked");
  card.innerHTML = "";

  if (driverState.receipt) {
    renderDriverReceipt(card, driverState.receipt);
    return;
  }

  card.appendChild(make("h2", "", "Parked now"));
  const data = driverState.data;

  if (data.tickets.length === 0) {
    const empty = make("div", "empty-state");
    empty.appendChild(make("p", "", data.plates.length === 0
      ? "Add your plate number on the right to see your parked vehicles here."
      : "None of your vehicles is parked right now."));
    card.appendChild(empty);
    return;
  }

  card.appendChild(make("p", "muted", "Fee rule: " + data.feeRule));
  for (const ticket of data.tickets) {
    card.appendChild(driverTicketBlock(ticket));
  }
}

function driverTicketBlock(ticket) {
  const box = make("div", "my-ticket");

  const head = make("div", "my-ticket-head");
  head.appendChild(make("b", "my-plate", ticket.plate));
  head.appendChild(make("span", "pill busy", ticket.slotId));
  box.appendChild(head);

  const rows = make("div", "rows");
  plainRow(rows, "Ticket", ticket.ticketId);
  plainRow(rows, "Vehicle", ticket.vehicleType);
  plainRow(rows, "Slot", ticket.slotId + " (" + ticket.slotType + ", " + money(ticket.rate) + " per hour)");
  plainRow(rows, "Parked since", formatEntry(ticket.entryTime));
  plainRow(rows, "Hours charged so far", String(ticket.hours));
  box.appendChild(rows);

  const fee = make("div", "my-fee");
  fee.appendChild(make("span", "muted", "Fee so far"));
  fee.appendChild(make("b", "", money(ticket.fee)));
  box.appendChild(fee);

  if (driverState.paying === ticket.ticketId) {
    box.appendChild(driverPayForm(ticket));
  } else {
    const pay = make("button", "btn", "Pay now");
    pay.setAttribute("type", "button");
    pay.addEventListener("click", () => {
      driverState.paying = ticket.ticketId;
      renderDriverParked();
    });
    box.appendChild(pay);
  }
  return box;
}

function driverPayForm(ticket) {
  return buildCheckout({
    methods: DRIVER_METHODS,
    summaryTitle: "Your parking fee",
    rows: [
      ["Vehicle", ticket.plate],
      ["Slot", ticket.slotId],
      ["Hours charged", String(ticket.hours)]
    ],
    total: ticket.fee,
    totalLabel: "Total due",
    cancel: () => {
      driverState.paying = null;
      renderDriverParked();
    },
    onPay: async (method, detail) => {
      driverState.receipt = await postForm("/api/my/pay", {
        ticketId: ticket.ticketId,
        method: method,
        detail: detail
      });
      driverState.paying = null;
      renderDriverParked();
      loadDriverPage(true);
    }
  });
}

function renderDriverReceipt(card, receipt) {
  const head = make("div", "ticket-head");
  const mark = make("div", "success-mark");
  mark.innerHTML = ICON_CHECK;
  head.appendChild(mark);
  head.appendChild(make("h2", "", "Payment successful"));
  head.appendChild(make("p", "muted", "Ticket " + receipt.ticketId + " is closed."));
  card.appendChild(head);

  const rows = make("div", "rows");
  for (const line of receiptLines(receipt)) plainRow(rows, line.label, line.value);
  card.appendChild(rows);

  card.appendChild(make("div", "gate-notice open", "The exit gate has opened for your vehicle. Please drive out now."));

  const printButton = make("button", "btn-ghost", "Print receipt");
  printButton.setAttribute("type", "button");
  printButton.addEventListener("click", () => printReceipt(receipt));
  card.appendChild(printButton);

  const done = make("button", "btn", "Done");
  done.setAttribute("type", "button");
  done.addEventListener("click", () => {
    driverState.receipt = null;
    renderDriverParked();
  });
  card.appendChild(done);
}

/* ---------- my plate numbers ---------- */

function renderDriverPlates() {
  const card = document.getElementById("driver-plates");
  const typed = plateInputBusy() ? document.getElementById("plate-input").value : "";
  card.innerHTML = "";

  card.appendChild(make("h2", "", "My plate numbers"));
  card.appendChild(make("p", "muted", "Vehicles with these plates appear on this page."));

  const list = make("div", "plate-list");
  const plates = driverState.data.plates;
  if (plates.length === 0) list.appendChild(make("span", "muted", "No plate numbers yet."));
  for (const plate of plates) {
    const chip = make("span", "plate-chip");
    chip.appendChild(make("span", "", plate));
    const remove = make("button", "", "×");
    remove.setAttribute("type", "button");
    remove.setAttribute("title", "Remove " + plate);
    remove.setAttribute("aria-label", "Remove " + plate);
    remove.addEventListener("click", () => removeDriverPlate(plate));
    chip.appendChild(remove);
    list.appendChild(chip);
  }
  card.appendChild(list);

  const form = make("form", "form");
  form.setAttribute("autocomplete", "off");
  form.setAttribute("novalidate", "");

  const field = make("label", "field");
  field.appendChild(make("span", "", "Add a plate number"));
  const inline = make("div", "inline");
  const input = make("input");
  input.id = "plate-input";
  input.setAttribute("type", "text");
  input.setAttribute("placeholder", "e.g. ABC 123");
  input.setAttribute("maxlength", "12");
  input.value = typed;
  const add = make("button", "btn-inline", "Add");
  add.setAttribute("type", "submit");
  inline.appendChild(input);
  inline.appendChild(add);
  field.appendChild(inline);
  form.appendChild(field);

  const message = make("div", "form-msg");
  message.id = "plate-msg";
  message.hidden = true;
  form.appendChild(message);

  form.addEventListener("submit", event => {
    event.preventDefault();
    addDriverPlate(input.value, message);
  });
  card.appendChild(form);
}

async function addDriverPlate(plate, message) {
  if (plate.trim() === "") {
    setMessage(message, "Enter a plate number.", "error");
    return;
  }
  try {
    const result = await postForm("/api/my/plates/add", { plate: plate });
    platesChanged(result.plates);
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

async function removeDriverPlate(plate) {
  try {
    const result = await postForm("/api/my/plates/remove", { plate: plate });
    platesChanged(result.plates);
  } catch (error) {
    setMessage(document.getElementById("plate-msg"), error.message, "error");
  }
}

/* keeps the My account page in step, then reloads the vehicles for the new list of plates */
function platesChanged(plates) {
  session.user.detail = plates.join(";");
  renderAccount();
  driverState.data.plates = plates;
  document.getElementById("plate-input").value = "";
  loadDriverPage(true);
}

/* ---------- payment history ---------- */

function renderDriverPayments() {
  const card = document.getElementById("driver-payments");
  card.innerHTML = "";
  const history = driverState.history;

  const all = driverState.data.payments;
  const head = make("div", "card-head");
  const title = make("div");
  title.appendChild(make("h2", "", "Payment history"));
  const total = all.reduce((sum, p) => sum + p.amount, 0);
  title.appendChild(make("p", "muted", all.length === 0 ? "Your payments and receipts appear here."
    : all.length + (all.length === 1 ? " payment" : " payments") + " \u00B7 " + money(total) + " paid in total"));
  head.appendChild(title);

  if (all.length > 0) {
    const select = make("select", "select history-filter");
    select.setAttribute("aria-label", "Filter by payment method");
    const methods = [...new Set(all.map(p => p.method))];
    select.appendChild(new Option("All methods", ""));
    for (const m of methods) select.appendChild(new Option(METHOD_LABEL[m] || m, m));
    select.value = methods.includes(history.method) ? history.method : "";
    history.method = select.value;
    select.addEventListener("change", () => {
      history.method = select.value;
      history.page = 1;
      history.open = null;
      renderDriverPayments();
      applyDriverSearch(false);
    });
    head.appendChild(select);
  }
  card.appendChild(head);

  if (all.length === 0) return;

  const payments = all.filter(p => history.method === "" || p.method === history.method);
  const totalPages = Math.max(1, Math.ceil(payments.length / HISTORY_PAGE_SIZE));
  history.page = Math.min(Math.max(1, history.page), totalPages);
  const shown = payments.slice((history.page - 1) * HISTORY_PAGE_SIZE, history.page * HISTORY_PAGE_SIZE);

  const wrap = make("div", "table-wrap");
  const table = make("table", "table");
  const headRow = make("tr");
  for (const label of ["Payment", "Vehicle", "Method", "Amount", ""]) headRow.appendChild(make("th", "", label));
  table.appendChild(make("thead")).appendChild(headRow);

  const body = make("tbody");
  for (const payment of shown) {
    const isOpen = history.open === payment.ticketId;
    const line = make("tr", "pay-row" + (isOpen ? " open" : ""));
    const paymentCell = make("td");
    paymentCell.appendChild(make("b", "pay-ref", payment.reference || "-"));
    paymentCell.appendChild(make("small", "pay-when", formatEntry(payment.time)));
    line.appendChild(paymentCell);
    line.appendChild(make("td", "", payment.plate));
    const methodCell = make("td");
    methodCell.appendChild(methodChip(payment.method));
    line.appendChild(methodCell);
    line.appendChild(make("td", "strong", money(payment.amount)));

    const action = make("td");
    const button = make("button", "btn-small", isOpen ? "Hide" : "Receipt");
    button.type = "button";
    button.setAttribute("aria-expanded", String(isOpen));
    /* the whole row opens the receipt, so it is easy to tap on a phone; the button bubbles up to it */
    line.addEventListener("click", () => {
      history.open = isOpen ? null : payment.ticketId;
      renderDriverPayments();
      applyDriverSearch(false);
    });
    action.appendChild(button);
    line.appendChild(action);
    body.appendChild(line);
  }
  table.appendChild(body);
  wrap.appendChild(table);
  card.appendChild(wrap);

  const openPayment = shown.find(p => p.ticketId === history.open);
  if (openPayment) card.appendChild(driverReceiptBox(openPayment));

  const foot = make("div", "table-foot");
  const pager = make("nav", "pager");
  pager.setAttribute("aria-label", "Payment history pages");
  foot.appendChild(pager);
  card.appendChild(foot);
  buildPager(pager, history.page, totalPages, (page) => {
    history.page = page;
    history.open = null;
    renderDriverPayments();
    applyDriverSearch(false);
  });
}

/* A past receipt, opened under the table, with a Print button. */
function driverReceiptBox(payment) {
  const box = make("div", "receipt-box");
  box.appendChild(make("h3", "", "Receipt"));
  const rows = make("div", "rows");
  for (const line of receiptLines(payment)) plainRow(rows, line.label, line.value);
  box.appendChild(rows);

  const print = make("button", "btn-ghost", "Print receipt");
  print.type = "button";
  print.addEventListener("click", () => printReceipt(payment));
  box.appendChild(print);
  return box;
}

/* ---------- keeping the page up to date ---------- */

window.addEventListener("hashchange", () => {
  if (currentView() === "driver") loadDriverPage(true);
});

setInterval(() => {
  if (currentView() === "driver") loadDriverPage(false);
}, 5000);
