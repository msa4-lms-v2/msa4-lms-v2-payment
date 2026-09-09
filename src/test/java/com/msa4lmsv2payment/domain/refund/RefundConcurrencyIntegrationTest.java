package com.msa4lmsv2payment.domain.refund;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.PgCancelRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-187 - 같은 결제에 부분환불(PG_CANCEL) 요청 2건이 진짜 동시에 들어와도(조회와 저장 사이의 경합 창)
 * uk_refunds_dedup(PAY:paymentId) 위반이 일반 500이 아니라 기존 REQUESTED 건을 갱신하는 정상 응답으로
 * 흡수되고, 최종적으로 환불 레코드가 하나만 남는지 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class RefundConcurrencyIntegrationTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, "ADMIN");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private RefundService refundService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Test
    void concurrentPartialRefundRequestsResultInOneUpdatedRefund() throws Exception {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(9201L, 9201L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-race-1");
        payment = paymentRepository.save(payment);
        Long paymentId = payment.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Object[] outcomes = new Object[2];
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> outcomes[0] = attempt(paymentId, BigDecimal.valueOf(200_000), "1차 요청", ready, start));
            Future<?> second = pool.submit(() -> outcomes[1] = attempt(paymentId, BigDecimal.valueOf(500_000), "2차 요청", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }

        // 두 요청 모두 예외 없이 정상 응답을 받아야 한다 - 어느 쪽이 이기든 결과는 하나의 REQUESTED 환불로 수렴한다.
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(RefundResponseDTO.class));
        List<?> refunds = refundRepository.findByTuitionBillIdOrderByRequestedAtDesc(bill.getId());
        assertThat(refunds).hasSize(1);
    }

    private Object attempt(Long paymentId, BigDecimal amount, String reason, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return refundService.createPgCancelRefund(ADMIN, new PgCancelRefundRequestDTO(paymentId, amount, reason));
    }
}
