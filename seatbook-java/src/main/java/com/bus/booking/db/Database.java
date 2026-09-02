package com.bus.booking.db;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared in-memory H2 instance. DB_CLOSE_DELAY=-1 keeps the database alive
 * for the lifetime of the JVM even though every caller opens and closes its
 * own connection (this mimics a real pooled DataSource in production).
 */
public final class Database {

    private static final String URL =
            "jdbc:h2:mem:seatbook;DB_CLOSE_DELAY=-1";

    private static final DataSource DATA_SOURCE = buildDataSource();

    private Database() {
    }

    public static DataSource dataSource() {
        return DATA_SOURCE;
    }

    private static DataSource buildDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(URL);
        ds.setUser("sa");
        ds.setPassword("");
        initSchema(ds);
        return ds;
    }

    private static void initSchema(DataSource ds) {
        // UNIQUE constraint on (trip_id, seat_no) is the actual guard against
        // a double sale: the application-level isAvailable() check races
        // under concurrent load, so the database is the final arbiter.
        String ddl = """
                CREATE TABLE IF NOT EXISTS bookings (
                    id IDENTITY PRIMARY KEY,
                    trip_id VARCHAR(64) NOT NULL,
                    seat_no INT NOT NULL,
                    passenger_name VARCHAR(128) NOT NULL,
                    fare DECIMAL(10,2) NOT NULL,
                    CONSTRAINT uq_bookings_trip_seat UNIQUE (trip_id, seat_no)
                )
                """;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize schema", e);
        }
    }

    /** Test/fixture helper: wipe all rows between runs. */
    public static void reset() {
        try (Connection conn = DATA_SOURCE.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM bookings");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset schema", e);
        }
    }
}
