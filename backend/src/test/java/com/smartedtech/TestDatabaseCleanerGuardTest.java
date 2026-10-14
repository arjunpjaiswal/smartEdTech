package com.smartedtech;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDatabaseCleanerGuardTest {

    @Test
    @DisplayName("Task 6: isTestSchema accurately identifies test schemas ending with _test")
    void isTestSchemaIdentifiesCorrectly() {
        // Valid test schemas
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/smartedtech_test?useSSL=false", "smartedtech_test")).isTrue();
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/my_app_test", "my_app_test")).isTrue();
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/db_test?serverTimezone=UTC", "")).isTrue();

        // Non-test production/dev schemas
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/smartedtech?useSSL=false", "smartedtech")).isFalse();
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/production", "production")).isFalse();
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/smartedtech_testing", "smartedtech_testing")).isFalse();
        assertThat(TestDatabaseCleaner.isTestSchema("jdbc:mysql://localhost:3306/smartedtech", null)).isFalse();
    }

    @Test
    @DisplayName("Task 6: verifyTestDatabase refuses to run on non-test schema and throws IllegalStateException")
    void cleanerRefusesToRunOnNonTestDatabase() throws SQLException {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.getCatalog()).thenReturn("smartedtech"); // production database!
        when(mockConn.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/smartedtech");

        TestDatabaseCleaner cleaner = new TestDatabaseCleaner(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                mockDs
        );

        assertThatThrownBy(cleaner::verifyTestDatabase)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAFETY GUARD REJECTION: TestDatabaseCleaner refused to run on non-test database")
                .hasMessageContaining("smartedtech");
    }
}
