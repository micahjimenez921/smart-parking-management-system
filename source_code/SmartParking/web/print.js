/* Printable ticket and receipt. Uses helpers from app.js and flows.js. */

function printSlip(title, rows, footer) {
  const slip = document.getElementById("print-slip");
  slip.innerHTML = "";

  slip.appendChild(make("div", "slip-brand", "SmartPark"));
  slip.appendChild(make("div", "slip-title", title));
  slip.appendChild(make("div", "slip-rule"));

  for (const item of rows) {
    const line = make("div", "slip-row" + (item.strong ? " strong" : ""));
    line.appendChild(make("span", "", item.label));
    line.appendChild(make("span", "", item.value));
    slip.appendChild(line);
  }

  slip.appendChild(make("div", "slip-rule"));
  slip.appendChild(make("div", "slip-note", footer));
  slip.appendChild(make("div", "slip-note", "Printed " + new Date().toLocaleString("en-PH")));

  window.print();
}

function printTicket(ticket) {
  printSlip("Parking ticket", [
    { label: "Ticket", value: ticket.ticketId, strong: true },
    { label: "Slot", value: ticket.slotId + " (" + (TYPE_LABEL[ticket.slotType] || ticket.slotType) + ")" },
    { label: "Plate number", value: ticket.plate },
    { label: "Vehicle", value: ticket.vehicleType.toLowerCase() },
    { label: "Owner", value: ticket.owner },
    { label: "Rate", value: money(ticket.rate) + " / hour" },
    { label: "Entered", value: formatEntry(ticket.entryTime) }
  ], "Keep this ticket and show it at the exit.");
}

function printReceipt(receipt) {
  const rows = [
    { label: "Ticket", value: receipt.ticketId },
    { label: "Plate number", value: receipt.plate },
    { label: "Slot", value: receipt.slotId },
    { label: "Hours charged", value: String(receipt.hours) }
  ];
  if (receipt.penalty > 0) {
    rows.push({ label: "Lost ticket penalty", value: money(receipt.penalty) });
  }
  rows.push({ label: "Payment method", value: METHOD_LABEL[receipt.method] || receipt.method });
  rows.push({ label: "Amount paid", value: money(receipt.amount), strong: true });
  if (receipt.change > 0) {
    rows.push({ label: "Change", value: money(receipt.change) });
  }
  rows.push({ label: "Paid at", value: formatEntry(receipt.time) });

  printSlip("Parking receipt", rows, "Thank you. Drive safely!");
}