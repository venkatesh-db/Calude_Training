package com.bus.booking;

public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String tripId, int seatNo) {
        super("Seat " + seatNo + " on trip " + tripId + " is already booked");
    }
}
