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
  printSlip("Official receipt", receiptLines(receipt), "Thank you. Drive safely! Keep this receipt as your proof of payment.");
}
