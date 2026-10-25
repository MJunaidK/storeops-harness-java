---
name: how-to-review
description: How the Evaluator reviews one StoreOps sprint — run the gates itself, collect the diff, and answer a fixed checklist of PASS/FAIL checks with file:line evidence. Read by the Evaluator; verdict rules are in grading-criteria.
---

# StoreOps — How to Review a Sprint

## Purpose

Make the review repeatable: two reviews of the same code must give the same answers. The Evaluator does not give opinions. It runs the gates, then answers every check below as **PASS** or **FAIL**, each with evidence. `grading-criteria` turns those answers into a score and a verdict.

## Inputs (read only these)

1. `.harness/output/sprint-<N>-contract.md` — the standard to review against (including any AMENDMENT block at its top, which overrides the text below it).
2. `.harness/output/generator-summary.md` — the Generator's claims. Treat every claim as unverified.
3. The diff of the sprint (below) and the files it touches.
4. `.harness/skills/architecture-principles/SKILL.md`, `how-to-test`, `coding-conventions` — as reference for the rules being checked.

## Steps

1. **Run the gates yourself:** `./mvnw verify` exactly as written — no pipes, no `-q`, no `-D` flags. Record the `BUILD …` line, the `Tests run:` totals, the `ArchitectureTest` test count, and the first failing gate if any.
2. **Collect the diff** against the base the orchestrator gives (default `main`), working tree included, without
   the orchestrator's archives: `git diff --merge-base <base> --stat -- . ":(exclude).harness/reviews"`,
   `git diff --merge-base <base> -- . ":(exclude).harness/reviews"` and `git status` (new, untracked files count too).
   Use only read-only git commands.
3. **Answer every check below**, in order. A check that cannot apply (e.g. no controller changed) is PASS with evidence "not applicable: <reason>".
4. Write the results to `evaluator-feedback.md` (format in `evaluator.agent.md`), then apply `grading-criteria`.

## Evidence rule

Every FAIL names **file:line** (or the build output line) and quotes the offending code or text. Every PASS names what was checked (e.g. "AC-1.3 → `ActivityControllerTest#bulkStatusByUnrelatedAssociateIsForbiddenForItem`, asserts `$.results[0].errorCode` and follow-up GET"). A check without evidence is invalid.

## Checklist

Each check is written as its **PASS condition**; anything else is FAIL.

### HG — Hard gates (from step 1)

- **HG-1** `./mvnw verify` ends with `BUILD SUCCESS`.
- **HG-2** `ArchitectureTest` reports `Tests run: 7` (no ArchUnit rule removed).

### CC — Contract compliance

- **CC-1** Every AC of this sprint appears in the summary's AC table with at least one `class#method`.
- **CC-2** Every test named in that table exists in the code under that exact name.
- **CC-3** Endpoint paths, HTTP methods, JSON field names, error codes and class/method names in the diff match the contract (after amendments). A deviation PASSES only if it is listed under the summary's Known gaps **with a reason that cites a rule** (e.g. D7 prefix); otherwise FAIL.

### TQ — Test quality (failure mode #3)

- **TQ-1** Each AC's test(s) assert, in addition to the status code, every body field, resulting state and event that the AC's THEN clause names.
- **TQ-2** Each AC whose THEN says "remains", "unchanged" or "no event" has a test asserting it (`never()` / follow-up GET / captured unchanged state).
- **TQ-3** Where the AC gives an exact value (code, message, status, count), the test asserts that exact value, not only `isNotNull()` / `isNotEmpty()`.
- **TQ-4** No assertion is true by construction (e.g. asserting a field of an immutable record against the value read from the same record, `assertTrue(true)`, comparing a variable to itself).
- **TQ-5** New MockMvc tests create their own data and do not assume an empty repository or a fixed total count.

### AR — Architecture rules not fully covered by ArchUnit

- **AR-1 (R4)** Each new or changed controller handler makes exactly one service call and contains no `if`, loop or business rule.
- **AR-2 (R3)** No `new <Throwable>(…)` other than an `AppError` subclass appears anywhere in the diff's `src/main` lines — any package, including dead code such as `if (false) { throw … }`.
- **AR-3 (R2, D9)** No new call from one module to another module's write method (`create|update|delete|add|remove|save…`), and no import of `AlertService` or `ReportService` outside their own module.
- **AR-4 (R1, R4)** New storage lives in the owning module's `repository` package; repositories contain no HTTP types and call no services.
- **AR-5 (D7)** Every new service method that changes state starts with a write prefix (`create`, `update`, `delete`, `add`, `remove`, `save`).
- **AR-6 (R5)** If the diff touches `reports`: it calls only read methods of other modules' services. Not applicable otherwise.

### SC — Scope

- **SC-1** The diff implements nothing listed under the contract's Scope out.
- **SC-2** No test asserts behaviour that the contract assigns to a later sprint or to Scope out (e.g. asserting 200 for input a later sprint must reject).
- **SC-3** Every changed file is covered by the contract's Expected changes, or listed in the summary with a reason.

### GI — Gate integrity

- **GI-1** The diff does not change `ArchitectureTest.java`, the checkstyle/spotbugs/jacoco/surefire configuration in `pom.xml`, or `config/` files.
- **GI-2** `git diff --merge-base <base> -- src/test` contains no removed (`-`) line in protected test code
  (definition and rule: architecture-principles "Protected tests"), except changes the contract or its
  amendment names explicitly. Evidence: "no `-` lines", or each `-` line with the contract line that allows it.
- **GI-3** No skip mechanism is added anywhere (`skipTests`, `<skip>`, JaCoCo `<excludes>`, a SpotBugs exclude file, `@SuppressWarnings` for SpotBugs) unless the contract allows it.

### SH — Honesty of the generator summary

- **SH-1** The summary's verify result (success/failure, test count) matches the Evaluator's own run in step 1.
- **SH-2** The summary's "Files changed" table lists exactly the files in the diff (none missing, none extra), each with its correct layer.
- **SH-3** Every deviation from the contract visible in the diff is listed under Known gaps.

## Not checkable from the output

How the Generator worked (e.g. editing files through Bash, piping `verify`) is not visible in the diff. Do not guess about it; such issues are found by the developer or the Monitor.
