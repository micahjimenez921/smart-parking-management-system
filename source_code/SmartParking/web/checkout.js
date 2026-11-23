/* The payment screen, shared by the attendant (Exit and payment) and by drivers.
 *
 * buildCheckout({ methods, total, rows, totalLabel, cancel, onPay }) returns a
 * <form>. The person picks a method on the left, fills in its details, and pays
 * from the summary on the right. onPay(method, detail) must return a promise;
 * if it throws, the message is shown under the button.
 *
 * This is a demo checkout: the card and wallet steps are simulated and nothing
 * is charged. Card details are checked in the browser and never sent to the server;
 * only the card number is sent, so the server can run the same rule as for cash.
 */

const ICON_BANKNOTE =
  '<svg class="ico" viewBox="0 0 24 24"><rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/><path d="M6 12h.01M18 12h.01"/></svg>';

const CHECKOUT_METHODS = {
  CASH: { label: "Cash", mark: '<span class="co-mark co-cash">' + ICON_BANKNOTE + "</span>" },
  CARD: { label: "Card (Visa)", mark: '<span class="co-mark co-visa">VISA</span>' },
  GCASH: { label: "GCash", mark: '<span class="co-mark co-gcash">GCash</span>' },
  PAYMAYA: { label: "PayMaya", mark: '<span class="co-mark co-maya">PayMaya</span>' }
};

/* how long the simulated approval takes, in milliseconds */
const CHECKOUT_WAIT = { CASH: 0, CARD: 900, GCASH: 1600, PAYMAYA: 1600 };

function onlyDigits(text) {
  return String(text).replace(/\D/g, "");
}

function coField(labelText, input, hint) {
  const label = make("label", "co-field");
  label.appendChild(make("span", "", labelText));
  label.appendChild(input);
  if (hint) label.appendChild(make("small", "", hint));
  return label;
}

function coInput(id, type, placeholder, mode) {
  const input = make("input");
  input.id = id;
  input.setAttribute("type", type);
  input.setAttribute("placeholder", placeholder);
  input.setAttribute("autocomplete", "off");
  if (mode) input.setAttribute("inputmode", mode);
  return input;
}

/* ---------- the three kinds of details panel ---------- */

function cashPanel(total) {
  const el = make("div", "co-panel");
  const input = coInput("co-cash", "text", "0.00", "decimal");
  const prefixed = make("div", "co-prefix");
  prefixed.appendChild(make("b", "", "₱"));
  prefixed.appendChild(input);
  el.appendChild(coField("Cash received", prefixed));

  const quick = make("div", "co-quick");
  const amounts = [{ text: "Exact", value: total }];
  for (const value of [100, 200, 500, 1000]) {
    if (value >= total) amounts.push({ text: money(value).replace(".00", ""), value: value });
  }
  for (const amount of amounts.slice(0, 4)) {
    const button = make("button", "co-chip", amount.text);
    button.setAttribute("type", "button");
    button.addEventListener("click", () => {
      input.value = Number(amount.value).toFixed(2);
      showChange();
    });
    quick.appendChild(button);
  }
  el.appendChild(quick);

  const change = make("div", "co-change");
  el.appendChild(change);

  function received() {
    const value = parseFloat(input.value.replace(/,/g, ""));
    return isNaN(value) ? 0 : value;
  }

  function showChange() {
    if (input.value.trim() === "") {
      change.textContent = "";
      change.className = "co-change";
    } else if (received() >= total) {
      change.textContent = "Change to give back: " + money(received() - total);
      change.className = "co-change ok";
    } else {
      change.textContent = "Still short by " + money(total - received());
      change.className = "co-change short";
    }
  }
  input.addEventListener("input", showChange);

  return {
    el: el,
    focus: () => input.focus(),
    validate() {
      if (input.value.trim() === "" || isNaN(parseFloat(input.value.replace(/,/g, "")))) {
        return "Enter the cash received.";
      }
      if (received() < total) return "Cash is not enough. Amount due: " + money(total) + ".";
      return null;
    },
    detail: () => String(received())
  };
}

