package payment;

import exception.PaymentFailedException;

public interface PaymentMethod {
    void pay(double amount) throws PaymentFailedException;

    String getMethodName();
}