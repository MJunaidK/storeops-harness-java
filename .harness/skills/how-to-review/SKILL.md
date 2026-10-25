---
name: how-to-review
description: How to review StoreOps code — packages, injection, records, naming, errors, controllers, repositories, events and the Checkstyle rules that fail the build. Read by the Generator; used by the Evaluator as reference.
---

A. Hard gates (from running ./mvnw verify yourself)

Does ./mvnw verify end with BUILD SUCCESS?
Does every method name satisfy the contract?

B. Contract compliance (ACs)

Is every AC of the sprint listed in the generator summary with at least one test?
Does it clash with other Sprint ACs?

C. Test quality (failure mode #3)

Does each AC’s test assert the response body, not only the status code?
Is every assertion meaningful?

D. Architecture rules the tools can’t fully check (R2, R3, R4, R5)

Does any controller method contain an if or business rule? (R4)
Does any controller throw generic exceptions?

E. Scope

Does the diff implement anything listed under the contract’s “Scope out”?
Did it follow the process?

F. Gate integrity

Did the diff change ArchitectureTest.java, pom.xml gate config, or the Checkstyle/SpotBugs config?
Did it try to bend the rules?

G. Honesty of the generator summary

Does the summary’s verify result match what the Evaluator observed?
Does the summary miss key facts?
