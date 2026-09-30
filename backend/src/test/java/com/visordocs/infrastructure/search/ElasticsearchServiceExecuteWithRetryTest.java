package com.visordocs.infrastructure.search;

import com.visordocs.domain.AppException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ElasticsearchServiceExecuteWithRetryTest {

    @Test
    void executeWithRetry_returnsValueOnFirstAttempt() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 3);
        setField(real, "retryDelayMs", 1);

        AtomicInteger calls = new AtomicInteger();
        Supplier<String> op = () -> {
            calls.incrementAndGet();
            return "success";
        };

        String result = invokeExecuteWithRetry(real, op);
        assertThat(result).isEqualTo("success");
        assertThat(calls).hasValue(1);
    }

    @Test
    void executeWithRetry_retriesOnRuntimeException_thenSuccess() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 3);
        setField(real, "retryDelayMs", 1);

        AtomicInteger calls = new AtomicInteger();
        Supplier<String> op = () -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("fail");
            }
            return "success";
        };

        String result = invokeExecuteWithRetry(real, op);
        assertThat(result).isEqualTo("success");
        assertThat(calls).hasValue(3);
    }

    @Test
    void executeWithRetry_doesNotRetryOnAppExceptionClientError() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 3);
        setField(real, "retryDelayMs", 1);

        AtomicInteger calls = new AtomicInteger();
        Supplier<String> op = () -> {
            calls.incrementAndGet();
            throw AppException.validationError("client error");
        };

        Throwable thrown = catchThrowable(() -> invokeExecuteWithRetry(real, op));
        assertThat(thrown).isInstanceOf(AppException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
        assertThat(calls).hasValue(1);
    }

    @Test
    void executeWithRetry_zeroAttempts_throwsSearchErrorWithoutRunningOperation() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 0);

        AtomicInteger calls = new AtomicInteger();
        Supplier<String> op = () -> {
            calls.incrementAndGet();
            return "unexpected";
        };

        Throwable thrown = catchThrowable(() -> invokeExecuteWithRetry(real, op));

        assertThat(thrown).isInstanceOf(AppException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("code", "SEARCH_ERROR");
        assertThat(thrown).hasMessageContaining("maxRetryAttempts is not positive");
        assertThat(calls).hasValue(0);
    }

    private void setField(Object target, String name, Object value) throws Throwable {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private <T> T invokeExecuteWithRetry(ElasticsearchService service, Supplier<T> op) throws Throwable {
        return service.executeWithRetry(op);
    }
}
