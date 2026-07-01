package com.hoanghnt.swiftpay.payment;

import java.math.BigDecimal;

import com.hoanghnt.swiftpay.entity.User;

public interface PaymentGatewayPort {

    TopupInitResult initiate(User user, BigDecimal amount);

    TopupConfirmResult confirm(String txnRef);

    TopupConfirmResult cancel(String txnRef);
}
