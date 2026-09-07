package com.msa4lmsv2payment;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 MySQL에 schema.sql만 그대로 적용해도 코드가 요구하는 스키마가 재현되는지 확인한다.
 * dev에 커밋되지 않은 DDL이 있으면 새 환경에서 이 테스트가 실패한다.
 */
@Testcontainers
class SchemaReproducibilityTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("lms_payment_schema_test");

    @Test
    void schemaSqlAppliesCleanlyToFreshDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));

            assertThat(tableExists(connection, "virtual_account_deposits")).isTrue();
            assertThat(columnExists(connection, "virtual_accounts", "order_id")).isTrue();
            assertThat(columnExists(connection, "virtual_accounts", "secret")).isTrue();
            assertThat(tableExists(connection, "installment_plans")).isTrue();
            assertThat(tableExists(connection, "installment_plan_items")).isTrue();
            assertThat(columnExists(connection, "payments", "installment_plan_item_id")).isTrue();
            assertThat(columnExists(connection, "refunds", "retry_count")).isTrue();
            assertThat(columnExists(connection, "virtual_accounts", "installment_plan_item_id")).isTrue();
            assertThat(columnExists(connection, "virtual_accounts", "payment_key")).isTrue();
            assertThat(columnExists(connection, "refunds", "refund_dedup_key")).isTrue();
            assertThat(columnExists(connection, "refunds", "refund_bank_code")).isTrue();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "'")) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                             + "AND TABLE_NAME = '" + tableName + "' AND COLUMN_NAME = '" + columnName + "'")) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }
}
