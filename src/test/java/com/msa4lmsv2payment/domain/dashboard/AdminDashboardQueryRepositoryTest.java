package com.msa4lmsv2payment.domain.dashboard;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class AdminDashboardQueryRepositoryTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    static JdbcTemplate jdbc;
    AdminDashboardQueryRepository queries;
    @BeforeAll static void start() {
        MYSQL.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("create table tuition_bills(id bigint primary key, student_id bigint, semester_id bigint, billing_amount decimal(15,2))");
        jdbc.execute("create table installment_plans(id bigint primary key, tuition_bill_id bigint, status varchar(30), created_at datetime)");
        jdbc.execute("create table student_snapshots(student_id bigint primary key, display_name varchar(100))");
        jdbc.execute("create table scholarships(id bigint primary key, tuition_bill_id bigint, amount decimal(15,2))");
        jdbc.execute("create table payments(id bigint primary key, tuition_bill_id bigint, amount decimal(15,2), status varchar(30))");
        jdbc.execute("create table refunds(id bigint primary key, tuition_bill_id bigint, amount decimal(15,2), status varchar(30))");
    }
    @AfterAll static void stop() { MYSQL.stop(); }
    @BeforeEach void reset() {
        for (String table : new String[]{"tuition_bills","installment_plans","student_snapshots","scholarships","payments","refunds"}) jdbc.update("delete from " + table);
        queries = new AdminDashboardQueryRepository(new NamedParameterJdbcTemplate(jdbc));
    }
    @Test void balancesUseScholarshipsSuccessfulPaymentsAndRefundsWithoutJoinMultiplication() {
        // 1: partial; 2: full scholarship; 3: unpaid after full refund; 4: paid; 5: old semester; 6: zero bill.
        jdbc.update("insert into tuition_bills values (1,1,20,1000),(2,2,20,1000),(3,3,20,1000),(4,4,20,1000),(5,5,19,1000),(6,6,20,0)");
        jdbc.update("insert into scholarships values (1,1,100),(2,1,100),(3,2,1000)");
        jdbc.update("insert into payments values (1,1,300,'SUCCEEDED'),(2,1,300,'SUCCEEDED'),(3,1,900,'FAILED'),(4,3,1000,'SUCCEEDED'),(5,4,1000,'SUCCEEDED'),(6,1,900,'REQUESTED')");
        jdbc.update("insert into refunds values (1,1,100,'SUCCEEDED'),(2,1,100,'SUCCEEDED'),(3,1,500,'FAILED'),(4,3,1000,'SUCCEEDED')");
        assertThat(queries.tuitionStats(20)).isEqualTo(new AdminDashboardResponse.TuitionStats(3,1,1));
        assertThat(queries.tuitionStats(999)).isEqualTo(new AdminDashboardResponse.TuitionStats(0,0,0));
    }
    @Test void pendingSummaryIsUnboundedAndTasksCarryBillLinkAndSnapshotFallback() {
        jdbc.update("insert into tuition_bills values (1,55,19,1000)");
        for(int i=1; i<=35; i++) jdbc.update("insert into installment_plans values (?,1,'REQUESTED','2025-01-01')", i);
        jdbc.update("insert into installment_plans values (36,1,'ACTIVE','2025-01-01'),(37,1,'REJECTED','2025-01-01')");
        assertThat(queries.summary()).isEqualTo(new AdminDashboardResponse.Summary(35,0));
        assertThat(queries.tasks()).hasSize(30);
        assertThat(queries.tasks().getFirst().tuitionBillId()).isEqualTo(1L);
        assertThat(queries.tasks().getFirst().requesterName()).isEqualTo("학생 #55");
    }
}
