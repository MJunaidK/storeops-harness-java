# StoreOps — Module Architecture

## Baseline (2026-10-21, commit bb2abb2)

```mermaid
flowchart TB
    activities["activities<br/>Store tasks"]
    programmes["programmes<br/>Store initiatives"]
    reports["reports<br/>Read-only aggregation"]
    staff["staff<br/>Read-only directory"]
    bus(["shared.event<br/>EventBus"])
    alerts["alerts<br/>Notification inbox"]

    activities -->|"requireById"| staff
    programmes -->|"requireById"| staff
    reports -->|"list"| activities
    reports -->|"listForStore"| programmes
    reports -->|"listByStore"| staff
    activities -.->|"TaskCreatedEvent<br/>TaskStatusChangedEvent"| bus
    bus -.->|"@EventListener"| alerts
    activities -. "programmeId (ID only, not validated)" .- programmes
```

- Solid arrow = read-only service call. Dashed arrow = event.
- Every HTTP request also calls `StaffService` via the `X-User-Id` resolver (shared.security).
- Forbidden: cross-module repository access (ArchUnit 1), activities/programmes → alerts/reports (ArchUnit 5), cycles (ArchUnit 3), reports beyond service/model (ArchUnit 6), reports writing via services (LLM review only).
