package com.visordocs.infrastructure.search;

import com.visordocs.domain.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchServiceExecuteWithRetryTest {

    @Test
    void executeWithRetry_returnsValueOnFirstAttempt() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 3);
        setField(real, "retryDelayMs", 1);

        Supplier<String> op = mock(Supplier.class);
        when(op.get()).thenReturn("success");

        String result = invokeExecuteWithRetry(real, op);
        assertThat(result).isEqualTo("success");
        verify(op, times(1)).get();
    }

    @Test
    void executeWithRetry_retriesOnRuntimeException_thenSuccess() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 3);
        setField(real, "retryDelayMs", 1);

        Supplier<String> op = mock(Supplier.class);
        when(op.get())
                .thenThrow(new RuntimeException("fail"))
                .thenThrow(new RuntimeException("fail"))
                .thenReturn("success");

        String result = invokeExecuteWithRetry(real, op);
        assertThat(result).isEqualTo("success");
        verify(op, times(3)).get();
    }

    @Test
    void executeWithRetry_doesNotRetryOnAppExceptionClientError() throws Throwable {
        ElasticsearchService real = new ElasticsearchService();
        setField(real, "maxRetryAttempts", 3);
        setField(real, "retryDelayMs", 1);

        Supplier<String> op = mock(Supplier.class);
        when(op.get()).thenThrow(AppException.validationError("client error"));

        Throwable thrown = catchThrowable(() -> invokeExecuteWithRetry(real, op));
        assertThat(thrown).isInstanceOf(AppException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
        verify(op, times(1)).get();
    }

    private void setField(Object target, String name, Object value) throws Throwable {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private <T> T invokeExecuteWithRetry(ElasticsearchService service, Supplier<T> op) throws Throwable {
        java.lang.reflect.Method m = ElasticsearchService.class.getDeclaredMethod("executeWithRetry", Supplier.class);
        m.setAccessible(true);
        try {
            return (T) m.invoke(service, op);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
