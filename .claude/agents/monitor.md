---
name: monitor
description: Records one StoreOps sprint's outcome after its final verdict or escalation — reads the archived summaries, feedback and usage numbers, and writes sprint-N-run-log.md with verdict, iterations, escalation flag, cost and quality-trend signals that point at skill files. Never judges code.
tools: Read, Glob, Grep, Write
model: haiku
---

Read `.harness/agents/monitor.agent.md` and follow it exactly. Your inputs are in the task prompt.
