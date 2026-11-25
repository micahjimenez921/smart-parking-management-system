const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { startServer } = require("../helpers/server");
const { launch, signIn, openView } = require("../helpers/browser");

let server, browser, driver, attendant;
before(async () => {
  server = await startServer();
  browser = await launch();
  driver = await signIn(browser, server, "driver");
  attendant = await signIn(browser, server, "attendant");
});
after(async () => { await browser?.close(); server?.stop(); });

const reserveBox = () => driver.locator("#driver-reserve");

test("a driver reserves a slot and sees the reservation", async () => {
  await driver.waitForSelector("#reserve-form");
  await driver.click("#reserve-form button[type=submit]");
  await driver.waitForSelector("#driver-reserve .my-reservation");
  const text = (await reserveBox().textContent()).replace(/\s+/g, " ");
  assert.match(text, /SAM 123/);
  assert.match(text, /Slot [A-Z]-\d+/);
  assert.ok(await reserveBox().getByRole("button", { name: "I have arrived" }).isVisible());
});

test("the attendant sees the reserved slot as not arrived yet", async () => {
  await attendant.reload();
  await attendant.waitForSelector(".bay.reserved");
  assert.match(await attendant.textContent(".bay.reserved"), /Held: SAM 123/);
});

test("'I have arrived' checks in, and the attendant is told", async () => {
  await reserveBox().getByRole("button", { name: "I have arrived" }).click();
  await driver.waitForSelector("#driver-reserve .pill >> text=Checked in");
  assert.match(await reserveBox().textContent(), /attendant knows you are here/i);
  assert.equal(await reserveBox().getByRole("button", { name: "I have arrived" }).count(), 0);

  await attendant.reload();
  await attendant.waitForSelector(".bay.reserved");
  assert.match(await attendant.textContent(".bay.reserved"), /Arrived: SAM 123/);
});

test("the attendant registers the vehicle straight from the reserved slot", async () => {
  await attendant.click(".bay.reserved");
  await attendant.getByRole("button", { name: "Register vehicle now" }).click();
  await attendant.waitForSelector("#view-entry", { state: "visible" });
  assert.equal(await attendant.inputValue("#entry-plate"), "SAM 123");

  await attendant.fill("#entry-owner", "Sample Driver");
  await attendant.click('#entry-vehicle .choice[data-value="CAR"]');
  await attendant.click("#entry-form button[type=submit]");
  await attendant.waitForSelector("#entry-result .ticket-id");
  assert.match(await attendant.textContent("#entry-result"), /had a reservation/i);

  await openView(attendant, "dashboard");
  assert.equal(await attendant.locator(".bay.reserved").count(), 0, "the reservation was used up");
  assert.equal(await attendant.locator(".bay.busy", { hasText: "SAM 123" }).count(), 1);
});

test("a driver can cancel a reservation", async () => {
  // SAM 123 is parked now, so use a second plate for this driver
  await driver.reload();
  await driver.waitForSelector("#plate-input");
  await driver.fill("#plate-input", "NEW 777");
  await driver.press("#plate-input", "Enter");
  await driver.waitForSelector(".plate-chip >> text=NEW 777");

  await driver.selectOption("#reserve-plate", "NEW 777");
  await driver.click("#reserve-form button[type=submit]");
  await driver.waitForSelector("#driver-reserve .my-reservation");
  await driver.getByRole("button", { name: "Cancel reservation" }).click();
  await driver.waitForFunction(() => document.querySelectorAll("#driver-reserve .my-reservation").length === 0);
});
