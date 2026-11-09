/* Settings page: pricing form and the list of parking slots. Uses helpers from app.js, flows.js and pages.js. */

async function loadSettingsPage() {
  try {
    const results = await Promise.all([getJson("/api/settings"), getJson("/api/slots")]);
    renderSettings(results[0]);
    renderSlotList(results[1]);
  } catch (error) {
    console.error(error);
    document.getElementById("offline").hidden = false;
  }
}

function renderSettings(settings) {
  const box = document.getElementById("rate-fields");
  box.innerHTML = "";

  for (const type of Object.keys(settings.rates)) {
    const label = make("label", "field");
    label.appendChild(make("span", "", (TYPE_LABEL[type] || type) + " rate (per hour)"));

    const input = make("input");
    input.id = "rate-" + type;
    input.setAttribute("type", "text");
    input.setAttribute("inputmode", "decimal");
    input.value = String(settings.rates[type]);

    label.appendChild(input);
    box.appendChild(label);
  }

  document.getElementById("set-cap").value = String(settings.dailyCapHours);
  document.getElementById("set-penalty").value = String(settings.lostTicketPenalty);
  document.getElementById("set-rule").textContent = "Current rule: " + settings.feeRule + ".";
}

async function saveSettings(event) {
  event.preventDefault();
  const message = document.getElementById("settings-msg");
  setMessage(message, "", "");

  const data = {
    dailyCapHours: document.getElementById("set-cap").value,
    lostTicketPenalty: document.getElementById("set-penalty").value
  };
  for (const input of document.querySelectorAll("#rate-fields input")) {
    data["rate_" + input.id.replace("rate-", "")] = input.value;
  }

  try {
    const settings = await postForm("/api/settings", data);
    renderSettings(settings);
    setMessage(message, "Settings saved. The new rates apply to every vehicle at checkout.", "success");
    refresh();
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

function renderSlotList(slots) {
  const body = document.getElementById("slots-body");
  body.innerHTML = "";

  if (slots.length === 0) {
    emptyRow(body, 4, "There are no slots yet.");
    return;
  }

  for (const slot of slots) {
    const tr = make("tr");
    cell(tr, slot.id, "strong");

    const typeCell = make("td");
    typeCell.appendChild(typeChip(slot.type));
    tr.appendChild(typeCell);

    cell(tr, slot.available ? "Free" : "Parked: " + slot.plate);

    const action = make("td");
    const button = make("button", "btn-small danger", "Remove");
    if (slot.available) {
      button.addEventListener("click", () => removeSlot(slot.id));
    } else {
      button.disabled = true;
      button.setAttribute("title", "Only a free slot can be removed.");
    }
    action.appendChild(button);
    tr.appendChild(action);

    body.appendChild(tr);
  }
}

async function addSlot(event) {
  event.preventDefault();
  const message = document.getElementById("slots-msg");
  setMessage(message, "", "");

  try {
    const slot = await postForm("/api/slots/add", { type: document.getElementById("slot-type").value });
    await loadSettingsPage();
    setMessage(message, "Added slot " + slot.id + " at " + money(slot.rate) + " per hour.", "success");
    refresh();
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

async function removeSlot(slotId) {
  if (!window.confirm("Remove slot " + slotId + "?")) return;

  const message = document.getElementById("slots-msg");
  setMessage(message, "", "");

  try {
    await postForm("/api/slots/remove", { id: slotId });
    await loadSettingsPage();
    setMessage(message, "Removed slot " + slotId + ".", "success");
    refresh();
  } catch (error) {
    setMessage(message, error.message, "error");
  }
}

/* ---------- start ---------- */

document.getElementById("settings-form").addEventListener("submit", saveSettings);
document.getElementById("slot-form").addEventListener("submit", addSlot);

window.addEventListener("hashchange", () => {
  if (currentView() === "settings") loadSettingsPage();
});

if (currentView() === "settings") loadSettingsPage();