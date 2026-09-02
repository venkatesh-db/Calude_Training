package com.bus.booking;

import java.math.BigDecimal;

public record Booking(String tripId, int seatNo, String passengerName, BigDecimal fare) {
}
