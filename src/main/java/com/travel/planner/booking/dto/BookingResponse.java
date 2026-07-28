package com.travel.planner.booking.dto;

import com.travel.planner.booking.entity.BookingType;
import com.travel.planner.booking.entity.TripBooking;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        BookingType type,
        String title,
        BigDecimal price,
        String bookingUrl,
        String imageUrl,
        String ticketUrl,
        LocalDate startDate,
        LocalDate endDate,
        String memo
) {
    public static BookingResponse from(TripBooking b) {
        return new BookingResponse(b.getId(), b.getType(), b.getTitle(), b.getPrice(),
                b.getBookingUrl(), b.getImageUrl(), b.getTicketUrl(),
                b.getStartDate(), b.getEndDate(), b.getMemo());
    }
}
