package com.bus.booking;

import com.bus.booking.db.BookingRepository;
import com.bus.booking.db.SeatAlreadyBookedException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Books seats on intercity trips.
 *
 * isAvailable() is only a fast-path optimization to skip pricing for an
 * obviously-taken seat -- it is not the source of truth, since booking goes
 * through a pooled DataSource and BookingRepository opens/closes a fresh
 * connection per call, leaving a check-then-act window between isAvailable()
 * and insert(). The actual guard against a double sale is the (trip_id,
 * seat_no) unique constraint enforced by the database on insert(); a
 * constraint violation there is what turns into SeatUnavailableException.
 */
public class SeatBookingService {

    private static final BigDecimal BASE_FARE = new BigDecimal("450.00");
    private static final BigDecimal PEAK_SURCHARGE = new BigDecimal("75.00");

    private final BookingRepository repository;

    public SeatBookingService(BookingRepository repository) {
        this.repository = repository;
    }

    /**
     * Books a seat for a passenger.
     *
     * @throws SeatUnavailableException if the seat is already booked
     */
    public Booking bookSeat(String tripId, int seatNo, String passengerName, boolean peakHour) {
        validate(tripId, seatNo, passengerName);

        // --- check ---
        if (!repository.isAvailable(tripId, seatNo)) {
            throw new SeatUnavailableException(tripId, seatNo);
        }

        // --- price (calls out to a "pricing" step that takes real time) ---
        BigDecimal fare = fareFor(tripId, seatNo, peakHour);

        // --- act ---
        try {
            repository.insert(tripId, seatNo, passengerName, fare);
        } catch (SeatAlreadyBookedException e) {
            throw new SeatUnavailableException(tripId, seatNo);
        }

        return new Booking(tripId, seatNo, passengerName, fare);
    }

    /**
     * Computes the fare for a seat. In production this calls a pricing
     * micro-service; here it's simulated with a short sleep so the
     * check-then-act window in bookSeat() is wide enough to matter under
     * real concurrent load.
     */
    private BigDecimal fareFor(String tripId, int seatNo, boolean peakHour) {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pricing seat", e);
        }
        BigDecimal fare = BASE_FARE;
        if (peakHour) {
            fare = fare.add(PEAK_SURCHARGE);
        }
        return fare.setScale(2, RoundingMode.HALF_UP);
    }

    private void validate(String tripId, int seatNo, String passengerName) {
        if (tripId == null || tripId.isBlank()) {
            throw new IllegalArgumentException("tripId must not be blank");
        }
        if (seatNo <= 0) {
            throw new IllegalArgumentException("seatNo must be positive");
        }
        if (passengerName == null || passengerName.isBlank()) {
            throw new IllegalArgumentException("passengerName must not be blank");
        }
    }
}
