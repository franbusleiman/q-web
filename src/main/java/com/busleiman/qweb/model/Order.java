package com.busleiman.qweb.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("ORDERS")
public class Order implements Persistable {
    @Id
    private Long id;
    private String buyerDni;
    private String sellerDni;
    private  Long javaCoinsAmount;
    private Long javaCoinPrice;
    private Long usdAmount;
    private OrderState bankAccepted;
    private OrderState walletAccepted;
    private OrderState orderState;

    @Override
    public boolean isNew() {
        return bankAccepted == OrderState.IN_PROGRESS && walletAccepted ==OrderState.IN_PROGRESS;
    }
}
