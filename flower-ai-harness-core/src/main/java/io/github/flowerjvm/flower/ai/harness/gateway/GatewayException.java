package io.github.flowerjvm.flower.ai.harness.gateway;

/**
 * Runtime failure raised by model gateway implementations.
 */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
