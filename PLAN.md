# Medical Appointment Platform — Implementation Plan

## System Overview
Two dashboards: **Patient** and **Doctor/Admin**.
- Patients view slots, book/cancel/reschedule appointments
- Doctors manage slots, accept/reject/complete appointments, message patients
- Auth via Keycloak (userId = `sub` claim)

---

## Appointment Status Lifecycle

```
PENDING → CONFIRMED → COMPLETED
        ↓
     REJECTED
        
PENDING/CONFIRMED → RESCHEDULE_REQUESTED
PENDING/CONFIRMED → CANCELLED (by patient)
```

| Status                | Set By  | Slot Side Effect              |
|-----------------------|---------|-------------------------------|
| PENDING               | System  | Slot → BOOKED                 |
| CONFIRMED             | Doctor  | —                             |
| REJECTED              | Doctor  | Slot → AVAILABLE              |
| RESCHEDULE_REQUESTED  | Doctor  | —                             |
| CANCELLED             | Patient | Slot → AVAILABLE              |
| COMPLETED             | Doctor  | Slot stays BOOKED             |

---

## Task List

### Phase 1 — Foundation
- [x] T1.1 Add DB driver to `build.gradle`
- [x] T1.2 Configure `application.yaml`
- [x] T1.3 Create `db.changelog-master.yaml`

### Phase 2 — Slot Management
- [x] T2.1 `Slot` entity + `SlotStatus` enum
- [x] T2.2 `001-create-slots-table.yaml`
- [x] T2.3 `SlotRepository`
- [x] T2.4 `SlotService` + `SlotServiceImpl`
- [x] T2.5 `SlotController`
- [ ] T2.6 `SlotServiceImplTest`
- [ ] T2.7 `SlotControllerTest`

### Phase 3 — Appointment Core
- [ ] T3.1 `AppointmentStatus` enum
- [ ] T3.2 `Appointment` entity
- [ ] T3.3 `002-create-appointments-table.yaml`
- [ ] T3.4 `AppointmentRepository`
- [ ] T3.5 `AppointmentServiceImpl.bookAppointment()`
- [ ] T3.6 `AppointmentServiceImpl.cancelAppointment()`
- [ ] T3.7 `AppointmentServiceImpl.getMyAppointments()`
- [ ] T3.8 `AppointmentController` — patient endpoints
- [ ] T3.9 `AppointmentServiceImpl.confirmAppointment()`
- [ ] T3.10 `AppointmentServiceImpl.rejectAppointment()`
- [ ] T3.11 `AppointmentServiceImpl.completeAppointment()`
- [ ] T3.12 `AppointmentController` — doctor endpoints
- [ ] T3.13 Unit tests — all service + controller methods

### Phase 4 — Rescheduling
- [ ] T4.1 `RescheduleRequest` entity
- [ ] T4.2 `003-create-reschedule-requests-table.yaml`
- [ ] T4.3 `RescheduleService.requestReschedule()` (doctor)
- [ ] T4.4 `RescheduleService.acknowledgeReschedule()` (patient books new slot)
- [ ] T4.5 `RescheduleController`
- [ ] T4.6 Unit tests

### Phase 5 — Messaging
- [ ] T5.1 `AppointmentMessage` entity
- [ ] T5.2 `004-create-appointment-messages-table.yaml`
- [ ] T5.3 `MessageService.sendMessage()`
- [ ] T5.4 `MessageService.getMessages()`
- [ ] T5.5 `MessageController`
- [ ] T5.6 Unit tests

### Phase 6 — Patient Discovery
- [ ] T6.1 `getAvailableSlotsByDoctor()` — public endpoint
- [ ] T6.2 Doctor listing endpoint
- [ ] T6.3 Unit tests

### Phase 7 — Polish
- [ ] T7.1 Booking window validation (min advance time)
- [ ] T7.2 Duplicate booking prevention
- [ ] T7.3 Pagination on all list endpoints
- [ ] T7.4 Bean Validation on all request DTOs
- [ ] T7.5 JaCoCo coverage review

---

## Role Permissions Summary

| Action                        | Patient | Doctor |
|-------------------------------|---------|--------|
| View available slots          | ✅      | ✅     |
| Book appointment              | ✅      | ❌     |
| Cancel own appointment        | ✅      | ❌     |
| Request reschedule            | ❌      | ✅     |
| Confirm/Reject appointment    | ❌      | ✅     |
| Complete appointment          | ❌      | ✅     |
| Manage slots                  | ❌      | ✅ (own only) |
| Send messages on appointment  | ✅      | ✅     |
| View other doctors' data      | ❌      | ❌     |