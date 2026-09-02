package com.bus.booking.db;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data access for bookings. Every method pulls its own connection from the
 * pooled DataSource and releases it immediately -- there is no transaction
 * spanning a read-check-write sequence anywhere in this class or its caller.
 */
public class BookingRepository {

    private final DataSource dataSource;

    public BookingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isAvailable(String tripId, int seatNo) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE trip_id = ? AND seat_no = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tripId);
            ps.setInt(2, seatNo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check seat availability", e);
        }
    }

    /**
     * @throws SeatAlreadyBookedException if the (trip_id, seat_no) unique
     *     constraint rejects this insert -- the seat was booked by a
     *     concurrent caller between the caller's availability check and now
     */
    public void insert(String tripId, int seatNo, String passengerName, BigDecimal fare) {
        String sql = "INSERT INTO bookings (trip_id, seat_no, passenger_name, fare) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tripId);
            ps.setInt(2, seatNo);
            ps.setString(3, passengerName);
            ps.setBigDecimal(4, fare);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                throw new SeatAlreadyBookedException(tripId, seatNo, e);
            }
            throw new IllegalStateException("Failed to insert booking", e);
        }
    }

    /** H2 (and standard SQL) unique/PK violation is SQLState class 23. */
    private boolean isUniqueViolation(SQLException e) {
        String sqlState = e.getSQLState();
        return sqlState != null && sqlState.startsWith("23");
    }

    public int countBookings(String tripId, int seatNo) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE trip_id = ? AND seat_no = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tripId);
            ps.setInt(2, seatNo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count bookings", e);
        }
    }
}
