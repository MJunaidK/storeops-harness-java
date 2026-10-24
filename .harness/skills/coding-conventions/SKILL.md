---
name: coding-conventions
description: How StoreOps production code is written — packages, injection, records, naming, errors, controllers, repositories, events and the Checkstyle rules that fail the build. Read by the Generator; used by the Evaluator as reference.
---

# StoreOps — Coding Conventions

## Purpose

New code must look like the existing code and pass the gates. Every rule below is taken from the current source (`ActivityService`, `ProgrammeService`, `ActivityController`, the records in `model`/`dto`, the `InMemory*Repository` classes) or from the gate configuration (`config/checkstyle.xml`, `pom.xml`). Architecture rules (R1–R5) are in `architecture-principles`; test rules are in `how-to-test`.

## Files and packages

- One public type per file, in `com.storeops.<module>.{controller, service, repository, model, dto}`. Cross-module infrastructure lives only in `com.storeops.shared.{error, event, security}`.
- Request/response shapes go in `dto`; domain data in `model`. Never return or accept a `dto` type from a repository.
- Do not add dependencies to `pom.xml` (no Lombok, no MapStruct, no new libraries) unless the sprint contract says so.

## Classes and dependency injection

- Constructor injection only: dependencies are `private final` fields assigned in the single constructor. No `@Autowired` on fields or setters.
- Services depend on repository **interfaces** (`TaskRepository`), never on `InMemory*` classes.
- Stereotypes: `@RestController` + `@RequestMapping("/api/<resource>")`, `@Service`, `@Repository`, `@Component` (shared infrastructure only).

## Records and immutability

- Models and DTOs are Java `record`s. Never add setters or mutable fields.
- A changed copy is produced by a `withX(...)` method that returns a new record (`Task.withChanges`, `Project.withMember`).
- A record holding a collection copies it in its compact constructor: `members = List.copyOf(members);` (also keeps SpotBugs `EI_EXPOSE_REP` quiet).

## Naming

- Service write methods start with `create`, `update`, `delete`, `add`, `remove` or `save`; read methods with `get`, `find`, `list` or `require` (D7, D9; A7 relies on these prefixes).
- `get…`/`require…` return the object or throw `NotFoundError`; `find…` returns `Optional`; `list…` returns a `List` (never null).
- Repositories: interface `<Entity>Repository`, implementation `InMemory<Entity>Repository`. Requests: `Create<X>Request`, `Update<X>Request`; other DTOs end in `Request`, `Response` or `Result`.
- The programmes module's domain classes are `Project*` (see app-context); do not create `Programme*` model classes.

## Errors

- Throw only `AppError` subclasses from `com.storeops.shared.error`: `NotFoundError`, `ValidationError`, `ForbiddenError`, `ConflictError`, `UnauthorizedError` (R3, A4).
- Not found: repositories return `Optional`; the service converts empty to `NotFoundError("<Entity> not found: " + id)`, e.g. `taskRepository.findById(id).orElseThrow(() -> new NotFoundError("Task not found: " + id))`.
- Never catch an exception just to rethrow a raw one; never return `null` to signal an error.

## Services

- All business rules live here: defaults, permission checks (compare `caller.id()` and `caller.role()`), state rules, validation that needs data.
- IDs: `UUID.randomUUID().toString()`. Time: `Instant now = Instant.now();` taken once per operation and reused for every timestamp and event in it (no `Clock` yet — known gap).
- After a successful state change, emit the event via `eventBus.emit(...)`; never call another module's write methods (R2, D9).
- Read-check-save sequences are not atomic (`ConcurrentHashMap` makes single operations safe only); do not claim concurrency safety you have not built.

## Controllers

- One handler = one service call. No `if`, loops or rules in a controller (R4).
- The caller is a method parameter `AuthenticatedUser caller`, filled by `CurrentUserArgumentResolver` from `X-User-Id`. Never read the header yourself.
- Put `@Valid` on every `@RequestBody` whose DTO has constraints. Set non-200 success codes with `@ResponseStatus(HttpStatus.CREATED)` (POST) or `@ResponseStatus(HttpStatus.NO_CONTENT)` (DELETE); do not return `ResponseEntity`.
- Literal paths (e.g. `/bulk-status`) are fine next to `/{id}`; Spring prefers the literal match.

## Repositories

- `@Repository` class backed by a `ConcurrentHashMap<String, Entity>`; only store, fetch, filter and sort. No HTTP types, no services, no rules (R4).
- Return `Optional` for single lookups and an immutable `List` (`.toList()`) for queries; sort explicitly when order matters (e.g. `Comparator.comparing(Task::createdAt)`).

## Comments

- No boilerplate Javadoc. A one-line `/** … */` only where intent is not obvious (e.g. `/** Read-only access to staff data; no other module may mutate users. */`).

## Checkstyle rules that fail the build

`config/checkstyle.xml` sets every rule to `warning`, and `pom.xml` sets `failOnViolation=true` with `violationSeverity=warning`, so **every warning fails `./mvnw verify`** — in `src/main` and `src/test` (`includeTestSourceDirectory=true`). The rules most often broken:

- Line length > 140; tabs; missing newline at end of file.
- `AvoidStarImport`, `UnusedImports`, `RedundantImport`.
- `NeedBraces` (always use braces, even for one-line `if`), `EmptyStatement`, `MissingSwitchDefault`.
- `WhitespaceAround`, `GenericWhitespace`, `MethodParamPad`, `NoWhitespaceBefore`.
- Naming checks (`TypeName`, `MethodName`, `ConstantName`, …), `ModifierOrder`, `RedundantModifier`.
