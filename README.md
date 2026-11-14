# Smart Parking Management System

A web-based Object-Oriented Parking Management System written in Java. It automates vehicle entry, slot assignment, time tracking, fee calculation, payment, reservations, and exit, with separate sign-ins for administrators, parking attendants, and drivers.

---

## Project Description

SmartPark is a capstone project for the Object-Oriented Programming course. It simulates the full lifecycle of a driver entering a parking facility, being assigned a slot, having their parking time tracked, paying the fee, and exiting.

The system is built around Object-Oriented design principles (encapsulation, inheritance, polymorphism, and abstraction) and is modeled with UML diagrams (Use Case, Class, Sequence, and Activity).

### Who uses the system

Every person signs in with their own account. What they see depends on their role, and the server checks the role on every request, not only in the menu.

| Role | What they can do |
|---|---|
| Administrator | Everything an attendant can do, plus change prices and slots on the Settings page and manage accounts on the Users page |
| Parking Attendant | Operate the dashboard: register vehicles, handle exit and payment, view tickets, receipts and reports, and manage reservations |
| Driver | See their own parked vehicles and the fee so far, pay online, reserve a slot, and save their plate numbers |

Drivers can register themselves on the sign-in page. Staff accounts can only be created by an administrator.

---

## Objectives

1. Apply OOP principles in a real-world scenario, for example private slot state, the Vehicle, ParkingSlot and User class hierarchies, and the PaymentMethod and FeeStrategy interfaces.
2. Design a complete UML model (Use Case, Class, Sequence, Activity) that reflects the implemented system.
3. Automate parking operations: slot assignment, duration tracking, fee computation, and payment.
4. Protect data and access: no double-booking of slots, no duplicate plate numbers, payment before a slot is released, hashed passwords, and role-based access.
5. Keep the architecture modular so real hardware (barrier gates, sensors), real payment gateways, and a database can be added later.
6. Keep a maintainable codebase with clear separation between entities, services, storage, security, and the web API.

---

## Key Features

### Parking operations
- **Vehicle entry**: registers the vehicle type (car, motorcycle, or truck), plate number, and owner.
- **Automatic slot assignment**: finds the first free slot of the chosen type (Regular, Handicapped, or VIP).
- **Ticket generation**: records the entry time and issues a unique ticket. Tickets and receipts can be printed.
- **Fee calculation**: hourly rates per slot type, with the time rounded up to whole hours. A daily cap (8 hours by default) stops the fee from growing past a set number of hours per day.
- **Lost tickets**: a vehicle can be found by plate number (ignoring case, spaces, and dashes), and a flat penalty (100 by default) is added to the fee.
- **Payment**: cash (with change), card, or online. A failed payment keeps the ticket open and the slot occupied so it can be retried.
- **Gate simulation**: the entry gate opens when a vehicle is parked, and the exit gate opens only after a successful payment. Gates close after 15 seconds or when the vehicle has passed.
- **Slot release**: the slot is freed only after exit.

### Accounts and access
- **Sign-in with roles**: Administrator, Parking Attendant, and Driver, each with their own menu.
- **Secure passwords**: stored hashed, never as plain text, with a minimum length. Five wrong passwords in a row lock the account for 60 seconds.
- **Sessions**: a sign-in lasts 30 minutes. The session cookie is HttpOnly and SameSite=Strict.
- **Users page (administrator)**: create accounts, rename people, reset passwords, and delete accounts.
- **My account**: every user can change their name and password.

### Drivers
- **Driver page**: parked vehicles, the fee so far, online payment, and saved plate numbers.
- **Reservations**: reserve a free slot for 30 minutes. Unused reservations expire. Attendants can see and cancel them.
- **Notifications**: the bell in the top bar shows real events, such as reservations and accounts.

### Management and reports
- **Settings page (administrator)**: change slot rates, the daily cap, and the lost-ticket penalty, and add or remove parking slots. Everything is saved.
- **Parking map and reports**: a color-coded slot map, plus charts for revenue (7 or 30 days), parking duration, and payment methods.
- **Data persistence**: parked vehicles, payments, ticket numbers, settings, and accounts are saved to files and restored on startup.
- **Sign-in page**: an animated 3D parking lot built with the three.js library, which is included in the project so it works offline.
- **Light and dark mode**: switchable from the top bar.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 17+ (built-in JDK `HttpServer`, no frameworks) |
| Frontend | HTML, CSS, and JavaScript (charts drawn as SVG; the sign-in scene uses three.js r160) |
| Data storage | Plain text files in `data/` |
| Tests | Plain Java test programs (no libraries) |
| Version control | Git and GitHub |

---

## OOP Concepts Applied

| Concept | Where it appears |
|---|---|
| Encapsulation | `ParkingSlot` keeps its occupied state private and changes it only through `occupy()` and `vacate()`. `PasswordHasher` is the only class that touches password hashing. |
| Inheritance and polymorphism | `Car`, `Motorcycle`, and `Truck` extend `Vehicle`. `RegularSlot`, `HandicappedSlot`, and `VIPSlot` extend `ParkingSlot`. `Administrator`, `ParkingAttendant`, and `Driver` extend `User`, each with its own permissions. |
| Abstraction (interfaces) | `PaymentMethod`, `FeeStrategy`, `ParkingRepository`, and `UserRepository` let payments, fee rules, and storage be swapped without changing `ParkingLot`. |
| Strategy pattern | `FeeStrategy` with `HourlyFee` and `DailyCapFee`. |
| Decorator-style wrapping | `PenaltyFee` wraps any `FeeStrategy` and adds a flat penalty. |
| Factory pattern | `VehicleFactory`, `PaymentFactory`, `SlotFactory`, and `UserFactory` build objects from the values sent by the browser. |
| Repository pattern | `FileParkingRepository` and `FileUserRepository` keep file handling out of the business logic. |

