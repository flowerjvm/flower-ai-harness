# Releasing Flower AI Harness to Maven Central

Release artifacts use the Maven group `io.github.flowerjvm`. Java API packages
use `io.github.flowerjvm.flower.ai.harness.*`.

The reactor resolves Flower from Maven Central at
`io.github.flowerjvm:flower-core:0.1.0`. Harness modules depend on one another
with `${project.version}` because they are released together. The samples
module remains part of CI but is excluded from the Central deployment bundle.

## Required repository secrets

| Secret | Value |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal token password |
| `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored private signing key |
| `MAVEN_GPG_PASSPHRASE` | Private-key passphrase |

## Release procedure

1. Set and commit a non-SNAPSHOT reactor version.
2. Run `mvn -B -ntp -Prelease clean verify`.
3. Create the matching `v`-prefixed Git tag and GitHub Release.
4. The release workflow signs and publishes the deployable reactor artifacts,
   then waits until Central reports the deployment as published.
5. Move `main` to the next development version, such as `0.1.1-SNAPSHOT`.

## Local dry run

```bash
mvn -B -ntp -Prelease \
  -Dcentral.skipPublishing=true \
  -Dgpg.skip=true \
  clean deploy
```

The Maven settings used for this command must contain a `central` server entry,
even when upload is skipped.
