package com.busleiman.qweb.dto;

import com.busleiman.qweb.model.OrderState;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
public class WalletConfirmation {
    private Long id;
    private OrderState orderState;

    //a setear una vez consumido
    private String sellerDni;
    private String errorDescription;
}

