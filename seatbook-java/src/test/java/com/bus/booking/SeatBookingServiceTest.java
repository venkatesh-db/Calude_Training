package com.bus.booking;

import com.bus.booking.db.BookingRepository;
import com.bus.booking.db.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * All tests here run sequentially against a single thread, so the
 * check-then-act race in SeatBookingService never has a chance to trigger.
 * The suite is green even though the service is unsafe under real
 * concurrent load -- see ConcurrentBookFixture for that.
 */
class SeatBookingServiceTest {

    private SeatBookingService service;
    private BookingRepository repository;

    @BeforeEach
    void setUp() {
        Database.reset();
        repository = new BookingRepository(Database.dataSource());
        service = new SeatBookingService(repository);
    }

    @Test
    void booksAnAvailableSeat() {
        Booking booking = service.bookSeat("TRIP-1", 1, "Asha", false);
        assertEquals("TRIP-1", booking.tripId());
        assertEquals(1, booking.seatNo());
        assertEquals("Asha", booking.passengerName());
    }

    @Test
    void appliesBaseFareOffPeak() {
        Booking booking = service.bookSeat("TRIP-1", 1, "Asha", false);
        assertEquals(new BigDecimal("450.00"), booking.fare());
    }

    @Test
    void appliesPeakSurcharge() {
        Booking booking = service.bookSeat("TRIP-1", 1, "Asha", true);
        assertEquals(new BigDecimal("525.00"), booking.fare());
    }

    @Test
    void rejectsSecondBookingOfSameSeat() {
        service.bookSeat("TRIP-1", 5, "Asha", false);
        assertThrows(SeatUnavailableException.class,
                () -> service.bookSeat("TRIP-1", 5, "Ravi", false));
    }

    @Test
    void allowsSameSeatNumberOnDifferentTrips() {
        service.bookSeat("TRIP-1", 5, "Asha", false);
        Booking other = service.bookSeat("TRIP-2", 5, "Ravi", false);
        assertEquals("TRIP-2", other.tripId());
    }

    @Test
    void allowsDifferentSeatsOnSameTrip() {
        service.bookSeat("TRIP-1", 1, "Asha", false);
        Booking other = service.bookSeat("TRIP-1", 2, "Ravi", false);
        assertEquals(2, other.seatNo());
    }

    @Test
    void rejectsBlankTripId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bookSeat("  ", 1, "Asha", false));
    }

    @Test
    void rejectsNonPositiveSeatNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bookSeat("TRIP-1", 0, "Asha", false));
    }

    @Test
    void rejectsBlankPassengerName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bookSeat("TRIP-1", 1, "", false));
    }

    @Test
    void repositoryReflectsExactlyOneRowAfterASuccessfulBooking() {
        service.bookSeat("TRIP-1", 9, "Asha", false);
        assertTrue(repository.countBookings("TRIP-1", 9) == 1);
    }
}
