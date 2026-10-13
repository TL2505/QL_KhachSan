# Phase 1 Front Desk Simulation

This is a reproducible manual procedure for the two-hour Dev C acceptance run. Use a disposable Phase 1 database, a staff account with front-desk permissions, and a room/booking fixture set that can be discarded afterward. Record the build revision, database revision, operator, and start/end timestamps with the run.

## Setup

1. Start MariaDB and the PMS backend; sign in through the desktop client.
2. Seed 30 confirmed arrivals, 20 checked-in departures, three available destination rooms for room moves, and at least 40 checked-in guest folios for incidental charges.
3. Launch the client at 1280×720. On a dual-monitor workstation, confirm the detached map opens on the secondary display.
4. Start a screen recording or observer timer. Keep a run sheet with one row per action and its result.

## Two-hour workload

1. Complete 30 check-ins through the F2 flow. Use only keyboard navigation after opening each flow; record elapsed time from opening to confirmation and verify the room changes to occupied.
2. Complete 20 check-outs through F3. Settle any outstanding master and guest balances, confirm the UUIDv4 checkout request is accepted once, and verify each room becomes vacant-dirty.
3. Perform three room moves. Confirm each source and destination room has the expected status and booking association after refresh.
4. Add 40 service charges. Confirm each charge is routed to the guest folio and the guest balance reflects the charge.
5. Exercise F4 filtering with a room number, guest name, and booking ID; then clear the field and verify all 200 map cells return.
6. Keep the client open for two hours with routine tab switches, scrolling, refreshes, and modal use. Capture any `EdtViolationDetector` warnings and their stack traces.

## Acceptance evidence

- Every seeded action has one successful result; no duplicate booking, payment, or service charge appears in the database.
- Each check-in reaches completion in under 15 seconds using the keyboard-only flow.
- Check-outs produce the correct master/guest folio settlement and `vacant_dirty` room state.
- Room moves and F4 filtering restore correctly after refresh or clearing the search.
- Save the run sheet, recording, application log, before/after booking and folio snapshots, and any failures with the release evidence.

This document defines the procedure; it does not claim that the two-hour run has been executed.
