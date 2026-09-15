package com.msa4lmsv2payment.domain.installment.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class InstallmentEligibilityRepository {
    private final EntityManager entityManager;

    // 납부 후에도 감사 로그와 실제 완료일로 연체 이력을 확인한다.
    // 승인된 분할납부는 원 고지 기한 대신 회차 기한으로 판단한다.
    public boolean hasDelinquency(Long studentId, LocalDate today) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*) from tuition_bills b
                where b.student_id = :studentId and (
                  (not exists (select 1 from installment_plans p where p.tuition_bill_id = b.id
                      and p.status in ('ACTIVE', 'COMPLETED')) and (
                    b.status = 'OVERDUE' or (b.status in ('UNPAID', 'PARTIAL') and b.due_date < :today)
                    or exists (select 1 from audit_logs a where a.target_type = 'TUITION_BILL'
                        and a.target_id = b.id and a.action = 'TUITION_BILL_OVERDUE')
                    or exists (select 1 from payments x where x.tuition_bill_id = b.id
                        and x.status = 'SUCCEEDED' and x.installment_plan_item_id is null
                        and date(x.completed_at) > b.due_date)
                  ))
                  or exists (select 1 from installment_plans p join installment_plan_items i
                      on i.installment_plan_id = p.id
                    where p.tuition_bill_id = b.id and p.status in ('ACTIVE', 'COMPLETED') and (
                      i.status = 'OVERDUE' or (i.status = 'SCHEDULED' and i.due_date < :today)
                      or exists (select 1 from audit_logs a where a.target_type = 'INSTALLMENT_PLAN_ITEM'
                          and a.target_id = i.id and a.action = 'INSTALLMENT_ITEM_OVERDUE')
                      or exists (select 1 from payments x where x.installment_plan_item_id = i.id
                          and x.status = 'SUCCEEDED' and date(x.completed_at) > i.due_date)
                    ))
                )
                """).setParameter("studentId", studentId).setParameter("today", today).getSingleResult();
        return count.longValue() > 0;
    }
}
