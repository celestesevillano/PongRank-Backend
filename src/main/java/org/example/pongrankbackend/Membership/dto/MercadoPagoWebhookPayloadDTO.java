package org.example.pongrankbackend.Membership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Forma del payload que manda MercadoPago al webhook (Checkout Pro), no la inventamos nosotros:
// { "type": "payment", "data": { "id": "123456789" }, ... otros campos que no nos interesan }
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoWebhookPayloadDTO {

    private String type;
    private DataDTO data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataDTO {
        private String id;
    }
}
