# Releasing Flower AI Harness to Maven Central

Release artifacts use the Maven group `io.github.flowerjvm`. Java API packages
use `io.github.flowerjvm.flower.ai.harness.*`.

The reactor resolves Flower from Maven Central at
`io.github.flowerjvm:flower-core:0.1.0`. Harness modules depend on one another
with `${project.version}` because they are released together. The samples
module remains part of CI but is excluded from the Central deployment bundle.

## Version policy

- Every deployable module uses the parent reactor version.
- Do not version a newly added module independently.
- Release versions must be non-SNAPSHOT semantic versions.
- Git tags and GitHub releases use the matching `v`-prefixed value.
- After release, move `main` to the next `-SNAPSHOT` version.

Before selecting a version, review
[`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) and confirm that module
inventory and release notes match the reactor.

## Required repository secrets

| Secret | Value |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal token password |
| `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored private signing key |
| `MAVEN_GPG_PASSPHRASE` | Private-key passphrase |

## Release procedure

1. Ensure `main` is clean and CI is green.
2. Set and commit a non-SNAPSHOT reactor version.
3. Run `mvn -B -ntp -Prelease clean verify`.
4. Confirm sources and Javadocs are generated for every deployable module.
5. Create the matching `v`-prefixed Git tag and GitHub Release.
6. The release workflow signs and publishes the deployable reactor artifacts,
   then waits until Central reports the deployment as published.
7. Resolve at least one published artifact from a clean Maven repository.
8. Move `main` to the next development version, such as `0.1.1-SNAPSHOT`.
9. Update `README.md` and `IMPLEMENTATION_STATUS.md` if their version summary
   changed.

## Local dry run

```bash
mvn -B -ntp -Prelease \
  -Dcentral.skipPublishing=true \
  -Dgpg.skip=true \
  clean deploy
```

The Maven settings used for this command must contain a `central` server entry,
even when upload is skipped.

## Post-release verification

Use a temporary local Maven repository so the check does not accidentally
succeed from a previous local install:

```bash
mvn -B -ntp \
  -Dmaven.repo.local=target/release-resolution-repository \
  dependency:get \
  -Dartifact=io.github.flowerjvm:flower-ai-harness-core:<version>
```

Repeat for every newly introduced artifact.
