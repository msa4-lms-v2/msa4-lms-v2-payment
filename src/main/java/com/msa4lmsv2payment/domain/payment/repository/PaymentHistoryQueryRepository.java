package com.msa4lmsv2payment.domain.payment.repository;

import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.entity.PaymentType;
import com.msa4lmsv2payment.domain.payment.entity.QPayment;
import com.msa4lmsv2payment.domain.payment.response.PaymentHistoryResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.QTuitionBill;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.EnumExpression;
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

    public List<PaymentHistoryResponseDTO> findMyHistory(Long studentId, PaymentStatus status) {
        return jpaQueryFactory
                .select(Projections.constructor(
                        PaymentHistoryResponseDTO.class,
                        payment.tuitionBillId,
                        tuitionBill.semesterId,
                        paymentType(),
                        payment.completedAt,
                        payment.amount,
                        payment.status
                ))
                .from(payment)
                .join(tuitionBill).on(tuitionBill.id.eq(payment.tuitionBillId))
                .where(payment.studentId.eq(studentId), statusEq(status))
                .orderBy(payment.requestedAt.desc())
                .fetch();
    }

    private EnumExpression<PaymentType> paymentType() {
        return new CaseBuilder()
                .when(payment.installmentPlanItemId.isNull())
                .then(PaymentType.LUMP_SUM)
                .otherwise(PaymentType.INSTALLMENT);
    }

    private BooleanExpression statusEq(PaymentStatus status) {
        return status == null ? null : payment.status.eq(status);
    }
}
