package com.lk.quantfund.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lk.quantfund.entity.QuantSignal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuantSignalMapperTest {
    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("""
                    CREATE TABLE quant_signal (
                        id BIGINT PRIMARY KEY, user_id BIGINT, account_id BIGINT, holding_id BIGINT,
                        fund_code VARCHAR, fund_name VARCHAR, action VARCHAR, action_text VARCHAR,
                        suggest_amount DECIMAL, suggest_ratio DECIMAL, risk_level VARCHAR,
                        confidence DECIMAL, total_score DECIMAL, trend_score DECIMAL,
                        opportunity_score DECIMAL, risk_score DECIMAL, position_score DECIMAL,
                        momentum_score DECIMAL, reasons_json VARCHAR, risks_json VARCHAR,
                        metrics_json VARCHAR, model_name VARCHAR, model_version VARCHAR,
                        deadline TIMESTAMP, signal_time TIMESTAMP, fallback_used INT, deleted INT,
                        request_payload CLOB, response_payload CLOB
                    )
                    """);
            sql.execute("""
                    INSERT INTO quant_signal
                        (id, user_id, account_id, holding_id, fund_code, action, signal_time, deleted,
                         reasons_json, metrics_json, request_payload, response_payload) VALUES
                        (1, 1, 10, 100, 'A', 'BUY',  '2026-09-10 10:00:00', 0, '[]', '{}', 'raw', 'raw'),
                        (2, 1, 10, 100, 'A', 'HOLD', '2026-09-11 10:00:00', 0, '[]', '{}', 'raw', 'raw'),
                        (3, 1, 10, 100, 'A', 'SELL', '2026-09-11 10:00:00', 0, '[]', '{}', 'raw', 'raw'),
                        (4, 1, 20, 200, 'B', 'BUY',  '2026-09-11 11:00:00', 0, '[]', '{}', 'raw', 'raw'),
                        (5, 1, 10, 100, 'A', 'BUY',  '2026-09-11 12:00:00', 1, '[]', '{}', 'raw', 'raw'),
                        (6, 2, 10, 100, 'A', 'BUY',  '2026-09-11 13:00:00', 0, '[]', '{}', 'raw', 'raw'),
                        (7, 1, 30, NULL, 'C', 'BUY', '2026-09-09 10:00:00', 0, '[]', '{}', 'raw', 'raw'),
                        (8, 1, 30, NULL, 'C', 'HOLD','2026-09-10 10:00:00', 0, '[]', '{}', 'raw', 'raw')
                    """);
        }
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(QuantSignalMapper.class);
        sessions = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void returnsLatestPerHoldingWithDeterministicTiesWithoutHistoryPayloads() {
        try (SqlSession session = sessions.openSession()) {
            var rows = session.getMapper(QuantSignalMapper.class).selectLatestSignals(1L, null, null, null, null);
            assertThat(rows).extracting(QuantSignal::getId).containsExactly(4L, 3L, 8L);
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.getRequestPayload()).isNull();
                assertThat(row.getResponsePayload()).isNull();
                assertThat(row.getMetricsJson()).isEqualTo("{}");
                assertThat(row.getReasonsJson()).isEqualTo("[]");
            });
        }
    }

    @Test
    void preservesFilteringBeforeLatestSelectionAndUserIsolation() {
        try (SqlSession session = sessions.openSession()) {
            var mapper = session.getMapper(QuantSignalMapper.class);
            assertThat(mapper.selectLatestSignals(1L, null, null, null, "BUY"))
                    .extracting(QuantSignal::getId).containsExactly(4L, 1L, 7L);
            assertThat(mapper.selectLatestSignals(1L, 10L, 100L, "A", null))
                    .extracting(QuantSignal::getId).containsExactly(3L);
            assertThat(mapper.selectLatestSignals(1L, 20L, null, null, null))
                    .extracting(QuantSignal::getId).containsExactly(4L);
            assertThat(mapper.selectLatestSignals(2L, null, null, null, null))
                    .extracting(QuantSignal::getId).containsExactly(6L);
            assertThat(mapper.selectLatestSignals(3L, null, null, null, null)).isEmpty();
            assertThat(mapper.selectLatestSignals(1L, null, null, "A' OR 1=1 --", null)).isEmpty();
        }
    }
}
