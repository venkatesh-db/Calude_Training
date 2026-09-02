package com.bus.booking;

import com.bus.booking.db.BookingRepository;
import com.bus.booking.db.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the seat-sold-twice defect: N passengers race to book
 * the same seat on the same trip. Unlike SeatBookingServiceTest, this
 * dispatches real concurrent threads so the check-then-act window in
 * bookSeat() actually gets exercised, instead of the sequential ordering
 * that let the race pass unnoticed before.
 */
class SeatBookingConcurrencyTest {

    private static final String TRIP_ID = "TRIP-RACE";
    private static final int SEAT_NO = 14;
    private static final int CONTENDERS = 8;

    private SeatBookingService service;
    private BookingRepository repository;

    @BeforeEach
    void setUp() {
        Database.reset();
        repository = new BookingRepository(Database.dataSource());
        service = new SeatBookingService(repository);
    }

    @Test
    void exactlyOneOfManyConcurrentRequestsForTheSameSeatSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CountDownLatch startLine = new CountDownLatch(1);

        List<Callable<Boolean>> contenders = IntStream.rangeClosed(1, CONTENDERS)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    startLine.await();
                    try {
                        service.bookSeat(TRIP_ID, SEAT_NO, "Passenger-" + i, false);
                        return true;
                    } catch (SeatUnavailableException e) {
                        return false;
                    }
                })
                .toList();

        List<Future<Boolean>> futures = contenders.stream()
                .map(pool::submit)
                .toList();

        startLine.countDown();
        pool.shutdown();
        assertEquals(true, pool.awaitTermination(10, TimeUnit.SECONDS),
                "contenders did not finish within the timeout");

        AtomicInteger successes = new AtomicInteger();
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successes.incrementAndGet();
            }
        }

        assertEquals(1, successes.get(),
                "expected exactly one booking to win the seat");
        assertEquals(1, repository.countBookings(TRIP_ID, SEAT_NO),
                "expected exactly one row in the database for the contended seat");
    }
}
