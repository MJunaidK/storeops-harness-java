Generate the StoreOps REST API project using Java 21, Spring Boot 3.x, Maven (with Maven wrapper), root package com.storeops.
Requirements:
- Domain: retail store operations management
- 5 modules: activities (operational activities), programmes (store programmes),
  staff (store staff), alerts (ops alerts), reports (store/region metrics)
- Each module: 3 layers (Routes → Service → Repository)
- Module boundary rules: no circular imports; notifications via
  event bus only; users is read-only for other modules
- Typed error hierarchy (AppError base class with code,
  message, statusCode) — no raw Error throws in services/routes
- 9 REST endpoints as specified in Section 3.6
- Test setup: JUnit5+MockMvc
- Linting/type checking configured and passing

Generate stub implementations (not full business logic) —
enough structure to compile, run tests, and pass lint.
Use in-memory storage (no database required).

## SPECIFICATION APPENDIX (the "Section 3.x" references above are not in this repo — use these details)

### Layout
- Per module: com.storeops.<module>.{controller, service, repository, model, dto}
- Shared: com.storeops.shared.{error, event, security}
- "Routes" layer = @RestController classes. Repository = interface + InMemory implementation (ConcurrentHashMap).
- "users" in the rules above = the staff module.

### Entities and enums
- activities: Task (id, programmeId, title, description, status, priority, category, assigneeId, ownerId, dueDate, createdAt, updatedAt)
  TaskStatus TODO|IN_PROGRESS|DONE|BLOCKED; TaskPriority LOW|MEDIUM|HIGH|CRITICAL;
  TaskCategory RESTOCKING|PLANOGRAM|AUDIT|COMPLIANCE|GENERAL
- programmes: Project (incl. storeId), ProjectMember, ProjectRole STORE_MANAGER|DEPARTMENT_LEAD|ASSOCIATE
- staff: User, UserProfile, StaffRole REGIONAL_MANAGER|STORE_MANAGER|DEPARTMENT_LEAD|ASSOCIATE, AuthToken.
  Auth-only: NO REST endpoints. Other modules may call only read methods of StaffService.
- alerts: Notification, NotificationChannel IN_APP|EMAIL, NotificationStatus UNREAD|READ,
  AlertType INVENTORY|SLA_BREACH|SHIFT_HANDOVER|ESCALATION
- reports: Report, ReportType STORE_SUMMARY|REGIONAL_ROLLUP|DEPARTMENT_PERFORMANCE,
  ReportStatus PENDING|READY|FAILED. Model + service skeleton only, NO endpoints yet.
  Read-only: never writes to activities, programmes or staff.

### The 9 endpoints
GET    /api/activities?programmeId=&status=
POST   /api/activities
GET    /api/activities/{id}
PATCH  /api/activities/{id}          (status, priority, category, assigneeId)
DELETE /api/activities/{id}          (only the task owner or a STORE_MANAGER, else 403)
GET    /api/programmes               (programmes for the authenticated user's store)
POST   /api/programmes
POST   /api/programmes/{id}/members
GET    /api/alerts                   (alerts for the authenticated user)
Auth stub: header X-User-Id resolved via StaffService against seeded in-memory users
(seed at least one STORE_MANAGER, one DEPARTMENT_LEAD, one ASSOCIATE). Missing/unknown → 401.

### Error contract
- abstract AppError extends RuntimeException with code (String), message, statusCode (int)
- Subclasses: NotFoundError 404, ValidationError 400, UnauthorizedError 401, ForbiddenError 403, ConflictError 409
- @RestControllerAdvice maps AppError to JSON {"code": ..., "message": ...}; Bean Validation on request DTOs maps to ValidationError format.

### Event bus
- shared.event.EventBus wrapping Spring ApplicationEventPublisher, method emit(DomainEvent)
- ActivityService emits TaskCreatedEvent / TaskStatusChangedEvent; alerts module handles them with @EventListener and creates Notifications.
- activities and programmes must NOT import anything from alerts or reports.

### Automated checks — all must run as part of ./mvnw verify and fail the build on violation
- ArchUnit tests:
  1. no class outside module X accesses com.storeops.X.repository
  2. controllers never access repositories directly
  3. no cyclic dependencies between modules
  4. classes in controller and service packages never construct RuntimeException, IllegalArgumentException, IllegalStateException or Exception — only AppError subclasses
  5. activities and programmes do not depend on alerts or reports
  6. reports depends on other modules only through their service packages
- Checkstyle (pragmatic config, failOnViolation=true) and SpotBugs (fail on high priority)
- JaCoCo check rules: service packages ≥ 80% line, controller packages ≥ 70%, shared ≥ 60%, overall ≥ 70%
- Tests: Mockito unit tests per service + MockMvc tests per endpoint that assert response body content and business rules (e.g. DELETE by non-owner associate → 403), not just status codes.

### Runtime
- Port 8080. Include a multi-stage Dockerfile, docker-compose.yml, and README with run instructions.
- When finished, run ./mvnw verify and fix issues until it passes. Then start the app and confirm
  GET /api/activities with a seeded X-User-Id header returns 200.
