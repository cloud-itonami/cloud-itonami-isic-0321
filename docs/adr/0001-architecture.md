# ADR-0001: Marine Aquaculture Operations Coordination Actor Architecture

## Status
Accepted

## Context

Marine aquaculture facilities require coordination of operations -- stock monitoring, maintenance scheduling, health concern escalation, and supply ordering. These are primarily back-office coordination tasks. However, critical decisions (treatment administration, harvest decisions, mortality culling) must remain with human experts (veterinarians, facility managers).

The governance model must separate:
- **Coordination proposals** (advisory/proposal-stage) -- which the LLM can generate
- **Human-exclusive authority** (treatment, harvest, culling decisions) -- which are permanently blocked
- **Escalation points** (health concerns, high-cost supplies) -- which require human review

## Decision

Implement a marine aquaculture operations actor using the cloud-itonami pattern:

1. **Governor**: Independent compliance layer that enforces hard rules
   - Facility must be verified and active before any action
   - All effects are `:propose` only (coordination, not direct execution)
   - Certain operations (treatment, harvest) are permanently blocked
   - Health concerns and high-cost supplies always escalate

2. **Store Protocol**: Abstracted data layer
   - In-memory implementation for development/testing
   - Future swap path to Datomic/kotoba-server
   - Append-only audit ledger for all operations

3. **LLM Advisor**: Proposal generator
   - Mock implementation for testing
   - Real LLM injection point via protocol
   - No external service calls in the base module

4. **State Machine**: langgraph-clj graph
   - Linear flow: request → advise → govern → decide → commit|hold|escalate
   - Checkpointed operation (one graph run = one auditable operation)
   - No unbounded loops

## Operations

**Allowed proposals (all :effect :propose):**
- `:log-stock-record` -- count, growth, mortality data
- `:schedule-facility-maintenance` -- equipment and pen maintenance
- `:flag-stock-health-concern` -- disease/water-quality concerns (always escalates)
- `:order-supplies` -- feed, veterinary supply procurement

**Blocked permanently:**
- `:administer-treatment` -- veterinarian exclusive
- `:decide-harvest` -- facility manager exclusive
- `:decide-cull` -- facility manager exclusive

**Hard Governor Rules (no human override):**
1. Facility verification: `:site-id` must exist and have `:status :active`
2. Effect integrity: proposal `:effect` must match `:op`
3. Coordination-only: all effects must be `:propose`
4. Forbidden fields: `:site-id`, `:facility-license`, `:facility-status` never modifiable

**Escalation Triggers (human review required):**
- Health concern flags (always)
- Supply orders >= 10,000 units (default threshold)
- Advisor confidence < 0.6

## Consequences

- **Coordination safety**: LLM cannot propose direct treatment or harvest decisions
- **Audit transparency**: All operations are logged with proposer, governor verdict, and human decisions
- **Facility authority preservation**: Facility managers and veterinarians retain exclusive authority
- **Extensibility**: Store protocol allows future swap to persistent database
- **Testability**: Pure governor checks and store protocol enable comprehensive contract tests

## Testing

- Governor contract tests: all hard rules, soft escalations
- Store contract tests: facility lookup, audit trail, operation logging
- Integration test harness (`aquaculture.sim`) demonstrates the full actor flow
