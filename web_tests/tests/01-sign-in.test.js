const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { startServer } = require("../helpers/server");
const { launch, signIn } = require("../helpers/browser");

let server, browser;
before(async () => { server = await startServer(); browser = await launch(); });
after(async () => { await browser?.close(); server?.stop(); });

async function freshPage() {
  const context = await browser.newContext();
  return context.newPage();
}

test("a signed-out visitor is sent to the sign-in page", async () => {
  const page = await freshPage();
  await page.goto(server.url + "/index.html");
  await page.waitForURL("**/login.html**");
  assert.ok(await page.isVisible("#login-form"));
});

test("a wrong password shows an error and stays on the sign-in page", async () => {
  const page = await freshPage();
  await page.goto(server.url + "/login.html");
  await page.fill("#login-username", "attendant");
  await page.fill("#login-password", "not-the-password");
  await page.click("#login-submit");
  await page.waitForSelector("#login-msg:not([hidden])");
  assert.match(await page.textContent("#login-msg"), /incorrect|wrong|invalid/i);
  assert.match(page.url(), /login\.html/);
});

test("an empty form asks for the username and password", async () => {
  const page = await freshPage();
  await page.goto(server.url + "/login.html");
  await page.click("#login-submit");
  await page.waitForSelector("#login-msg:not([hidden])");
  assert.ok((await page.textContent("#login-msg")).trim().length > 0);
  assert.match(page.url(), /login\.html/);
});

test("the attendant signs in with the form and sees the dashboard, but not Users or Settings", async () => {
  const page = await freshPage();
  await page.goto(server.url + "/login.html");
  await page.fill("#login-username", "attendant");
  await page.fill("#login-password", server.passwords.attendant);
  await page.click("#login-submit");
  await page.waitForURL("**/index.html**");
  await page.waitForSelector("#user-name");
  assert.match(await page.textContent("#user-name"), /Sample Attendant/);
  assert.ok(await page.isVisible("#view-dashboard"));
  assert.ok(await page.isVisible('.nav-item[data-view="entry"]'));
  assert.ok(await page.isVisible('.nav-item[data-view="reports"]'));
  assert.equal(await page.isVisible('.nav-item[data-view="users"]'), false);
  assert.equal(await page.isVisible('.nav-item[data-view="settings"]'), false);
});

test("the administrator also sees Users and Settings", async () => {
  const page = await signIn(browser, server, "admin");
  assert.ok(await page.isVisible('.nav-item[data-view="users"]'));
  assert.ok(await page.isVisible('.nav-item[data-view="settings"]'));
});

test("the driver lands on the driver page and sees no staff pages", async () => {
  const page = await signIn(browser, server, "driver");
  await page.waitForSelector("#view-driver", { state: "visible" });
  assert.match(await page.textContent("#user-name"), /Sample Driver/);
  assert.equal(await page.isVisible('.nav-item[data-view="entry"]'), false);
  assert.equal(await page.isVisible('.nav-item[data-view="reports"]'), false);
  assert.equal(await page.isVisible('.nav-item[data-view="users"]'), false);
});

test("signing out returns to the sign-in page and closes the session", async () => {
  const page = await signIn(browser, server, "attendant");
  await page.click("#logout-button");
  await page.waitForURL("**/login.html**");
  await page.goto(server.url + "/index.html");
  await page.waitForURL("**/login.html**");
});
