# Open Source and Commercialization Strategy

## Core Question

If `flower-ai-harness` is open source, will people just take it and leave?

Yes, some will. That is part of the deal.

This document records possible commercialization and open-source directions.
It is not a decision to commercialize now.

The current stage is:

```text
learn harness engineering
validate through ArchDox
revise the abstraction
keep commercialization options open
```

The strategic question is not "how do we stop everyone from using it without
paying?" If the project is truly open source, that cannot be the goal.

The better question is:

```text
Which parts should be free enough to drive adoption,
and which parts create enough operational or domain value that teams pay?
```

## The Self-Build Objection

If the open-source core is useful, capable teams can build their own dashboards,
prompt registries, eval runners, approval flows, and audit logs.

That is true.

Therefore, the commercial plan should not assume that every serious user will
pay for add-ons. Many teams will self-build. The open-source core should be
treated as:

- an adoption engine
- a credibility engine
- a standardization layer
- a lead generator for consulting, support, hosted operations, and domain
  products

The strongest business value is not "users cannot build this." It is:

```text
Users could build this, but they would rather not spend months maintaining it.
```

Paid products must save real operational time, reduce risk, or provide domain
expertise that is hard to recreate. Otherwise users will just use the core and
move on.

For this project, do not rely only on generic dashboard monetization. The
healthier commercial path is:

```text
Open-source core
  -> credibility and adoption

ArchDox / document workflow products
  -> domain-specific business value

Hosted ops / governance / support
  -> convenience and enterprise adoption
```

## Recommended Strategy

Use an open-core strategy:

```text
Open source:
  - core framework
  - provider interfaces
  - fake provider
  - Flower step helpers
  - validation/refine abstractions
  - basic finding model
  - small runnable samples

Commercial or separately licensed:
  - hosted dashboard
  - run/trace UI
  - prompt/version registry
  - evaluation/regression runner
  - team governance and approvals
  - audit/reporting features
  - premium provider adapters if needed
  - domain harness packs
  - enterprise support
```

For `flower-ai-harness`, the open source core should be genuinely useful. If
the free part feels fake, developers will not trust it.

## License Direction

Recommended default for the core:

```text
Apache-2.0
```

Why:

- familiar to Java/Spring teams
- commercially friendly
- clear patent grant
- easy for companies to approve
- good for adoption

Alternative:

```text
MIT
```

MIT is simpler, but Apache-2.0 is usually better for infrastructure-style Java
libraries because of patent language.

Avoid AGPL for the core framework unless there is a very specific reason. It
can scare companies away from embedding the library in business applications.

Avoid calling source-available licenses "open source." If a license restricts
commercial use or competition, it may be a valid business choice, but it is not
the same as OSI-style open source.

## Business Source / Fair Source Option

There is a middle path used by some companies:

```text
source-available now
converts to open source later
restricts direct commercial competition
```

Examples in the market include Business Source License and Functional Source
License style approaches.

This can protect against cloud/service cloning, but it has tradeoffs:

- lower community trust
- harder enterprise legal review
- less natural for developer framework adoption
- more explanation required
- cannot honestly be marketed as classic open source

For this project, the better first move is likely Apache-2.0 open core, not
source-available core.

## What To Protect

Do not try to protect the basic idea.

Protect:

- brand and trademark
- hosted service experience
- governance workflows
- evaluation datasets and benchmark suites
- domain harness packs
- ArchDox-specific integration value
- enterprise support relationship
- documentation quality and examples
- community trust

The framework can be copied. A credible ecosystem, product experience, domain
knowledge, and trusted maintenance cadence are much harder to copy.

## Practical Monetization Paths

### 1. Hosted Harness Ops

A hosted dashboard for:

- run history
- trace visualization
- prompt versions
- model/cost usage
- retry/refine history
- schema validation failures
- human review queues

This is likely the best SaaS-shaped product.

### 2. Enterprise Governance

Paid features for:

- SSO/SAML
- RBAC
- approval workflows
- audit logs
- retention policies
- environment separation
- compliance exports

These are not necessary for hobby users, but serious teams pay for them.

### 3. Domain Harness Packs

Paid or commercial modules:

- document QA harness
- legal review harness
- inspection report harness
- compliance checklist harness
- internal policy review harness

These can remain separate from the open core.

### 4. Consulting To Product

Use ArchDox and early customers to discover repeatable patterns. Productize
only what repeats.

This is often the healthiest path early because the market is still learning
what AI workflow reliability means.

### 5. Support And Integration

Sell:

- Java/Spring integration support
- architecture reviews
- custom provider adapter work
- workflow design help
- production rollout support

This is less scalable than SaaS, but good for early revenue and learning.

## Community Strategy

The open source project should be useful without payment:

- small setup
- clear README
- honest non-goals
- runnable samples
- fake provider tests
- examples that solve real workflow pain
- no fake "community edition" that is intentionally crippled

Community users are not freeloaders by default. They create:

- feedback
- bug reports
- examples
- credibility
- GitHub stars
- search visibility
- enterprise discovery

Some will never pay. That is acceptable if the project has a clear commercial
layer.

## Recommended Public Message

```text
flower-ai-harness is open source because the core pattern should be inspectable,
testable, and embeddable in real Java applications.

Commercial value will come from operating harnesses at scale: traces, prompt
governance, evaluations, auditability, team workflows, and domain-specific
harness packs.
```

## Recommended Initial Decision

For now:

```text
Core framework license: undecided, Apache-2.0 is the leading option if public
Commercial layer: not created yet
ArchDox integration: first validation target, separate from open core
Domain harness packs: possible later
Hosted dashboard: possible later
Trademark/brand: keep controlled if the project becomes public
```

This keeps adoption easy while preserving a credible business path.

## Current Practical Priority

Do not over-optimize for open-source monetization yet.

The practical next step is to build enough of the harness to support an ArchDox
document QA workflow and learn from it. After that, revisit:

- whether the project should be public
- which license makes sense
- whether there is real external demand
- whether any paid layer is worth building
- which parts are generic framework versus ArchDox/domain product
