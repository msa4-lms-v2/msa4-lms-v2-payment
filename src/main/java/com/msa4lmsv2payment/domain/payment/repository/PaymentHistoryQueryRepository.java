package com.msa4lmsv2payment.domain.payment.repository;

import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.entity.PaymentType;
import com.msa4lmsv2payment.domain.payment.entity.QPayment;
import com.msa4lmsv2payment.domain.payment.response.PaymentHistoryResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.QTuitionBill;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentHistoryQueryRepository {

    private static final QPayment payment = QPayment.payment;
    private static final QTuitionBill tuitionBill = QTuitionBill.tuitionBill;

    private final JPAQueryFactory jpaQueryFactory;

    // CaseBuilder().then(enum)/.otherwise(enum)로 납부구분을 SQL CASE로 계산하면
    // Hibernate 7이 SqmParameter의 ValueMapping을 못 정해 500(JpaSystemException)을 던진다.
    // installmentPlanItemId를 그대로 받아 자바에서 납부구분을 판정해 우회한다.
    public List<PaymentHistoryResponseDTO> findMyHistory(Long studentId, PaymentStatus status) {
        List<Tuple> rows = jpaQueryFactory
                .select(
                        payment.tuitionBillId,
                        tuitionBill.semesterId,
                        payment.installmentPlanItemId,
                        payment.completedAt,
                        payment.amount,
                        payment.status
                )
                .from(payment)
                .join(tuitionBill).on(tuitionBill.id.eq(payment.tuitionBillId))
                .where(payment.studentId.eq(studentId), statusEq(status))
                .orderBy(payment.requestedAt.desc())
                .fetch();

        return rows.stream()
                .map(row -> new PaymentHistoryResponseDTO(
                        row.get(payment.tuitionBillId),
                        row.get(tuitionBill.semesterId),
                        row.get(payment.installmentPlanItemId) == null ? PaymentType.LUMP_SUM : PaymentType.INSTALLMENT,
                        row.get(payment.completedAt),
                        row.get(payment.amount),
                        row.get(payment.status)
                ))
                .toList();
    }

    private BooleanExpression statusEq(PaymentStatus status) {
        return status == null ? null : payment.status.eq(status);
    }
}