---

## Project Structure

```bash
smart-parking-management-system/
├── README.md
├── source_code/
│   └── SmartParking/
│       ├── src/
│       │   ├── api/         # web server, API endpoints, access guard
│       │   ├── app/         # WebMain (start here), sample data
│       │   ├── exception/   # custom exceptions
│       │   ├── fee/         # fee rules (hourly, daily cap, penalty)
│       │   ├── gate/        # entry and exit gate simulation
│       │   ├── model/       # vehicles, slots, tickets, receipts, reservations, factories
│       │   ├── payment/     # cash, card, and online payment
│       │   ├── security/    # access rules, password hashing, sessions
│       │   ├── service/     # parking lot, sign-in, user directory, event log
│       │   ├── storage/     # saving and loading to files
│       │   ├── tests/       # automated tests
│       │   └── user/        # Administrator, ParkingAttendant, Driver, roles, permissions
│       └── web/             # pages, styles, scripts; vendor/ holds three.js and fonts
├── data/                    # created when the system runs (not committed)
├── UML_diagrams/            # activity, class, sequence, and use case diagrams
└── .gitignore
```

---

## Setup / Execution Instructions

**Requirements:** JDK 17 or newer, and VS Code with the Extension Pack for Java (any Java IDE also works).

**Run in VS Code**
1. Open the repository folder in VS Code.
2. Open `source_code/SmartParking/src/app/WebMain.java` and click **Run** above `main`.
3. Write down the usernames and passwords printed in the terminal on the first run (see below).
4. Open http://localhost:8080 in a browser and sign in.
5. Stop the server with the red stop square in the terminal.

**Run from a terminal (PowerShell, in the repository folder)**
```bash
$files = Get-ChildItem -Recurse source_code\SmartParking\src -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $files
java -cp out app.WebMain
```

### First run and accounts

On the first run the system creates three accounts and prints their passwords once in the terminal. The passwords are not saved as text and are not shown again.

| Username | Role |
|---|---|
| `admin` | Administrator |
| `attendant` | Parking Attendant |
| `driver` | Driver (sample plate `SAM 123`) |

Change the passwords on the **My account** page. If you lose them, stop the server, delete `data/users.txt`, and run again to get new ones. New drivers can also use **Create account** on the sign-in page.

### Saved data

The first run starts with sample data (8 slots, 3 parked vehicles, and 30 days of sample payment history). Everything is saved in the `data/` folder and loaded on the next start:

| File | Holds |
|---|---|
| `data/smartpark.txt` | Parked vehicles, payments, ticket numbers, prices, slots, and reservations |
| `data/users.txt` | Accounts (passwords are stored hashed) |

To go back to the sample data, stop the server, delete the files in `data/`, and run again. If a file is damaged, the system keeps a copy (`smartpark.broken.txt` or `users.broken.txt`) and starts fresh.

---

## Running the Tests

The tests are plain Java programs and need no libraries. From the repository folder (PowerShell):

```bash
$files = Get-ChildItem -Recurse source_code\SmartParking\src -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $files
java -cp out tests.ParkingTests
java -cp out tests.SecurityTests
java -cp out tests.UserApiTests
java -cp out tests.DriverApiTests
java -cp out tests.ReservationTests
java -cp out tests.WebAccessTests
```

Each program prints the number of checks passed and failed. At the time of writing, all 714 checks pass.

| Test program | Covers |
|---|---|
| `ParkingTests` | Fees, fee rules, lost tickets, gates, slots, payments, factories, settings, saving and loading |
| `SecurityTests` | Password hashing, sessions, role permissions, lockout |
| `UserApiTests` | Creating, renaming, resetting, and deleting accounts; self-registration |
| `DriverApiTests` | The driver page: vehicles, fees, online payment, plate numbers |
| `ReservationTests` | Reserving slots, expiry, notifications |
| `WebAccessTests` | Which pages need a sign-in, cookie settings, file types |

The browser pages are not covered by automated tests yet.

---

## UML Diagrams

The diagram images are in the `UML_diagrams` folder. They were last updated on October 10 and do not yet show the fee rules, gates, accounts, or reservations.

```
Activity Diagram - https://drive.google.com/file/d/1qCvfWEacayVQOiDpiswt9HcmbSzh8mZ2/view?usp=sharing
Class Diagram - https://drive.google.com/file/d/1xnFcemooXX9RI9cOM4pxUOtNwubIkxb4/view?usp=drive_link
Sequence Diagram - https://drive.google.com/file/d/1nI1CPg_d7qLd5hFDEZWrF4E8xKmXwD2z/view?usp=drive_link
Usecase Diagram - https://drive.google.com/file/d/1fJ5VBUVPP-s7xM8wY9TlLhuA2bSJ6bVm/view?usp=drive_link
```

---

## Third-Party Files

- **three.js r160** (MIT license) in `web/vendor/three/`, used for the 3D sign-in page.
- **Inter** fonts (SIL Open Font License) in `web/vendor/fonts/`.
