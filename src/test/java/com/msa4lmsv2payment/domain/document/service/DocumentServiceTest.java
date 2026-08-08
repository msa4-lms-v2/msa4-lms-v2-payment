package com.msa4lmsv2payment.domain.document.service;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.global.error.PaymentNotCompletedException;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private TuitionBillService tuitionBillService;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private DocumentService documentService;

    private static TuitionBill tuitionBill(Long id) {
        TuitionBill bill = new TuitionBill(20260001L, 5L, BigDecimal.valueOf(4_200_000), LocalDate.of(2026, 9, 1), TuitionBillStatus.PAID, 1L);
        setField(bill, "id", id);
        return bill;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = TuitionBill.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // SCRUM-114: 납부 확인서
    @Test
    void 납부_이력이_있으면_확인서가_발급된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(tuitionBill(1L));
        when(paymentService.hasSucceededPayment(1L)).thenReturn(true);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = documentService.issuePaymentReceipt(student, new PaymentReceiptRequestDTO(1L));

        assertThat(result.documentType()).isEqualTo(DocumentType.PAYMENT_CERTIFICATE);
        assertThat(result.verificationToken()).isNotBlank();
    }

    @Test
    void 납부_이력이_없으면_발급이_거부된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(tuitionBill(1L));
        when(paymentService.hasSucceededPayment(1L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.issuePaymentReceipt(student, new PaymentReceiptRequestDTO(1L)))
                .isInstanceOf(PaymentNotCompletedException.class);
    }

    @Test
    void 발급된_확인서는_학생소유이고_교수id는_비어있다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(tuitionBill(1L));
        when(paymentService.hasSucceededPayment(1L)).thenReturn(true);
        when(documentRepository.save(any())).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            assertThat(d.getStudentId()).isEqualTo(20260001L);
            assertThat(d.getProfessorId()).isNull();
            return d;
        });

        documentService.issuePaymentReceipt(student, new PaymentReceiptRequestDTO(1L));
    }
}
