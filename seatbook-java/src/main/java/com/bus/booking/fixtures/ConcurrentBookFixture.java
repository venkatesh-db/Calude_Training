package com.bus.booking.fixtures;

import com.bus.booking.SeatBookingService;
import com.bus.booking.SeatUnavailableException;
import com.bus.booking.db.BookingRepository;
import com.bus.booking.db.Database;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproduces the seat-sold-twice defect: N passengers race to book the same
 * seat on the same trip. A correct implementation lets exactly one succeed;
 * the rest should see SeatUnavailableException. This fixture fails loudly
 * (non-zero exit) when more than one booking lands for the seat.
 *
 * Run with:
 *   mvn -q compile exec:java -Dexec.mainClass="com.bus.booking.fixtures.ConcurrentBookFixture"
 */
public final class ConcurrentBookFixture {

    private static final String TRIP_ID = "BLR-CHN-0901";
    private static final int SEAT_NO = 14;
    private static final int CONTENDERS = 4;

    public static void main(String[] args) throws InterruptedException {
        Database.reset();
        BookingRepository repository = new BookingRepository(Database.dataSource());
        SeatBookingService service = new SeatBookingService(repository);

        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        for (int i = 1; i <= CONTENDERS; i++) {
            String passenger = "Passenger-" + i;
            pool.submit(() -> {
                try {
                    startLine.await();
                    service.bookSeat(TRIP_ID, SEAT_NO, passenger, false);
                    successes.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    rejections.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLine.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        int actualRows = repository.countBookings(TRIP_ID, SEAT_NO);

        System.out.println("Contenders:        " + CONTENDERS);
        System.out.println("Reported successes: " + successes.get());
        System.out.println("Reported rejections:" + rejections.get());
        System.out.println("Rows in DB for seat: " + actualRows);

        if (actualRows > 1) {
            System.out.println("FAIL - seat " + SEAT_NO + " sold " + actualRows + " times");
            System.exit(1);
        }

        System.out.println("PASS - seat " + SEAT_NO + " sold exactly once");
    }
}