function cardPanel() {
  const el = make("div", "co-panel");

  const preview = make("div", "co-card-preview");
  const chip = make("span", "co-chip-art");
  const previewNumber = make("div", "co-card-number", "•••• •••• •••• ••••");
  const previewName = make("div", "co-card-name", "CARDHOLDER NAME");
  const previewExp = make("div", "co-card-exp", "MM/YY");
  const previewBrand = make("div", "co-card-brand", "VISA");
  preview.appendChild(chip);
  preview.appendChild(previewBrand);
  preview.appendChild(previewNumber);
  const bottom = make("div", "co-card-bottom");
  bottom.appendChild(previewName);
  bottom.appendChild(previewExp);
  preview.appendChild(bottom);
  el.appendChild(preview);

  const name = coInput("co-card-name", "text", "Name on card");
  name.setAttribute("autocomplete", "off");
  name.setAttribute("maxlength", "40");
  el.appendChild(coField("Cardholder name", name));

  const number = coInput("co-card-number", "text", "4111 1111 1111 1111", "numeric");
  number.setAttribute("maxlength", "19");
  el.appendChild(coField("Card number", number, "A Visa card number starts with 4."));

  const row = make("div", "co-row");
  const exp = coInput("co-card-exp", "text", "MM/YY", "numeric");
  exp.setAttribute("maxlength", "5");
  const cvv = coInput("co-card-cvv", "password", "123", "numeric");
  cvv.setAttribute("maxlength", "3");
  row.appendChild(coField("Expiry date", exp));
  row.appendChild(coField("Security code", cvv));
  el.appendChild(row);

  name.addEventListener("input", () => {
    previewName.textContent = name.value.trim() === "" ? "CARDHOLDER NAME" : name.value.toUpperCase();
  });
  number.addEventListener("input", () => {
    const digits = onlyDigits(number.value).slice(0, 16);
    number.value = digits.replace(/(\d{4})(?=\d)/g, "$1 ");
    const padded = (digits + "•".repeat(16)).slice(0, 16);
    previewNumber.textContent = padded.replace(/(.{4})(?=.)/g, "$1 ");
  });
  exp.addEventListener("input", () => {
    let digits = onlyDigits(exp.value).slice(0, 4);
    if (digits.length >= 3) digits = digits.slice(0, 2) + "/" + digits.slice(2);
    exp.value = digits;
    previewExp.textContent = digits === "" ? "MM/YY" : digits;
  });
  cvv.addEventListener("input", () => { cvv.value = onlyDigits(cvv.value).slice(0, 3); });

  return {
    el: el,
    focus: () => name.focus(),
    validate() {
      if (name.value.trim() === "") return "Enter the name on the card.";
      const digits = onlyDigits(number.value);
      if (digits.length !== 16 || digits[0] !== "4") return "Enter a 16-digit Visa card number starting with 4.";
      const match = /^(\d{2})\/(\d{2})$/.exec(exp.value);
      if (!match || Number(match[1]) < 1 || Number(match[1]) > 12) return "Enter the expiry date as MM/YY.";
      const endOfMonth = new Date(2000 + Number(match[2]), Number(match[1]), 1);
      if (endOfMonth <= new Date()) return "This card has expired.";
      if (cvv.value.length !== 3) return "Enter the 3-digit security code.";
      return null;
    },
    detail: () => onlyDigits(number.value)
  };
}

function walletPanel(method) {
  const wallet = CHECKOUT_METHODS[method].label;
  const el = make("div", "co-panel");

  el.appendChild(make("p", "co-wallet-note",
    "Enter the mobile number linked to your " + wallet + " account. A request for the amount is sent to your "
    + wallet + " app, and you approve it there."));

  const input = coInput("co-wallet-number", "tel", "09XX XXX XXXX", "numeric");
  input.setAttribute("maxlength", "13");
  el.appendChild(coField(wallet + " mobile number", input, "11 digits, starting with 09."));

  input.addEventListener("input", () => {
    const digits = onlyDigits(input.value).slice(0, 11);
    input.value = digits.replace(/^(\d{4})(\d{0,3})(\d{0,4}).*$/, (all, a, b, c) =>
      [a, b, c].filter(Boolean).join(" "));
  });

  return {
    el: el,
    focus: () => input.focus(),
    validate() {
      return /^09\d{9}$/.test(onlyDigits(input.value))
        ? null
        : "Enter the " + wallet + " mobile number: 11 digits starting with 09.";
    },
    detail: () => onlyDigits(input.value)
  };
}

