package com.msa4lmsv2payment.global.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IdempotencyService idempotencyService;

    private IdempotencyService service() {
        return new IdempotencyService(idempotencyKeyRepository, objectMapper);
    }

    @Test
    void 처음_쓰는_키는_통과하고_새로_저장된다() {
        idempotencyService = service();
        when(idempotencyKeyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        assertThatCode(() -> idempotencyService.verifyAndReserve("key-1", 1L, "/api/refunds/virtual-account-requests", "body"))
                .doesNotThrowAnyException();
    }

    @Test
    void 같은_키_같은_요청_재시도는_통과한다() {
        idempotencyService = service();
        IdempotencyKey existing = new IdempotencyKey("key-1", 1L, "/api/refunds/virtual-account-requests",
                hashOf("body"), IdempotencyKeyStatus.COMPLETED, LocalDateTime.now().plusDays(1));
        when(idempotencyKeyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatCode(() -> idempotencyService.verifyAndReserve("key-1", 1L, "/api/refunds/virtual-account-requests", "body"))
                .doesNotThrowAnyException();
    }

    @Test
    void 같은_키_다른_요청은_거부된다() {
        idempotencyService = service();
        IdempotencyKey existing = new IdempotencyKey("key-1", 1L, "/api/refunds/virtual-account-requests",
                hashOf("other-body"), IdempotencyKeyStatus.COMPLETED, LocalDateTime.now().plusDays(1));
        when(idempotencyKeyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.verifyAndReserve("key-1", 1L, "/api/refunds/virtual-account-requests", "body"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void 처리중인_요청이_아직_만료되지_않았으면_거부된다() {
        idempotencyService = service();
        IdempotencyKey existing = new IdempotencyKey("key-1", 1L, "/api/refunds/virtual-account-requests",
                hashOf("body"), IdempotencyKeyStatus.IN_PROGRESS, LocalDateTime.now().plusDays(1));
        when(idempotencyKeyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.verifyAndReserve("key-1", 1L, "/api/refunds/virtual-account-requests", "body"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    private String hashOf(Object body) {
        IdempotencyService s = service();
        try {
            var method = IdempotencyService.class.getDeclaredMethod("hash", Object.class);
            method.setAccessible(true);
            return (String) method.invoke(s, body);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
