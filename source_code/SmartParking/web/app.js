function money(value) {
  return "\u20B1" + value.toLocaleString("en-PH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function addCell(row, text) {
  const cell = document.createElement("td");
  cell.textContent = text;
  row.appendChild(cell);
}

async function loadStats() {
  const response = await fetch("/api/stats");
  const stats = await response.json();

  document.getElementById("stat-total").textContent = stats.total;
  document.getElementById("stat-available").textContent = stats.available;
  document.getElementById("stat-occupied").textContent = stats.occupied;
  document.getElementById("stat-revenue").textContent = money(stats.revenue);
}

async function loadSlots() {
  const response = await fetch("/api/slots");
  const slots = await response.json();

  const body = document.getElementById("slot-rows");
  body.innerHTML = "";

  for (const slot of slots) {
    const row = document.createElement("tr");
    addCell(row, slot.id);
    addCell(row, slot.type);
    addCell(row, money(slot.rate));
    addCell(row, slot.available ? "Available" : "Occupied");
    addCell(row, slot.plate ? slot.plate + " (" + slot.vehicleType + ")" : "-");
    addCell(row, slot.ticketId || "-");
    body.appendChild(row);
  }
}

loadStats();
loadSlots();