/* Entry and exit gate status. Uses helpers from app.js and flows.js. */

const gateStatus = { ENTRY: null, EXIT: null };
const GATE_NAME = { ENTRY: "Entry gate", EXIT: "Exit gate" };

async function refreshGates() {
  try {
    const gates = await getJson("/api/gates");
    for (const gate of gates) {
      gateStatus[gate.id] = gate;
    }
    renderGatesCard();
    renderGateNotices();
  } catch (error) {
    /* the main refresh already shows the offline banner */
  }
}

function gateText(gate) {
  if (!gate || gate.state !== "OPEN") return "Closed";
  return "Open for " + gate.plate + " \u00B7 closes in " + gate.secondsLeft + " s";
}

function renderGatesCard() {
  const list = document.getElementById("gates-list");
  if (!list) return;
  list.innerHTML = "";

  for (const id of ["ENTRY", "EXIT"]) {
    const gate = gateStatus[id];
    const open = !!gate && gate.state === "OPEN";

    const item = make("li");
    const info = make("div", "gate-info");
    info.appendChild(make("div", "gate-name", GATE_NAME[id]));
    info.appendChild(make("div", "gate-sub", gateText(gate)));
    item.appendChild(info);
    item.appendChild(make("span", "pill " + (open ? "free" : "closed"), open ? "Open" : "Closed"));
    list.appendChild(item);
  }
}

function fillNotice(boxId, gateId, plate) {
  const box = document.getElementById(boxId);
  if (!box) return;

  const gate = gateStatus[gateId];
  const open = !!gate && gate.state === "OPEN" && gate.plate === plate;
  const mode = open ? "open" : "closed";

  if (box.dataset.mode !== mode) {
    box.innerHTML = "";
    box.dataset.mode = mode;
    box.className = "gate-notice " + mode;

    box.lineNode = make("div", "gate-line");
    box.appendChild(box.lineNode);

    if (open) {
      const button = make("button", "btn-small", "Vehicle has passed");
      button.addEventListener("click", () => closeGate(gateId));
      box.appendChild(button);
    }
  }

  box.lineNode.textContent = open
    ? GATE_NAME[gateId] + " is open for " + plate + ". It closes by itself in " + gate.secondsLeft + " s."
    : GATE_NAME[gateId] + " is closed.";
}

function renderGateNotices() {
  if (entryState.ticket) fillNotice("entry-gate-notice", "ENTRY", entryState.ticket.plate);
  if (exitState.receipt) fillNotice("exit-gate-notice", "EXIT", exitState.receipt.plate);
}

async function closeGate(gateId) {
  try {
    await postForm("/api/gate/close", { gate: gateId });
  } catch (error) {
    console.error(error);
  }
  refreshGates();
}

refreshGates();
setInterval(refreshGates, 1000);