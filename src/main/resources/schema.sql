CREATE TABLE ORDERS (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    SELLER_DNI VARCHAR(255),
    BUYER_DNI VARCHAR(255),
    JAVA_COINS_AMOUNT DOUBLE,
    USD_AMOUNT DOUBLE,
    JAVA_COIN_PRICE DOUBLE,
    BANK_ACCEPTED VARCHAR(255),
    WALLET_ACCEPTED VARCHAR(255),
    ORDER_STATE VARCHAR(255),
    ERROR_DESCRIPTION VARCHAR(255)

);