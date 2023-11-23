package com.busleiman.qweb.model;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("ORDERS")
public class Order {
    @Id
    private Long id;
    private String buyerDni;
    private String sellerDni;
    private Double javaCoinPrice;
    private Double usdAmount;
    private Double javaCoinsAmount;
    private OrderState bankAccepted;
    private OrderState walletAccepted;
    private OrderState orderState;
    private String errorDescription;

    public void calculateJavaCoinsAmount(){
        this.javaCoinsAmount =  this.usdAmount * this.javaCoinPrice;
    }
}
