package org.example.pongrankbackend.Match.rating;

import org.springframework.stereotype.Component;

@Component
public class Glicko2Calculator {

    private static final double SCALE = 173.7178;
    private static final double TAU = 0.5; // Constante del sistema para tenis de mesa
    private static final double EPSILON = 0.000001;

    /**
     * Calcula la actualización de Rating Glicko-2 para un jugador frente a un único rival.
     *
     * @param rating          Rating actual del jugador (ej. 1500.0)
     * @param ratingDeviation Desviación del rating actual (RD, ej. 350.0)
     * @param volatility      Volatilidad actual (sigma, ej. 0.06)
     * @param opponentRating  Rating del oponente
     * @param opponentRd      Desviación del oponente
     * @param score           Resultado: 1.0 (victoria) o 0.0 (derrota)
     * @return Glicko2Result con el nuevo rating, RD, volatilidad y delta
     */
    public Glicko2Result calculate(double rating, double ratingDeviation, double volatility,
                                   double opponentRating, double opponentRd, double score) {

        // Paso 2: Conversión a la escala Glicko-2
        double mu = (rating - 1500.0) / SCALE;
        double phi = ratingDeviation / SCALE;

        double muOpponent = (opponentRating - 1500.0) / SCALE;
        double phiOpponent = opponentRd / SCALE;

        // Paso 3: Función g(phi) y expectativa E(mu, mu_j, phi_j)
        double g = g(phiOpponent);
        double e = expectation(mu, muOpponent, g);

        // Paso 3 y 4: Varianza estimada v y delta
        double variance = 1.0 / (g * g * e * (1.0 - e));
        double delta = variance * g * (score - e);

        // Paso 5: Determinación de la nueva volatilidad sigma' usando el algoritmo de Illinois
        double newVolatility = calculateNewVolatility(phi, volatility, variance, delta);

        // Paso 6: Actualización de la desviación preliminar phi*
        double phiStar = Math.sqrt(phi * phi + newVolatility * newVolatility);

        // Paso 7: Actualización de phi' y mu'
        double newPhi = 1.0 / Math.sqrt((1.0 / (phiStar * phiStar)) + (1.0 / variance));
        double newMu = mu + (newPhi * newPhi) * g * (score - e);

        // Paso 8: Conversión de vuelta a la escala original Glicko
        double newRating = SCALE * newMu + 1500.0;
        double newRd = SCALE * newPhi;

        // Redondeo de precisión para legibilidad
        double roundedRating = Math.round(newRating * 10.0) / 10.0;
        double roundedRd = Math.round(newRd * 10.0) / 10.0;
        double roundedVol = Math.round(newVolatility * 10000.0) / 10000.0;
        double ratingDelta = Math.round((roundedRating - rating) * 10.0) / 10.0;

        return Glicko2Result.builder()
                .newRating(roundedRating)
                .newRatingDeviation(roundedRd)
                .newVolatility(roundedVol)
                .ratingDelta(ratingDelta)
                .build();
    }

    private double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + (3.0 * phi * phi) / (Math.PI * Math.PI));
    }

    private double expectation(double mu, double muOpponent, double gPhiOpponent) {
        return 1.0 / (1.0 + Math.exp(-gPhiOpponent * (mu - muOpponent)));
    }

    private double calculateNewVolatility(double phi, double sigma, double v, double delta) {
        double a = Math.log(sigma * sigma);
        double phiSq = phi * phi;
        double deltaSq = delta * delta;

        // Función f(x) definida en el paper de Glickman
        // f(x) = [e^x * (delta^2 - phi^2 - v - e^x) / (2 * (phi^2 + v + e^x)^2)] - [(x - a) / tau^2]
        java.util.function.DoubleUnaryOperator f = x -> {
            double ex = Math.exp(x);
            double numerator = ex * (deltaSq - phiSq - v - ex);
            double denominator = 2.0 * Math.pow(phiSq + v + ex, 2);
            return (numerator / denominator) - ((x - a) / (TAU * TAU));
        };

        // Límites iniciales A y B
        double A = a;
        double B;
        if (deltaSq > phiSq + v) {
            B = Math.log(deltaSq - phiSq - v);
        } else {
            double k = 1.0;
            while (f.applyAsDouble(a - k * TAU) < 0) {
                k += 1.0;
            }
            B = a - k * TAU;
        }

        double fA = f.applyAsDouble(A);
        double fB = f.applyAsDouble(B);

        // Iteración de Illinois para encontrar la raíz
        while (Math.abs(B - A) > EPSILON) {
            double C = A + (A - B) * fA / (fB - fA);
            double fC = f.applyAsDouble(C);

            if (fC * fB <= 0) {
                A = B;
                fA = fB;
            } else {
                fA = fA / 2.0;
            }
            B = C;
            fB = fC;
        }

        return Math.exp(A / 2.0);
    }
}
