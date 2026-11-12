/* Driver page: my parked vehicles, paying, my plate numbers, and my recent payments.
   Uses helpers from app.js (make, row, money, formatEntry, getJson, currentView) and
   from flows.js (postForm, setMessage, markChoice, plainRow, PAY_DETAIL, METHOD_LABEL, ICON_CHECK). */

const driverState = { data: null, paying: null, method: "CARD", receipt: null };

/* drivers pay by card or online; cash is taken by the attendant */
const DRIVER_METHODS = ["CARD", "ONLINE"];

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
      driverState.method = "CARD";
      renderDriverParked();
      const detail = document.getElementById("driver-pay-detail");
      if (detail) detail.focus();
    });
    box.appendChild(pay);
  }
  return box;
}

function driverPayForm(ticket) {
  const form = make("form", "form my-pay");
  form.setAttribute("autocomplete", "off");

  const methodField = make("div", "field");
  methodField.appendChild(make("span", "", "Payment method"));
  const choices = make("div", "choices");
  for (const key of DRIVER_METHODS) {
    const button = make("button", "choice");
    button.setAttribute("type", "button");
    button.dataset.value = key;
    button.appendChild(make("b", "", METHOD_LABEL[key]));
    choices.appendChild(button);
  }
  methodField.appendChild(choices);
  form.appendChild(methodField);

  const input = make("input");
  input.id = "driver-pay-detail";
  input.setAttribute("type", "text");
  input.setAttribute("inputmode", "numeric");
  const detailLabel = make("label", "field");
  const detailText = make("span", "", "");
  detailLabel.appendChild(detailText);
  detailLabel.appendChild(input);
  form.appendChild(detailLabel);

  const message = make("div", "form-msg");
  message.id = "driver-pay-msg";
  message.hidden = true;
  form.appendChild(message);

  const submit = make("button", "btn", "Pay " + money(ticket.fee));
  submit.setAttribute("type", "submit");
  form.appendChild(submit);

  const cancel = make("button", "btn-ghost", "Cancel");
  cancel.setAttribute("type", "button");
  cancel.addEventListener("click", () => {
    driverState.paying = null;
    renderDriverParked();
  });
  form.appendChild(cancel);

  function updateDetail() {
    const info = PAY_DETAIL[driverState.method];
    detailText.textContent = info.label;
    input.setAttribute("placeholder", info.hint);
    input.value = "";
  }

  for (const button of choices.querySelectorAll(".choice")) {
    button.addEventListener("click", () => {
      driverState.method = button.dataset.value;
      markChoice(choices, driverState.method);
      updateDetail();
      setMessage(message, "", "");
    });
  }
  markChoice(choices, driverState.method);
  updateDetail();

  form.addEventListener("submit", event => {
    event.preventDefault();
    submitDriverPayment(ticket, input.value, message, submit);
  });
  return form;
}

async function submitDriverPayment(ticket, detail, message, submit) {
  setMessage(message, "", "");
  submit.disabled = true;

  try {
    driverState.receipt = await postForm("/api/my/pay", {
      ticketId: ticket.ticketId,
      method: driverState.method,
      detail: detail
    });
    driverState.paying = null;
    renderDriverParked();
    loadDriverPage(true);
  } catch (error) {
    setMessage(message, error.message, "error");
    submit.disabled = false;
  }
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
  plainRow(rows, "Vehicle", receipt.plate);
  plainRow(rows, "Slot", receipt.slotId);
  plainRow(rows, "Hours charged", String(receipt.hours));
  plainRow(rows, "Method", METHOD_LABEL[receipt.method] || receipt.method);
  plainRow(rows, "Amount paid", money(receipt.amount));
  plainRow(rows, "Paid at", formatEntry(receipt.time));
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

/* ---------- recent payments ---------- */

function renderDriverPayments() {
  const card = document.getElementById("driver-payments");
  card.innerHTML = "";
  card.appendChild(make("h2", "", "Recent payments"));

  const payments = driverState.data.payments;
  if (payments.length === 0) {
    card.appendChild(make("p", "muted", "No payments yet."));
    return;
  }

  const wrap = make("div", "table-wrap");
  const table = make("table", "table");
  const headRow = make("tr");
  for (const title of ["Paid at", "Vehicle", "Method", "Amount"]) headRow.appendChild(make("th", "", title));
  table.appendChild(make("thead")).appendChild(headRow);

  const body = make("tbody");
  for (const payment of payments) {
    const line = make("tr");
    line.appendChild(make("td", "", formatEntry(payment.time)));
    line.appendChild(make("td", "", payment.plate));
    line.appendChild(make("td", "", METHOD_LABEL[payment.method] || payment.method));
    line.appendChild(make("td", "strong", money(payment.amount)));
    body.appendChild(line);
  }
  table.appendChild(body);
  wrap.appendChild(table);
  card.appendChild(wrap);
}

/* ---------- keeping the page up to date ---------- */

window.addEventListener("hashchange", () => {
  if (currentView() === "driver") loadDriverPage(true);
});

setInterval(() => {
  if (currentView() === "driver") loadDriverPage(false);
}, 5000);
