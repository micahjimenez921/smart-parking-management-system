/* Vehicle entry and exit pages. Uses helpers from app.js. */

const entryState = { vehicleType: "CAR", slotType: "REGULAR", ticket: null };
const exitState = { ticket: null, method: "CASH", receipt: null };

const SLOT_TYPES = ["REGULAR", "HANDICAPPED", "VIP"];
const METHOD_LABEL = { CASH: "Cash", CARD: "Card", ONLINE: "Online" };

const ICON_CHECK =
  '<svg class="ico" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>';

/* ---------- helpers ---------- */

async function sendRequest(url, options) {
  let response;
  try {
    response = await fetch(url, options);
  } catch (error) {
    throw new Error("Cannot reach the SmartPark server.");
  }

  let body = {};
  try {
    body = await response.json();
  } catch (error) {
    /* the server sent no JSON */
  }

  if (!response.ok) {
    throw new Error(body.error || "Something went wrong (" + response.status + ").");
  }
  return body;
}

function postForm(url, data) {
  return sendRequest(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
    body: new URLSearchParams(data).toString()
  });
}

function setMessage(node, text, kind) {
  node.hidden = !text;
  node.className = "form-msg " + (kind || "");
  node.textContent = text || "";
}

function markChoice(box, value) {
  for (const button of box.querySelectorAll(".choice")) {
    button.classList.toggle("selected", button.dataset.value === value);
  }
}

function setupChoices(boxId, store, key, onChange) {
  const box = document.getElementById(boxId);
  for (const button of box.querySelectorAll(".choice")) {
    button.addEventListener("click", () => {
      if (button.disabled) return;
      store[key] = button.dataset.value;
      markChoice(box, store[key]);
      if (onChange) onChange();
    });
  }
  markChoice(box, store[key]);
}

function plainRow(parent, key, value) {
  parent.appendChild(row(key, value));
}

/* ---------- vehicle entry ---------- */

function renderEntryAvailability() {
  for (const type of SLOT_TYPES) {
    const ofType = state.slots.filter(s => s.type === type);
    const free = ofType.filter(s => s.available).length;
    const info = document.getElementById("info-" + type);
    const button = document.getElementById("choice-slot-" + type);

    if (ofType.length === 0) {
      info.textContent = "No slots";
    } else {
      info.textContent = money(ofType[0].rate) + "/hr \u00B7 " + (free > 0 ? free + " free" : "Full");
    }
    button.disabled = free === 0;
  }
}

function renderEntryResult() {
  const card = document.getElementById("entry-result");
  card.innerHTML = "";
  const t = entryState.ticket;

  if (!t) {
    const empty = make("div", "empty-state");
    empty.appendChild(make("h2", "", "Ticket"));
    empty.appendChild(make("p", "", "The assigned slot and ticket appear here after you park a vehicle."));
    card.appendChild(empty);
    return;
  }

  const head = make("div", "ticket-head");
  head.appendChild(make("div", "label", "Parking ticket"));
  head.appendChild(make("div", "ticket-id", t.ticketId));
  head.appendChild(make("div", "ticket-slot", "Slot " + t.slotId));
  card.appendChild(head);

  const rows = make("div", "rows");
  plainRow(rows, "Plate number", t.plate);
  plainRow(rows, "Vehicle", t.vehicleType.toLowerCase());
  plainRow(rows, "Owner", t.owner);
  plainRow(rows, "Slot type", TYPE_LABEL[t.slotType] || t.slotType);
  plainRow(rows, "Rate", money(t.rate) + " / hour");
  plainRow(rows, "Entered", formatEntry(t.entryTime));
  card.appendChild(rows);

  const another = make("button", "btn", "Park another vehicle");
  another.addEventListener("click", () => {
    entryState.ticket = null;
    renderEntryResult();
    document.getElementById("entry-plate").focus();
  });
  card.appendChild(another);

  const back = make("button", "btn-ghost", "View on dashboard");
  back.addEventListener("click", () => {
    state.selectedId = t.slotId;
    state.filter = "ALL";
    updateFilterButtons();
    showView("dashboard");
    renderBays();
    renderDetail();
  });
  card.appendChild(back);
}

