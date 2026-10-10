# Smart Parking Management System

A console-based Object-Oriented Parking Management System that automates vehicle entry, slot assignment, time tracking, fee calculation, payment processing, and exit — replacing manual parking operations with a clean, class-driven architecture.

---

## Project Description

The Smart Parking Management System is a capstone project developed for the Object-Oriented Programming course. It simulates the complete lifecycle of a driver entering a parking facility, being assigned a parking slot, having their parking duration tracked, paying the computed fee, and exiting the premises.

The system is built entirely around Object-Oriented design principles — encapsulation, inheritance, polymorphism, and abstraction — and is modeled using UML diagrams (Use Case, Class, Sequence, and Activity) that stay fully consistent with the implementation.

### The system supports three primary actors:

| Actor | Role |
|---|---|
| Driver | Enters, parks, pays, and exits |
| Parking Attendant | Handles cash payments, monitors the lot, and generates reports |
| Gate & Sensor System | Automated hardware/software that detects vehicles and controls the barrier gate |

---

## Objectives

1. Apply OOP principles — encapsulation, inheritance, polymorphism, and abstraction — in a real-world scenario.
2. Design a complete UML model (Use Case, Class, Sequence, Activity) that accurately reflects the implemented system.
3. Automate parking operations: slot assignment, duration tracking, fee computation, and payment processing.
4. Ensure data integrity: prevent double-booking of slots, enforce payment before gate opening, and maintain accurate ticket states.
5. Provide a modular, extensible architecture that can later support real hardware (barrier gates, sensors) and online payments.
6. Deliver a maintainable codebase with clear separation of concerns between entities, services, and controllers.

---

## Key Features

### Core Features
- Vehicle Entry — detects vehicle type and registers arrival
- Automatic Slot Assignment — finds and reserves the first available slot matching the vehicle type
- Ticket Generation — records entry time and issues a unique ticket
- Duration Tracking — automatically computes elapsed parking time
- Dynamic Fee Calculation — applies hourly rates per slot type (Regular / Handicapped / VIP)
- Payment Processing — supports cash and card/online methods
- Gate Control — opens only after successful payment
- Receipt Generation — prints/saves transaction receipt
- Slot Release — frees the slot after exit

### Secondary Features
- Parking Lot Monitoring — attendant can view real-time slot occupancy
- Report Generation — daily/weekly reports of entries, exits, and revenue
- Payment Validation — gate stays closed on failed payment; user can retry

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Python (Django) |
| Frontend (planned) | React + TypeScript + Tailwind CSS |
| Version Control | Git + GitHub |

---

