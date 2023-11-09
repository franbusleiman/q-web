package com.busleiman.qweb.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
public class WalletConfirmation {
    private Long id;
    private String orderState;

    //a setear una vez consumido
    private String sellerDni;
}
