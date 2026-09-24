package org.example.pongrankbackend.Match.rating;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Glicko2Result {

    private double newRating;
    private double newRatingDeviation;
    private double newVolatility;
    private double ratingDelta;
}
