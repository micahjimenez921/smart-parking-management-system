const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { startServer } = require("../helpers/server");
const { launch, signIn, openView, parkVehicle } = require("../helpers/browser");

let server, browser, driver, attendant;
before(async () => {
  server = await startServer();
  browser = await launch();
  driver = await signIn(browser, server, "driver");
  attendant = await signIn(browser, server, "attendant");
});
after(async () => { await browser?.close(); server?.stop(); });

test("a driver sees their parked vehicle and the fee so far", async () => {
  await parkVehicle(attendant, "SAM 123", "Sample Driver");
  await driver.reload();
  await driver.waitForSelector("#driver-parked .my-ticket");
  const text = (await driver.textContent("#driver-parked")).replace(/\s+/g, " ");
  assert.match(text, /SAM 123/);
  assert.match(text, /Fee so far/);
});

test("the driver pays by GCash and gets a receipt", async () => {
  await driver.getByRole("button", { name: "Pay now" }).click();
  await driver.waitForSelector("#driver-parked .checkout");
  assert.equal(await driver.locator('#driver-parked .co-method[data-value="CASH"]').count(), 0, "cash is for the attendant");

  await driver.click('#driver-parked .co-method[data-value="GCASH"]');
  await driver.fill("#co-wallet-number", "09171234567");
  await driver.click("#driver-parked .co-pay");
  await driver.waitForSelector("#driver-parked .success-mark", { timeout: 10000 });
  const text = (await driver.textContent("#driver-parked")).replace(/\s+/g, " ");
  assert.match(text, /Payment successful/);
  assert.match(text, /SP-\d{6}-/);
});

test("the payment shows in the driver's history, and the slot is free", async () => {
  await driver.getByRole("button", { name: "Done" }).click();
  await driver.waitForSelector("#driver-payments tbody tr.pay-row");
  const text = (await driver.textContent("#driver-payments")).replace(/\s+/g, " ");
  assert.match(text, /SAM 123/);
  assert.match(text, /GCash/);

  await attendant.reload();
  await openView(attendant, "dashboard");
  await attendant.waitForSelector(".bay", { state: "visible" });
  assert.equal(await attendant.locator(".bay.busy", { hasText: "SAM 123" }).count(), 0);
});
