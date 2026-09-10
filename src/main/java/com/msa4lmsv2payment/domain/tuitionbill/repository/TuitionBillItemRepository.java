package com.msa4lmsv2payment.domain.tuitionbill.repository;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TuitionBillItemRepository extends JpaRepository<TuitionBillItem, Long> {
    List<TuitionBillItem> findByTuitionBillIdOrderByIdAsc(Long tuitionBillId);
}