function panelFor(method, total) {
  if (method === "CASH") return cashPanel(total);
  if (method === "CARD") return cardPanel();
  return walletPanel(method);
}

/* ---------- the checkout itself ---------- */

function buildCheckout(opts) {
  const form = make("form", "checkout");
  form.setAttribute("autocomplete", "off");
  form.setAttribute("novalidate", "novalidate");

  let method = opts.methods[0];
  let panel = null;
  let busy = false;

  /* left: method and details */
  const main = make("div", "co-main");
  main.appendChild(make("h3", "co-title", "Payment method"));

  const tiles = make("div", "co-methods");
  tiles.setAttribute("role", "radiogroup");
  tiles.setAttribute("aria-label", "Payment method");
  tiles.style.setProperty("--count", String(opts.methods.length));
  for (const key of opts.methods) {
    const tile = make("button", "co-method");
    tile.setAttribute("type", "button");
    tile.setAttribute("role", "radio");
    tile.dataset.value = key;
    tile.insertAdjacentHTML("afterbegin", CHECKOUT_METHODS[key].mark);
    tile.appendChild(make("span", "co-method-name", CHECKOUT_METHODS[key].label));
    tile.addEventListener("click", () => choose(key));
    tiles.appendChild(tile);
  }
  main.appendChild(tiles);

  const details = make("div", "co-details");
  main.appendChild(details);
  form.appendChild(main);

  /* right: summary and the pay button */
  const side = make("aside", "co-summary");
  side.appendChild(make("h3", "co-title", opts.summaryTitle || "Summary"));

  const list = make("div", "co-lines");
  for (const row of opts.rows) {
    const line = make("div", "co-line");
    line.appendChild(make("span", "", row[0]));
    line.appendChild(make("b", "", row[1]));
    list.appendChild(line);
  }
  side.appendChild(list);

  const total = make("div", "co-total");
  total.appendChild(make("span", "", opts.totalLabel || "Total"));
  total.appendChild(make("b", "", money(opts.total)));
  side.appendChild(total);

  const message = make("div", "form-msg");
  message.hidden = true;
  message.setAttribute("role", "alert");
  side.appendChild(message);

  const pay = make("button", "btn co-pay", "Pay " + money(opts.total));
  pay.setAttribute("type", "submit");
  side.appendChild(pay);

  if (opts.cancel) {
    const cancel = make("button", "btn-ghost", "Cancel");
    cancel.setAttribute("type", "button");
    cancel.addEventListener("click", () => { if (!busy) opts.cancel(); });
    side.appendChild(cancel);
  }

  side.appendChild(make("p", "co-note", "Demo checkout. No real payment is made."));
  form.appendChild(side);

  function choose(key) {
    if (busy) return;
    method = key;
    for (const tile of tiles.querySelectorAll(".co-method")) {
      const selected = tile.dataset.value === key;
      tile.classList.toggle("selected", selected);
      tile.setAttribute("aria-checked", String(selected));
    }
    panel = panelFor(key, opts.total);
    details.innerHTML = "";
    details.appendChild(panel.el);
    setMessage(message, "", "");
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (busy) return;

    const problem = panel.validate();
    if (problem) {
      setMessage(message, problem, "error");
      return;
    }

    busy = true;
    setMessage(message, "", "");
    pay.disabled = true;
    const wait = CHECKOUT_WAIT[method];
    if (wait > 0) {
      pay.textContent = method === "CARD"
        ? "Processing card..."
        : "Waiting for approval in " + CHECKOUT_METHODS[method].label + "...";
      await new Promise(resolve => setTimeout(resolve, wait));
    }

    try {
      await opts.onPay(method, panel.detail());
    } catch (error) {
      setMessage(message, error.message, "error");
      pay.disabled = false;
      pay.textContent = "Pay " + money(opts.total);
      busy = false;
    }
  });

  choose(method);

  /* the wrapper is what the layout measures, so the checkout can stack in narrow cards */
  const wrap = make("div", "checkout-wrap");
  wrap.appendChild(form);
  return wrap;
}
