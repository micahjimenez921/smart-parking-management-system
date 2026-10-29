package payment;

import exception.PaymentFailedException;

public class CardPayment implements PaymentMethod {
    private String cardNumber;

    public CardPayment(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    @Override
    public void pay(double amount) throws PaymentFailedException {
        if (!cardNumber.matches("\\d{16}")) {
            throw new PaymentFailedException("Card number must be 16 digits.");
        }
    }

    @Override
    public String getMethodName() { return "CARD"; }
}