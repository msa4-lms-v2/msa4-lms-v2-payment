package com.msa4lmsv2payment.domain.installment.entity;

import com.msa4lmsv2payment.global.error.InstallmentItemAlreadyPaidException;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "installment_plan_items")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class InstallmentPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long installmentPlanId;

    private Integer roundNo;

    private BigDecimal amount;

    private LocalDate dueDate;

    private Long paymentId;

    @Enumerated(EnumType.STRING)
    private InstallmentItemStatus status;

    public InstallmentPlanItem(Long installmentPlanId, Integer roundNo, BigDecimal amount, LocalDate dueDate) {
        this.installmentPlanId = installmentPlanId;
        this.roundNo = roundNo;
        this.amount = amount;
        this.dueDate = dueDate;
        this.status = InstallmentItemStatus.SCHEDULED;
    }

    // 결제창 연동(체크아웃 세션 생성) 시점에 회차가 이미 결제됐거나 다른 결제에 물려있지 않은지 막는다.
    public void assignPayment(Long paymentId) {
        if (status == InstallmentItemStatus.PAID) {
            throw new InstallmentItemAlreadyPaidException("이미 납부 완료된 회차입니다.");
        }
        this.paymentId = paymentId;
    }

    public void markPaid() {
        this.status = InstallmentItemStatus.PAID;
    }

    // 장학금 변경으로 실납부액이 바뀌었을 때 미납 회차 금액을 다시 나눈 값으로 갱신한다. 이미 납부된 회차는 소급해 바꾸지 않는다.
    public void updateAmount(BigDecimal newAmount) {
        if (status == InstallmentItemStatus.PAID) {
            throw new InstallmentItemAlreadyPaidException("이미 납부 완료된 회차입니다.");
        }
        this.amount = newAmount;
    }

    // 기한이 지나도록 미납인 회차만 대상이다(스케줄러가 SCHEDULED만 조회해서 부르므로 여기서는 무조건 전환).
    public void markOverdue() {
        this.status = InstallmentItemStatus.OVERDUE;
    }
}
