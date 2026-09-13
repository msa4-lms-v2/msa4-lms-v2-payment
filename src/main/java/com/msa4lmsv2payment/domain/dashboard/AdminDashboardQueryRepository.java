package com.msa4lmsv2payment.domain.dashboard;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import static com.msa4lmsv2payment.domain.dashboard.AdminDashboardResponse.*;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public Summary summary() {
        Long count = jdbc.queryForObject("select count(*) from installment_plans where status = 'REQUESTED'",
                Map.of(), Long.class);
        // Certificates are issued synchronously by DocumentService; no approval queue exists.
        return new Summary(count == null ? 0 : count, 0);
    }

    public List<Task> tasks() {
        return jdbc.query("""
            select p.id, p.tuition_bill_id, p.created_at,
                   coalesce(s.display_name, concat('학생 #', b.student_id)) requester_name
            from installment_plans p join tuition_bills b on b.id = p.tuition_bill_id
            left join student_snapshots s on s.student_id = b.student_id
            where p.status = 'REQUESTED' order by p.created_at, p.id limit 30
            """, Map.of(), (rs, row) -> new Task(rs.getLong("id"), "INSTALLMENT",
                rs.getString("requester_name"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getLong("tuition_bill_id")));
    }

    public TuitionStats tuitionStats(long semesterId) {
        // Aggregate each one-to-many relationship separately before joining. Otherwise
        // multiple payments, refunds and scholarships multiply one another's amounts.
        return jdbc.queryForObject("""
            select coalesce(sum(case when net_paid >= net_due then 1 else 0 end), 0) paid,
                   coalesce(sum(case when net_paid > 0 and net_paid < net_due then 1 else 0 end), 0) in_progress,
                   coalesce(sum(case when net_paid = 0 and net_due > 0 then 1 else 0 end), 0) unpaid
            from (
                select b.id,
                       greatest(b.billing_amount - coalesce(s.amount, 0), 0) net_due,
                       greatest(coalesce(p.amount, 0) - coalesce(r.amount, 0), 0) net_paid
                from tuition_bills b
                left join (select tuition_bill_id, sum(amount) amount from scholarships group by tuition_bill_id) s
                    on s.tuition_bill_id = b.id
                left join (select tuition_bill_id, sum(amount) amount from payments
                           where status = 'SUCCEEDED' group by tuition_bill_id) p on p.tuition_bill_id = b.id
                left join (select tuition_bill_id, sum(amount) amount from refunds
                           where status = 'SUCCEEDED' group by tuition_bill_id) r on r.tuition_bill_id = b.id
                where b.semester_id = :semesterId
            ) balances
            """, Map.of("semesterId", semesterId), (rs, row) -> new TuitionStats(
                rs.getLong("paid"), rs.getLong("in_progress"), rs.getLong("unpaid")));
    }
}
