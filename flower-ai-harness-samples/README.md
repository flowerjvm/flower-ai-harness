# flower-ai-harness Samples

Run the sample tests from the repository root:

```bash
mvn -B -ntp -pl flower-ai-harness-samples -am test
```

The sample application class is:

```text
io.github.flowerjvm.flower.ai.harness.samples.textreview.TextReviewSampleApplication
```

To run the `main`, install the reactor artifacts once, then execute the
samples module:

```bash
mvn -B -ntp -pl flower-ai-harness-samples -am install -DskipTests
mvn -B -ntp -pl flower-ai-harness-samples \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=io.github.flowerjvm.flower.ai.harness.samples.textreview.TextReviewSampleApplication
```

It builds a `TextReviewInput`, runs the harness against `FakeAiModelGateway`,
and prints the run id, attempt count, and emitted findings.
