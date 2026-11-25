# SmartPark browser tests

These tests open the real web pages in a browser and click through them, like a person would.
The Java tests in `source_code/SmartParking/src/tests` check the code. These check the pages.

## What is tested (30 checks)

| File | What it does |
|---|---|
| `01-sign-in.test.js` | Signed-out visitors are sent to sign-in, wrong and empty passwords are refused, each role sees only its own pages, sign-out works |
| `02-park-and-pay.test.js` | Park a vehicle, refuse a duplicate plate, pay with Cash, Card (Visa), GCash and PayMaya (including bad input), lost ticket penalty, receipt reference numbers |
| `03-reservation.test.js` | Driver reserves a slot, presses "I have arrived", the attendant sees "Arrived", registers the vehicle from the slot, driver cancels a reservation |
| `04-driver-pay.test.js` | A driver sees their parked vehicle, pays by GCash, and finds the payment in their history |
| `05-reports-and-access.test.js` | Reports page charts, 7/30/90 day period, CSV download, printed report, driver is blocked from staff pages and addresses |

Each file starts its own private SmartPark server with empty sample data on a free port.
Your real `data/` folder is never touched, and your normal server on port 8080 can stay open.

## What you need

- Java JDK 17 or newer (you already have this for SmartPark)
- Node.js 20 or newer (https://nodejs.org)

## Run the tests

Open a terminal in this `web_tests` folder, then:

```
npm install
npx playwright install chromium
npm test
```

The first command installs Playwright. The second downloads a test browser (once). The third runs all tests and takes about two minutes.

If you would rather use the Chrome that is already on your computer, skip the second command and set this before `npm test`:

```
set SMARTPARK_BROWSER=chrome        (Windows Command Prompt)
$env:SMARTPARK_BROWSER="chrome"     (Windows PowerShell)
```

To run one file only: `node --test tests/03-reservation.test.js`

## Good to know

- The tests compile the Java code themselves (into `web_tests/.build`, which git ignores).
- When a test fails, the message says which page step did not work.
- The sample accounts are created fresh for every test file, so tests do not depend on each other's data.
