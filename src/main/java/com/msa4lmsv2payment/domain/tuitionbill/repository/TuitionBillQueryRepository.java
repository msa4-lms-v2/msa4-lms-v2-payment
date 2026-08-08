package com.msa4lmsv2payment.domain.tuitionbill.repository;

import com.msa4lmsv2payment.domain.tuitionbill.entity.QTuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TuitionBillQueryRepository {

    private static final QTuitionBill tuitionBill = QTuitionBill.tuitionBill;

    private final JPAQueryFactory jpaQueryFactory;

    public List<TuitionBill> search(TuitionBillStatus status, int offset, int limit) {
        return jpaQueryFactory
                .selectFrom(tuitionBill)
                .where(statusEq(status))
                .orderBy(tuitionBill.createdAt.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long count(TuitionBillStatus status) {
        Long total = jpaQueryFactory
                .select(tuitionBill.count())
                .from(tuitionBill)
                .where(statusEq(status))
                .fetchOne();
        return total == null ? 0L : total;
    }

    private BooleanExpression statusEq(TuitionBillStatus status) {
        return status == null ? null : tuitionBill.status.eq(status);
    }
}
