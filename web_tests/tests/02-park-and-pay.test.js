const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { startServer } = require("../helpers/server");
const { launch, signIn, openView, parkVehicle, lookUpTicket } = require("../helpers/browser");

let server, browser, page, firstTicket;
before(async () => {
  server = await startServer();
  browser = await launch();
  page = await signIn(browser, server, "attendant");
});
after(async () => { await browser?.close(); server?.stop(); });

const receipt = async () => {
  await page.waitForSelector("#exit-pay .success-mark", { timeout: 10000 });
  return (await page.textContent("#exit-pay")).replace(/\s+/g, " ");
};

async function choose(method) {
  await page.click('.co-method[data-value="' + method + '"]');
}

test("parking a vehicle issues a ticket and takes a slot", async () => {
  const id = await parkVehicle(page, "PWT 101", "Test Owner");
  firstTicket = id;
  assert.match(id, /^T-\d+/);
  const text = (await page.textContent("#entry-result")).replace(/\s+/g, " ");
  assert.match(text, /PWT 101/);
  assert.match(text, /Test Owner/);

  await openView(page, "dashboard");
  const busy = await page.locator(".bay.busy", { hasText: "PWT 101" }).count();
  assert.equal(busy, 1, "the slot map shows PWT 101 as occupied");

  await openView(page, "tickets");
  await page.waitForSelector("#view-tickets >> text=PWT 101");
});

test("the same plate cannot be parked twice", async () => {
  await openView(page, "entry");
  await page.fill("#entry-plate", "PWT 101");
  await page.fill("#entry-owner", "Someone Else");
  await page.click('#entry-vehicle .choice[data-value="CAR"]');
  await page.click('#entry-slot .choice[data-value="REGULAR"]');
  await page.click("#entry-form button[type=submit]");
  await page.waitForSelector("#entry-msg:not([hidden])");
  assert.match(await page.textContent("#entry-msg"), /already parked/i);
});

test("the entry form asks for a plate number", async () => {
  await openView(page, "entry");
  await page.fill("#entry-plate", "");
  await page.click("#entry-form button[type=submit]");
  await page.waitForSelector("#entry-msg:not([hidden])");
  assert.ok((await page.textContent("#entry-msg")).trim().length > 0);
});

test("paying cash: too little is refused, the exact amount works", async () => {
  await lookUpTicket(page, firstTicket);
  await choose("CASH");
  await page.fill("#co-cash", "1");
  await page.click(".co-pay");
  await page.waitForSelector("#exit-pay .form-msg.error");
  assert.match(await page.textContent("#exit-pay .form-msg"), /not enough/i);

  await page.click(".co-chip >> text=Exact");
  await page.click(".co-pay");
  const text = await receipt();
  assert.match(text, /Payment successful/);
  assert.match(text, /Cash/);
  assert.match(text, /SP-\d{6}-/, "the receipt has a reference number");

  await openView(page, "dashboard");
  assert.equal(await page.locator(".bay.busy", { hasText: "PWT 101" }).count(), 0, "the slot is free again");
});

test("paying by card (Visa): a bad number is refused, a good one works", async () => {
  const id = await parkVehicle(page, "CRD 202", "Card Owner");
  await lookUpTicket(page, id);
  await choose("CARD");
  await page.fill("#co-card-name", "Card Owner");
  await page.fill("#co-card-number", "1234 5678 9012 3456");
  await page.fill("#co-card-exp", "1230");
  await page.fill("#co-card-cvv", "123");
  await page.click(".co-pay");
  await page.waitForSelector("#exit-pay .form-msg.error");
  assert.match(await page.textContent("#exit-pay .form-msg"), /Visa/);

  await page.fill("#co-card-number", "4111 1111 1111 1111");
  await page.click(".co-pay");
  const text = await receipt();
  assert.match(text, /Card \(Visa\)/);
  assert.match(text, /SP-\d{6}-/);
});

test("paying by GCash needs a valid mobile number", async () => {
  const id = await parkVehicle(page, "GCS 303", "Wallet Owner");
  await lookUpTicket(page, id);
  await choose("GCASH");
  await page.fill("#co-wallet-number", "12345");
  await page.click(".co-pay");
  await page.waitForSelector("#exit-pay .form-msg.error");
  assert.match(await page.textContent("#exit-pay .form-msg"), /GCash/);

  await page.fill("#co-wallet-number", "09171234567");
  await page.click(".co-pay");
  const text = await receipt();
  assert.match(text, /GCash/);
});

test("paying by PayMaya works", async () => {
  const id = await parkVehicle(page, "PMY 404", "Maya Owner");
  await lookUpTicket(page, id);
  await choose("PAYMAYA");
  await page.fill("#co-wallet-number", "09181234567");
  await page.click(".co-pay");
  const text = await receipt();
  assert.match(text, /PayMaya/);
});

test("a lost ticket is found by plate number and a penalty is added", async () => {
  const id = await parkVehicle(page, "LST 505", "Lost Owner");
  await openView(page, "exit");
  await page.click("#lost-toggle");
  await page.fill("#exit-ticket", "LST 505");
  await page.click("#lookup-form button[type=submit]");
  await page.waitForSelector("#exit-pay .checkout");
  assert.match(await page.textContent("#exit-pay"), /Lost ticket penalty/);
  assert.ok(id);
});

test("paid payments appear on the Tickets page with a reference number", async () => {
  await openView(page, "tickets");
  await page.waitForSelector("#payments-count");
  const text = (await page.textContent("#view-tickets")).replace(/\s+/g, " ");
  assert.match(text, /SP-\d{6}-/);
  assert.match(text, /CRD 202/);
});
