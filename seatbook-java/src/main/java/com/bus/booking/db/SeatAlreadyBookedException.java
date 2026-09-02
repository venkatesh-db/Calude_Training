package com.bus.booking.db;

import java.sql.SQLException;

/**
 * Thrown when an insert is rejected by the (trip_id, seat_no) unique
 * constraint -- the database-level signal that a concurrent caller won the
 * race for this seat.
 */
public class SeatAlreadyBookedException extends RuntimeException {

    public SeatAlreadyBookedException(String tripId, int seatNo, SQLException cause) {
        super("Seat " + seatNo + " on trip " + tripId + " was booked by a concurrent request", cause);
    }
}
