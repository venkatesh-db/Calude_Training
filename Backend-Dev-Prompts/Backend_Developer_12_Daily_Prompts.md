# 12 Essential AI Prompts for a Backend Developer

Production-focused, copy-paste-ready prompts covering the work a backend developer typically performs during the week.

## How to use

1. Replace every `[PLACEHOLDER]` with your project details.
2. Attach or open the repository when the prompt asks the AI to inspect code.
3. Keep the verification and safety instructions in the prompt.
4. Review all generated changes before merging or deploying.

## Contents

1. [Understand a ticket before coding](#1-understand-a-ticket-before-coding)
2. [Understand an unfamiliar backend codebase](#2-understand-an-unfamiliar-backend-codebase)
3. [Design a production-ready API](#3-design-a-production-ready-api)
4. [Design or review a database change](#4-design-or-review-a-database-change)
5. [Implement a backend feature safely](#5-implement-a-backend-feature-safely)
6. [Generate comprehensive backend tests](#6-generate-comprehensive-backend-tests)
7. [Review a pull request like a senior engineer](#7-review-a-pull-request-like-a-senior-engineer)
8. [Debug a backend failure](#8-debug-a-backend-failure)
9. [Investigate a slow API](#9-investigate-a-slow-api)
10. [Perform a backend security review](#10-perform-a-backend-security-review)
11. [Handle a production incident](#11-handle-a-production-incident)
12. [Prepare a release-readiness report](#12-prepare-a-release-readiness-report)

---

## 1. Understand a ticket before coding

```text
Act as a senior backend engineer. Analyze the following requirement before writing code.

Requirement:
[PASTE TICKET / USER STORY]

Project context:
- Backend stack: [JAVA/SPRING BOOT, GO, PYTHON/FASTAPI, NODE.JS, ETC.]
- Database: [POSTGRESQL/MYSQL/MONGODB]
- Messaging/cache: [KAFKA/REDIS/RABBITMQ/NONE]
- Expected traffic: [REQUESTS OR TPS]
- Repository path: [PATH]

Tasks:
1. Restate the requirement in simple technical language.
2. Identify functional and non-functional requirements.
3. Find the relevant modules, APIs, database tables and dependencies in the repository.
4. Map the current request flow.
5. Identify missing or ambiguous requirements.
6. List edge cases, failure scenarios and security concerns.
7. Define acceptance criteria.
8. Propose the smallest production-safe implementation plan.
9. List the files likely to change.
10. Do not modify code yet.

Do not invent repository behavior. Support conclusions with filenames, functions and existing code references.
```

## 2. Understand an unfamiliar backend codebase

```text
Act as a senior engineer onboarding to this backend repository.

Analyze the repository and explain:

1. Application purpose and major business capabilities.
2. Language, frameworks, build system and runtime.
3. Entry points and application startup flow.
4. Architecture layers and module boundaries.
5. One complete request flow:
   Client → Route → Controller → Service → Repository → Database → Response
6. Authentication and authorization flow.
7. Database access, migrations and transaction handling.
8. Redis, Kafka, queues and external-service integrations.
9. Configuration and secret-management approach.
10. Logging, metrics, tracing and error handling.
11. Test structure and local execution commands.
12. CI/CD and deployment configuration.
13. Five areas with the highest operational risk.

Create a compact repository map with important directories and files. Reference actual code locations and clearly label anything you cannot verify.
```

## 3. Design a production-ready API

```text
Design a production-ready backend API for this requirement:

[PASTE REQUIREMENT]

Environment:
- Stack: [STACK]
- Database: [DATABASE]
- Authentication: [JWT/OAUTH2/API KEY]
- Consumers: [WEB/MOBILE/INTERNAL SERVICES]
- Expected load: [TPS/DAILY REQUESTS]

Provide:

1. Endpoint, HTTP method and purpose.
2. Path, query and header parameters.
3. Request and response JSON examples.
4. Field validation rules.
5. HTTP status codes and standardized error responses.
6. Authentication and authorization rules.
7. Idempotency strategy where relevant.
8. Pagination, filtering and sorting.
9. Database reads/writes and transaction boundary.
10. Concurrency and race-condition handling.
11. Timeout, retry and circuit-breaker expectations.
12. Rate limiting and abuse protection.
13. Logs, metrics and traces.
14. OpenAPI specification.
15. Unit, integration and contract-test scenarios.
16. Backward-compatibility and versioning risks.

Prefer simple REST conventions and explain important design trade-offs.
```

## 4. Design or review a database change

```text
Act as a PostgreSQL performance specialist and backend architect.

Review or design the database change below:

[PASTE SCHEMA / QUERY / REQUIREMENT]

Workload:
- Table size: [ROWS]
- Read/write ratio: [RATIO]
- Peak traffic: [TPS]
- Common filters and sorting: [DETAILS]
- Data-retention requirement: [DETAILS]

Analyze:

1. Tables, columns, data types and constraints.
2. Primary, foreign and unique keys.
3. Required indexes and why each one is needed.
4. Query execution risks and expected access patterns.
5. Transaction boundaries and isolation level.
6. Lost updates, deadlocks and race conditions.
7. N+1 queries or excessive round trips.
8. Safe migration and rollback strategy.
9. Backfill approach for existing records.
10. Zero-downtime deployment compatibility.
11. Archival, partitioning or retention requirements.
12. Monitoring signals for slow queries and lock contention.

Provide the proposed SQL migration, rollback SQL, optimized queries and verification commands. Avoid speculative indexes that the workload does not justify.
```

## 5. Implement a backend feature safely

```text
Implement the following backend feature in the existing repository:

[PASTE REQUIREMENT]

Instructions:

1. Inspect the repository conventions before editing.
2. Reuse existing architecture, utilities and error formats.
3. Keep the change minimal and focused.
4. Implement validation, authorization and error handling.
5. Preserve backward compatibility unless explicitly told otherwise.
6. Handle null values, duplicate requests, timeouts and dependency failures.
7. Use safe transaction boundaries.
8. Never log passwords, tokens, personal data or secrets.
9. Add or update unit and integration tests.
10. Update API documentation and configuration examples when required.
11. Run formatting, linting, compilation and relevant tests.
12. Do not modify unrelated files.

Before editing, briefly state the intended changes. After implementation, report:
- Files changed
- Important design decisions
- Commands executed
- Test results
- Remaining risks or assumptions
```

## 6. Generate comprehensive backend tests

```text
Create production-quality tests for the following backend code or feature:

[PASTE CODE / FILE PATH / REQUIREMENT]

Use the testing conventions already present in the repository.

Cover:

1. Happy path.
2. Request validation failures.
3. Authentication and authorization.
4. Resource not found.
5. Duplicate and idempotent requests.
6. Database errors and transaction rollback.
7. External-service timeouts and failures.
8. Retryable versus non-retryable errors.
9. Boundary values, nulls and empty collections.
10. Concurrent requests and race conditions.
11. Serialization and response-contract validation.
12. Regression scenarios related to the change.

Create:
- Unit tests for business logic
- Repository/database tests
- API integration tests
- Mocks or fakes only at real dependency boundaries

Run the relevant tests and report coverage gaps. Do not change production behavior merely to make a weak test pass.
```

## 7. Review a pull request like a senior engineer

```text
Perform a strict production-readiness review of the current code changes.

Review the diff and surrounding code for:

1. Requirement correctness.
2. Logic bugs and missing edge cases.
3. API compatibility.
4. Authentication, authorization and data exposure.
5. SQL performance, transactions and locking.
6. Concurrency issues and race conditions.
7. Timeout, retry and idempotency behavior.
8. Resource leaks involving connections, files or goroutines/threads.
9. Error handling and error-message quality.
10. Logs, metrics and traces.
11. Test quality and missing regression coverage.
12. Configuration and deployment risks.
13. Maintainability and unnecessary complexity.

Classify findings as:
- Blocker
- High
- Medium
- Low
- Suggestion

For every finding, include:
- File and code location
- Concrete failure scenario
- Why it matters
- Recommended correction

Do not criticize formatting already handled by automated tools. If the change is safe, state that explicitly and mention any residual risks.
```

## 8. Debug a backend failure

```text
Act as a production backend troubleshooting engineer.

Investigate this problem:

Symptoms:
[PASTE ERROR / USER IMPACT]

Logs:
[PASTE LOGS]

Recent changes:
[PASTE DEPLOYMENT OR CODE CHANGES]

Environment:
- Service: [NAME]
- Database: [DATABASE]
- Dependencies: [SERVICES]
- Deployment: [KUBERNETES/VM/SERVERLESS]
- First observed: [TIME]

Follow this process:

1. Build an evidence-based incident timeline.
2. Separate confirmed facts from assumptions.
3. Identify the failing component and request path.
4. Propose the three most likely hypotheses, ranked by probability.
5. Specify the exact log, metric, trace, query or command that will validate each hypothesis.
6. Correlate application, database, infrastructure and dependency signals.
7. Identify the most probable root cause.
8. Recommend the safest immediate mitigation.
9. Propose the permanent code or configuration fix.
10. Define verification and rollback steps.
11. List preventive alerts and tests.

Do not jump directly to a solution based only on one error message.
```

## 9. Investigate a slow API

```text
Analyze and improve the performance of this backend endpoint:

Endpoint: [METHOD AND PATH]
Current traffic: [TPS]
Current latency: P50 [X], P95 [X], P99 [X]
Target latency/SLO: [TARGET]
Error rate: [RATE]

Evidence:
[PASTE CODE, QUERY PLAN, METRICS, TRACE OR LOGS]

Break down latency across:
- Load balancer/network
- Application processing
- Database
- Cache
- External services
- Queueing and thread/connection pools

Check for:
1. Slow or repeated database queries.
2. Missing or ineffective indexes.
3. N+1 calls.
4. Lock contention.
5. Connection-pool exhaustion.
6. Blocking work or thread starvation.
7. Serialization overhead.
8. Cache misses or hot keys.
9. Retry amplification.
10. Unbounded payloads or inefficient pagination.
11. CPU, memory and garbage-collection pressure.

Recommend improvements ranked by impact, risk and implementation effort. Include required code changes, before/after measurements, load-test scenarios and rollback criteria. Do not optimize without measurable evidence.
```

## 10. Perform a backend security review

```text
Perform a security review of this backend feature or code:

[PASTE CODE / API / REQUIREMENT]

Evaluate:

1. Authentication bypass.
2. Missing object-level or function-level authorization.
3. SQL, command and template injection.
4. SSRF and unsafe outbound requests.
5. Insecure deserialization.
6. Mass assignment.
7. Weak input validation.
8. Sensitive data in responses, logs or errors.
9. Password, token and secret handling.
10. Replay attacks and missing idempotency.
11. Rate limiting and brute-force protection.
12. File-upload vulnerabilities.
13. Dependency and configuration risks.
14. Encryption in transit and at rest.
15. Audit logging and privacy requirements.

For every finding provide:
- Severity
- Exploitation scenario
- Affected code
- Recommended remediation
- Security test to prevent regression

Avoid theoretical warnings that do not apply to the actual implementation.
```

## 11. Handle a production incident

```text
Act as the backend engineer on call for this production incident:

Incident:
[PASTE ALERT OR DESCRIPTION]

Current signals:
- Availability: [VALUE]
- P95/P99 latency: [VALUE]
- Error rate: [VALUE]
- Traffic: [VALUE]
- CPU/memory: [VALUE]
- Database health: [VALUE]
- Dependency health: [VALUE]
- Recent deployment: [DETAILS]

Create an incident-response plan containing:

1. User and business impact.
2. Incident severity and justification.
3. First five checks in priority order.
4. Immediate containment options.
5. Decision criteria for rollback, feature disablement or traffic reduction.
6. Commands, dashboards, logs and traces to inspect.
7. Communication update for stakeholders.
8. Recovery verification checklist.
9. Evidence required before declaring resolution.
10. Post-incident root-cause-analysis outline.
11. Corrective actions with owners and priorities.
12. New alerts, runbooks and tests needed.

Prioritize restoring service safely before performing deep optimization.
```

## 12. Prepare a release-readiness report

```text
Evaluate whether the current backend changes are ready for production release.

Release scope:
[PASTE FEATURE / DIFF / VERSION]

Environment:
- Deployment platform: [KUBERNETES/VM/SERVERLESS]
- Database changes: [YES/NO + DETAILS]
- Feature flag: [YES/NO]
- Expected traffic: [DETAILS]

Verify:

1. Requirements and acceptance criteria.
2. Compilation, linting and test results.
3. API and schema backward compatibility.
4. Database migration and rollback safety.
5. Configuration and secret availability.
6. Security and privacy risks.
7. Performance and capacity impact.
8. Logs, metrics, traces and dashboards.
9. Alerts and operational runbooks.
10. Deployment sequencing.
11. Canary or gradual-rollout strategy.
12. Rollback triggers and exact rollback procedure.
13. Post-deployment smoke tests.
14. Ownership during the monitoring window.

Return:
- Release decision: GO, CONDITIONAL GO or NO-GO
- Evidence supporting the decision
- Blocking issues
- Deployment checklist
- First-30-minute monitoring checklist
- Rollback plan
- Remaining risks and owners

Do not mark the release as GO without concrete verification evidence.
```

---

## Recommended weekly usage

| Typical situation | Prompt |
|---|---:|
| New ticket arrives | 1 |
| Working in an unfamiliar service | 2 |
| Designing a new endpoint | 3 |
| Changing a schema or slow query | 4 |
| Implementing the ticket | 5 |
| Adding test coverage | 6 |
| Reviewing a teammate's PR | 7 |
| Fixing a development or test failure | 8 |
| Investigating latency | 9 |
| Reviewing security risks | 10 |
| Responding to a production alert | 11 |
| Releasing to production | 12 |

## Universal instruction to add when needed

```text
Work from evidence. Do not invent files, APIs, dependencies, test results or system behavior. Ask a focused question only when missing information materially changes the implementation. Preserve existing conventions, avoid unrelated modifications, verify all changes with the repository's actual commands and clearly report anything you could not validate.
```
