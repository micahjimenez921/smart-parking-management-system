const { chromium } = require("playwright");

// SMARTPARK_BROWSER=chrome uses the Chrome that is already installed (no download needed).
function launch() {
  const channel = process.env.SMARTPARK_BROWSER;
  return chromium.launch(channel ? { channel } : {});
}

/**
 * Opens a new signed-in page. `who` is attendant, driver or admin.
 * It signs in through the server (like the form does) so the heavy animated sign-in page is not drawn
 * in every test. 01-sign-in.test.js still uses the real form.
 */
async function signIn(browser, server, who) {
  const username = who === "admin" ? Object.keys(server.passwords).find(u => u !== "attendant" && u !== "driver") : who;
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const response = await context.request.post(server.url + "/api/login", {
    form: { username, password: server.passwords[username] }
  });
  if (!response.ok()) throw new Error("Sign-in failed for " + username + ": " + response.status());
  const page = await context.newPage();
  await page.goto(server.url + "/index.html");
  await page.waitForSelector("#user-name");
  return page;
}

/** Opens a page of the signed-in app (dashboard, entry, exit, tickets, reports, driver ...). */
async function openView(page, name) {
  await page.click('.nav-item[data-view="' + name + '"]');
  await page.waitForSelector("#view-" + name + ":not([hidden])", { state: "visible" });
}

/** Parks a vehicle from the Vehicle entry page and returns the ticket id. */
async function parkVehicle(page, plate, owner, vehicle = "CAR", slotType = "REGULAR") {
  await openView(page, "entry");
  await page.fill("#entry-plate", plate);
  await page.fill("#entry-owner", owner);
  await page.click('#entry-vehicle .choice[data-value="' + vehicle + '"]');
  await page.click('#entry-slot .choice[data-value="' + slotType + '"]');
  await page.click("#entry-form button[type=submit]");
  // the card of the previous ticket stays on screen, so wait for the one with this plate
  await page.waitForFunction(p => document.getElementById("entry-result").textContent.includes(p), plate);
  return (await page.textContent("#entry-result .ticket-id")).trim();
}

/** Looks up a ticket on the Exit and payment page. */
async function lookUpTicket(page, ticketId) {
  await openView(page, "exit");
  await page.fill("#exit-ticket", ticketId);
  await page.click("#lookup-form button[type=submit]");
  await page.waitForSelector("#exit-pay .checkout");
}

module.exports = { launch, signIn, openView, parkVehicle, lookUpTicket };
