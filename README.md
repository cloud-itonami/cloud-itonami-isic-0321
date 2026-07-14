# cloud-itonami-isic-0321

Open Business Blueprint for **ISIC Rev.5 0321: Marine aquaculture** — a marine facility operations coordination actor.

## Overview

This actor supports a marine aquaculture facility's back-office coordination workflow:

- **Stock record logging** — count, growth, mortality data
- **Facility maintenance scheduling** — propose equipment and pen maintenance
- **Health concern flagging** — surface disease or water-quality issues (always escalates to human)
- **Supply ordering** — feed and veterinary supply procurement proposals

## Governance Model

All proposals carry `:effect :propose` — this actor is **coordination only**. It never:
- Administers treatments (veterinarian exclusive)
- Makes harvest or culling decisions (facility manager exclusive)
- Modifies facility identity or license records

Hard blocks (no human override):
1. **Facility verification** — site must be registered and active
2. **Effect integrity** — proposal effect must match the request operation
3. **Coordination-only effects** — all effects are `:propose`
4. **Forbidden fields** — site-id, facility-license, facility-status cannot be touched
5. **Blocked operations** — treatment/harvest decisions rejected outright

Escalation triggers (human review required):
- **Health concerns** always escalate
- **High-cost supply orders** (>= 10,000 units) escalate
- **Low advisor confidence** (< 0.6) escalates

## Architecture

```
Request → Advisor (LLM) → Proposal
            ↓
         Governor (independent censor)
            ↓
         Decide (hold | escalate | commit)
            ↓
         Store (persistent audit ledger)
```

All modules are portable `.cljc` (ClojureScript-first, compatible with JVM/nbb).

## Building & Testing

```bash
# Install (workspace already has langgraph-clj at ../../../kotoba-lang/langgraph)
clj -M:test

# Run demo
clj -M:dev:run

# Lint
clj -M:lint
```

## Files

- `blueprint.edn` — open business blueprint metadata
- `src/aquaculture/` — core actor modules
  - `governor.cljc` — compliance rules and hard blocks
  - `store.cljc` — persistent store protocol + in-memory impl
  - `llm_advisor.cljc` — advisor interface (mock or real LLM)
  - `operation.cljc` — langgraph-clj state machine
  - `sim.cljc` — demo simulation
- `test/aquaculture/` — contract tests for governance and store

## License

AGPL-3.0-or-later

## Contributing

This actor is part of the cloud-itonami open business blueprint fleet. See CONTRIBUTING.md.