async function submitEntry(event) {
  event.preventDefault();
  const message = document.getElementById("entry-msg");
  setMessage(message, "", "");

  try {
    entryState.ticket = await postForm("/api/park", {
      plate: document.getElementById("entry-plate").value,
      owner: document.getElementById("entry-owner").value,
      vehicleType: entryState.vehicleType,
      slotType: entryState.slotType
    });
    document.getElementById("entry-plate").value = "";
    document.getElementById("entry-owner").value = "";
    renderEntryResult();
    await refresh();
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

/* ---------- exit and payment ---------- */

const PAY_DETAIL = {
  CASH: { label: "Cash tendered", hint: "Amount handed over, e.g. 100" },
  CARD: { label: "Card number", hint: "16 digits" },
  ONLINE: { label: "Mobile number", hint: "11 digits" }
};

function renderExit() {
  renderExitSummary();
  renderPayCard();
}

function renderExitSummary() {
  const box = document.getElementById("exit-summary");
  box.innerHTML = "";
  const t = exitState.ticket;
  if (!t) return;

  const rows = make("div", "rows summary");
  plainRow(rows, "Vehicle", t.plate + " (" + t.vehicleType.toLowerCase() + ")");
  plainRow(rows, "Owner", t.owner);
  plainRow(rows, "Slot", t.slotId + " (" + (TYPE_LABEL[t.slotType] || t.slotType) + ")");
  plainRow(rows, "Entered", formatEntry(t.entryTime));
  plainRow(rows, "Hours charged", String(t.hours));
  box.appendChild(rows);

  const fee = make("div", "fee-box");
  fee.appendChild(make("div", "k", "Amount due"));
  fee.appendChild(make("div", "v", money(t.fee)));
  box.appendChild(fee);
}

function renderPayCard() {
  const card = document.getElementById("exit-pay");
  card.innerHTML = "";

  if (exitState.receipt) {
    renderReceipt(card, exitState.receipt);
    return;
  }

  if (!exitState.ticket) {
    const empty = make("div", "empty-state");
    empty.appendChild(make("h2", "", "Payment"));
    empty.appendChild(make("p", "", "Look up a ticket to take payment."));
    card.appendChild(empty);
    return;
  }

  card.appendChild(make("h2", "", "Payment"));

  const form = make("form", "form");
  form.setAttribute("autocomplete", "off");

  const methodField = make("div", "field");
  methodField.appendChild(make("span", "", "Payment method"));
  const choices = make("div", "choices");
  choices.id = "pay-methods";
  for (const key of Object.keys(METHOD_LABEL)) {
    const button = make("button", "choice");
    button.setAttribute("type", "button");
    button.dataset.value = key;
    button.appendChild(make("b", "", METHOD_LABEL[key]));
    choices.appendChild(button);
  }
  methodField.appendChild(choices);
  form.appendChild(methodField);

  const input = make("input");
  input.id = "pay-detail";
  input.setAttribute("type", "text");
  const detailLabel = make("label", "field");
  const detailText = make("span", "", "");
  detailLabel.appendChild(detailText);
  detailLabel.appendChild(input);
  form.appendChild(detailLabel);

  const message = make("div", "form-msg");
  message.id = "pay-msg";
  message.hidden = true;
  form.appendChild(message);

  const pay = make("button", "btn", "Pay and exit");
  pay.setAttribute("type", "submit");
  form.appendChild(pay);

  function updateDetail() {
    const info = PAY_DETAIL[exitState.method];
    detailText.textContent = info.label;
    input.setAttribute("placeholder", info.hint);
    input.value = "";
  }

  for (const button of choices.querySelectorAll(".choice")) {
    button.addEventListener("click", () => {
      exitState.method = button.dataset.value;
      markChoice(choices, exitState.method);
      updateDetail();
      setMessage(message, "", "");
    });
  }
  markChoice(choices, exitState.method);
  updateDetail();

  form.addEventListener("submit", event => {
    event.preventDefault();
    submitPayment(input.value, message);
  });

  card.appendChild(form);
}

function renderReceipt(card, receipt) {
  const head = make("div", "ticket-head");
  const mark = make("div", "success-mark");
  mark.innerHTML = ICON_CHECK;
  head.appendChild(mark);
  head.appendChild(make("h2", "", "Payment successful"));
  head.appendChild(make("p", "muted", "Ticket " + receipt.ticketId + " is closed and the slot is free."));
  card.appendChild(head);

  const rows = make("div", "rows");
  plainRow(rows, "Vehicle", receipt.plate);
  plainRow(rows, "Slot", receipt.slotId);
  plainRow(rows, "Hours charged", String(receipt.hours));
  plainRow(rows, "Method", METHOD_LABEL[receipt.method] || receipt.method);
  plainRow(rows, "Amount paid", money(receipt.amount));
  if (receipt.change > 0) plainRow(rows, "Change", money(receipt.change));
  plainRow(rows, "Paid at", formatEntry(receipt.time));
  card.appendChild(rows);

  const next = make("button", "btn", "Check out another vehicle");
  next.addEventListener("click", () => {
    exitState.receipt = null;
    exitState.ticket = null;
    document.getElementById("exit-ticket").value = "";
    setMessage(document.getElementById("lookup-msg"), "", "");
    renderExit();
    document.getElementById("exit-ticket").focus();
  });
  card.appendChild(next);

  const back = make("button", "btn-ghost", "Back to dashboard");
  back.addEventListener("click", () => showView("dashboard"));
  card.appendChild(back);
}

async function lookupTicket(rawId) {
  const message = document.getElementById("lookup-msg");
  setMessage(message, "", "");
  exitState.receipt = null;

  const id = rawId.trim();
  if (id === "") {
    exitState.ticket = null;
    renderExit();
    setMessage(message, "Enter a ticket ID.", "error");
    return;
  }

  try {
    exitState.ticket = await sendRequest("/api/ticket?id=" + encodeURIComponent(id));
  } catch (error) {
    exitState.ticket = null;
    setMessage(message, error.message, "error");
  }
  renderExit();
}

async function submitPayment(detail, message) {
  setMessage(message, "", "");
  try {
    exitState.receipt = await postForm("/api/checkout", {
      ticketId: exitState.ticket.ticketId,
      method: exitState.method,
      detail: detail
    });
    exitState.ticket = null;
    renderExit();
    await refresh();
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

function openCheckout(ticketId) {
  document.getElementById("exit-ticket").value = ticketId;
  showView("exit");
  lookupTicket(ticketId);
}

/* ---------- start ---------- */

for (const button of document.querySelectorAll("#entry-vehicle .choice")) {
  button.insertAdjacentHTML("afterbegin", VEHICLE_SVG[button.dataset.value]);
}

setupChoices("entry-vehicle", entryState, "vehicleType");
setupChoices("entry-slot", entryState, "slotType");

document.getElementById("entry-form").addEventListener("submit", submitEntry);
document.getElementById("lookup-form").addEventListener("submit", event => {
  event.preventDefault();
  lookupTicket(document.getElementById("exit-ticket").value);
});

renderEntryAvailability();
renderEntryResult();
renderExit();