package com.travel.planner.planning.dto;

import com.travel.planner.planning.entity.Place;
import com.travel.planner.planning.entity.PlaceProvider;
import java.math.BigDecimal;

public record PlaceResponse(
        Long id,
        PlaceProvider provider,
        String name,
        String category,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static PlaceResponse from(Place p) {
        if (p == null) {
            return null;
        }
        return new PlaceResponse(p.getId(), p.getProvider(), p.getName(),
                p.getCategory(), p.getAddress(), p.getLatitude(), p.getLongitude());
    }
}
