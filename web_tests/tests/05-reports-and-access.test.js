const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const { startServer } = require("../helpers/server");
const { launch, signIn, openView } = require("../helpers/browser");

let server, browser, attendant;
before(async () => {
  server = await startServer();
  browser = await launch();
  attendant = await signIn(browser, server, "attendant");
});
after(async () => { await browser?.close(); server?.stop(); });

const number = async id => Number((await attendant.textContent(id)).replace(/[^\d.]/g, ""));

test("the Reports page shows the stats and all five charts", async () => {
  await openView(attendant, "reports");
  await attendant.waitForSelector("#chart-revenue svg");
  for (const id of ["#chart-revenue", "#chart-hours", "#chart-slottypes", "#chart-duration", "#chart-methods"]) {
    assert.ok(await attendant.locator(id + " svg").count(), id + " has a chart");
  }
  assert.ok((await number("#rpt-total")) > 0, "revenue is shown");
  assert.ok((await number("#rpt-count")) > 0, "payments are counted");
});

test("choosing a longer period changes the numbers", async () => {
  const week = await number("#rpt-count");
  await attendant.click('#range-chips .chip[data-range="90"]');
  await attendant.waitForFunction(old => Number(document.getElementById("rpt-count").textContent) > old, week);
  assert.match(await attendant.textContent("#rpt-total-meta"), /90 days/);
});

test("the payments can be downloaded as a CSV file", async () => {
  const [download] = await Promise.all([attendant.waitForEvent("download"), attendant.click("#rpt-csv")]);
  assert.match(download.suggestedFilename(), /^smartpark-payments-\d{4}-\d{2}-\d{2}\.csv$/);
  const text = fs.readFileSync(await download.path(), "utf8");
  const lines = text.trim().split(/\r?\n/);
  assert.match(lines[0], /Reference,Ticket,Plate,Slot,Slot type,Hours,Method,Amount,Date,Time/);
  assert.equal(lines.length - 1, await number("#rpt-count"), "one line per payment");
});

test("the print button builds a full report", async () => {
  await attendant.evaluate(() => { window.print = () => { window.__printed = true; }; });
  await attendant.click("#rpt-print");
  await attendant.waitForFunction(() => window.__printed === true);
  const text = (await attendant.textContent("#print-report")).replace(/\s+/g, " ");
  assert.match(text, /Revenue report/);
  assert.match(text, /By payment method/);
  assert.match(text, /All payments/);
});

test("a driver cannot open the staff pages or call their addresses", async () => {
  const driver = await signIn(browser, server, "driver");
  const answer = await driver.evaluate(async () => (await fetch("/api/receipts?limit=5")).status);
  assert.equal(answer, 403);
  const park = await driver.evaluate(async () =>
    (await fetch("/api/park", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "plate=X" })).status);
  assert.equal(park, 403);
});

test("signed-out requests to the API are refused", async () => {
  const context = await browser.newContext();
  const response = await context.request.get(server.url + "/api/receipts?limit=5");
  assert.equal(response.status(), 401);
});
