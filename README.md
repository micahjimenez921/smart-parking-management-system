# Smart Parking Management System

A web-based Object-Oriented Parking Management System written in Java that automates vehicle entry, slot assignment, time tracking, fee calculation, payment processing, and exit — replacing manual parking operations with a clean, class-driven architecture.

---

## Project Description

The Smart Parking Management System is a capstone project developed for the Object-Oriented Programming course. It simulates the complete lifecycle of a driver entering a parking facility, being assigned a parking slot, having their parking duration tracked, paying the computed fee, and exiting the premises.

The system is built entirely around Object-Oriented design principles — encapsulation, inheritance, polymorphism, and abstraction — and is modeled using UML diagrams (Use Case, Class, Sequence, and Activity).

### The system supports two primary actors:

| Actor | Role |
|---|---|
| Driver | Enters, parks, pays, and exits |
| Parking Attendant | Operates the dashboard: registers vehicles, handles payments, monitors the lot, and views reports |

---

## Objectives

1. Apply OOP principles — encapsulation, inheritance, polymorphism, and abstraction — in a real-world scenario (for example: private slot state, the Vehicle and ParkingSlot class hierarchies, the PaymentMethod interface, and per-slot hourly rates).
2. Design a complete UML model (Use Case, Class, Sequence, Activity) that accurately reflects the implemented system.
3. Automate parking operations: slot assignment, duration tracking, fee computation, and payment processing.
4. Ensure data integrity: prevent double-booking of slots and duplicate plate numbers, enforce payment before a slot is released, and maintain accurate ticket states.
5. Provide a modular, extensible architecture that can later support real hardware (barrier gates, sensors), real payment gateways, and a database.
6. Deliver a maintainable codebase with clear separation of concerns between entities, services, storage, and the web API.

---

## Key Features

### Core Features
- Vehicle Entry — registers the vehicle type (car, motorcycle, or truck), plate number, and owner on arrival
- Automatic Slot Assignment — finds and reserves the first available slot of the chosen type (Regular / Handicapped / VIP)
- Ticket Generation — records entry time and issues a unique ticket
- Duration Tracking — automatically computes elapsed parking time, rounded up to whole hours
- Dynamic Fee Calculation — applies hourly rates per slot type (Regular / Handicapped / VIP)
- Payment Processing — supports cash (with change), card, and online methods
- Exit Control — closes the ticket and releases the slot only after successful payment
- Receipt Generation — shows and saves a transaction receipt
- Slot Release — frees the slot after exit

### Secondary Features
- Parking Lot Monitoring — attendant can view real-time slot occupancy on a color-coded parking map
- Report Generation — revenue per day (7 or 30 days), parking duration, and payment method charts
- Payment Validation — a failed payment keeps the ticket open and the slot occupied; user can retry
- Data Persistence — parked vehicles, payments, and ticket numbering are saved to a file and restored on startup
- Light and Dark Mode — switchable from the top bar of the dashboard

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 17+ (built-in JDK HttpServer) |
| Frontend | HTML + CSS + JavaScript (charts drawn as SVG) |
| Data Storage | Plain text file (data/smartpark.txt) |
| Version Control | Git + GitHub |

---
## Project Structure
```bash
smart-parking-management-system/
├── README.md
├── source_code/
│   └── SmartParking/
│       ├── src/
│       │   ├── api/
│       │   ├── app/
│       │   ├── exception/
│       │   ├── model/
│       │   ├── payment/
│       │   ├── service/
│       │   └── storage/
│       └── web/
├── data/            # created when the system runs (not committed)
├── docs/
├── UML_diagrams/
│   ├── Smart Parking - Activity Diagram.drawio
│   ├── Smart Parking - Class Diagram.drawio
│   ├── Smart Parking - Sequence Diagram.drawio
│   ├── Smart Parking - Usecase Diagram.drawio
└── .gitignore
```
## Setup/Execution Instructions

**Requirements:** JDK 17 or newer, and VS Code with the Extension Pack for Java (any Java IDE also works).

**Run in VS Code**
1. Open the repository folder in VS Code.
2. Open `source_code/SmartParking/src/app/WebMain.java` and click **Run** above `main`.
3. Open http://localhost:8080 in a browser.
4. Stop the server with the red stop square in the terminal.

**Run from a terminal (PowerShell, in the repository folder)**
```bash
$files = Get-ChildItem -Recurse source_code\SmartParking\src -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $files
java -cp out app.WebMain
```

**Saved data**

The first run starts with sample data (3 parked vehicles and 30 days of sample payment history) and saves it to `data/smartpark.txt`. After that, every parked vehicle and every payment is saved, and the file is loaded on the next start. To go back to the sample data, stop the server, delete `data/smartpark.txt`, and run again.

## UML Diagrams
```
Activity Diagram - https://drive.google.com/file/d/1qCvfWEacayVQOiDpiswt9HcmbSzh8mZ2/view?usp=sharing
Class Diagram - https://drive.google.com/file/d/1xnFcemooXX9RI9cOM4pxUOtNwubIkxb4/view?usp=drive_link
Sequence Diagram - https://drive.google.com/file/d/1nI1CPg_d7qLd5hFDEZWrF4E8xKmXwD2z/view?usp=drive_link
Usecase Diagram - https://drive.google.com/file/d/1fJ5VBUVPP-s7xM8wY9TlLhuA2bSJ6bVm/view?usp=drive_link
```