/* Driver page: reserve a slot, see the reservations I hold, and cancel one.
   Uses helpers from app.js (make, money, getJson, currentView, typeChip) and from flows.js (postForm, setMessage). */

const reserveState = { data: null, plate: null, slotType: null, message: null };

async function loadReservations(force) {
  if (!canUse("SELF_SERVICE")) return;

  try {
    reserveState.data = await getJson("/api/my/reservations");
    renderReservations();
  } catch (error) {
    if (!reserveState.data) {
      const card = document.getElementById("driver-reserve");
      card.innerHTML = "";
      card.appendChild(make("h2", "", "Reserve a slot"));
      card.appendChild(make("p", "empty", "Could not load the reservations. Is the SmartPark server running?"));
    }
  }
}

function startReservations() {
  loadReservations(true);
}

function timeText(iso) {
  return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

function renderReservations() {
  const data = reserveState.data;
  const card = document.getElementById("driver-reserve");
  card.innerHTML = "";

  card.appendChild(make("h2", "", "Reserve a slot"));
  card.appendChild(make("p", "muted",
    "Hold a free slot for " + data.holdMinutes + " minutes. It costs nothing, and it is released if you do not arrive in time."));

  /* the reservations I hold now */
  for (const r of data.reservations) card.appendChild(reservationBox(r));

  if (data.plates.length === 0) {
    card.appendChild(make("p", "muted", "Add a plate number below, then you can reserve a slot for it."));
    return;
  }
  if (data.reservations.length >= data.max) {
    card.appendChild(make("p", "muted", "You hold the most reservations allowed (" + data.max + "). Cancel one to make another."));
    return;
  }

  card.appendChild(reserveForm(data));
}

function reservationBox(r) {
  const box = make("div", "my-reservation");

  const head = make("div", "my-ticket-head");
  head.appendChild(make("b", "my-plate", r.plate));
  head.appendChild(make("span", "pill held", "Slot " + r.slotId));
  box.appendChild(head);

  box.appendChild(make("p", "muted", (TYPE_LABEL[r.slotType] || r.slotType) + " slot, " + money(r.rate)
    + " per hour once you park. Held for " + r.minutesLeft + " more min (until " + timeText(r.expiresAt) + ")."));

  const cancel = make("button", "btn-ghost", "Cancel reservation");
  cancel.setAttribute("type", "button");
  cancel.addEventListener("click", async () => {
    cancel.disabled = true;
    try {
      await postForm("/api/my/reserve/cancel", { id: r.id });
      reserveState.message = { kind: "success", text: "Reservation " + r.id + " cancelled." };
    } catch (error) {
      reserveState.message = { kind: "error", text: error.message };
    }
    loadReservations(true);
  });
  box.appendChild(cancel);
  return box;
}

function reserveForm(data) {
  const form = make("form", "form reserve-form");
  form.id = "reserve-form";
  form.setAttribute("novalidate", "");

  if (!data.plates.includes(reserveState.plate)) reserveState.plate = data.plates[0];

  const plateLabel = make("label", "field");
  plateLabel.appendChild(make("span", "", "For which plate"));
  const plate = make("select", "select reserve-select");
  plate.id = "reserve-plate";
  for (const p of data.plates) {
    const option = make("option", "", p);
    option.value = p;
    if (p === reserveState.plate) option.selected = true;
    plate.appendChild(option);
  }
  plate.addEventListener("change", () => { reserveState.plate = plate.value; });
  plateLabel.appendChild(plate);
  form.appendChild(plateLabel);

  const typeField = make("div", "field");
  typeField.appendChild(make("span", "", "Slot type"));
  const choices = make("div", "choices");
  const usable = data.options.filter(o => o.free > 0);
  if (!usable.some(o => o.type === reserveState.slotType)) {
    reserveState.slotType = usable.length > 0 ? usable[0].type : null;
  }
  for (const option of data.options) {
    const button = make("button", "choice");
    button.setAttribute("type", "button");
    button.dataset.value = option.type;
    button.appendChild(make("b", "", TYPE_LABEL[option.type] || option.type));
    button.appendChild(make("small", "", money(option.rate) + "/hr · " + (option.free > 0 ? option.free + " free" : "Full")));
    button.disabled = option.free === 0;
    button.addEventListener("click", () => {
      reserveState.slotType = option.type;
      markChoice(choices, reserveState.slotType);
    });
    choices.appendChild(button);
  }
  typeField.appendChild(choices);
  form.appendChild(typeField);
  markChoice(choices, reserveState.slotType);

  const message = make("div", "form-msg");
  message.id = "reserve-msg";
  message.hidden = true;
  if (reserveState.message) {
    setMessage(message, reserveState.message.text, reserveState.message.kind);
  }
  form.appendChild(message);

  const submit = make("button", "btn", "Reserve a slot");
  submit.setAttribute("type", "submit");
  submit.disabled = reserveState.slotType === null;
  form.appendChild(submit);
  if (reserveState.slotType === null) {
    message.hidden = false;
    message.className = "form-msg error";
    message.textContent = "Every slot is taken right now. Try again in a few minutes.";
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    submit.disabled = true;
    reserveState.message = null;
    try {
      const made = await postForm("/api/my/reserve", { plate: reserveState.plate, slotType: reserveState.slotType });
      reserveState.message = { kind: "success", text: "Slot " + made.slotId + " is held for " + made.plate + " until " + timeText(made.expiresAt) + "." };
    } catch (error) {
      reserveState.message = { kind: "error", text: error.message };
    }
    loadReservations(true);
    if (typeof loadNotifications === "function") loadNotifications();
  });
  return form;
}
