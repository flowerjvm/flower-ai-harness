package io.github.flowerjvm.flower.ai.harness.provider.agentcli;

final class BoundedTextTail {

    private final int maxCharacters;
    private final StringBuilder value = new StringBuilder();

    BoundedTextTail(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    synchronized void append(String line) {
        value.append(line).append(System.lineSeparator());
        int excess = value.length() - maxCharacters;
        if (excess > 0) {
            value.delete(0, excess);
        }
    }

    @Override
    public synchronized String toString() {
        return value.toString();
    }
}
