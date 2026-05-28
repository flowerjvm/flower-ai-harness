package io.github.parkkevinsb.flower.ai.harness.test.fake;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;

import java.util.Arrays;
import java.util.Objects;

/**
 * Predicate used to choose a fake response program for a request.
 */
@FunctionalInterface
public interface RequestMatcher {

    boolean matches(AiModelRequest request);

    static RequestMatcher any() {
        return request -> true;
    }

    static RequestMatcher modelEquals(ModelId id) {
        Objects.requireNonNull(id, "id must not be null");
        return request -> id.equals(request.modelId());
    }

    static RequestMatcher promptContains(String fragment) {
        Objects.requireNonNull(fragment, "fragment must not be null");
        return request -> request.prompt().messages().stream()
                .anyMatch(message -> message.content().contains(fragment));
    }

    static RequestMatcher and(RequestMatcher... matchers) {
        Objects.requireNonNull(matchers, "matchers must not be null");
        RequestMatcher[] copy = Arrays.copyOf(matchers, matchers.length);
        for (RequestMatcher matcher : copy) {
            Objects.requireNonNull(matcher, "matcher must not be null");
        }
        return request -> {
            for (RequestMatcher matcher : copy) {
                if (!matcher.matches(request)) {
                    return false;
                }
            }
            return true;
        };
    }
}
