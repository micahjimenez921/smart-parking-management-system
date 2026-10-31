package payment;

import exception.PaymentFailedException;

public class CashPayment implements PaymentMethod {
    private double tendered;

    public CashPayment(double tendered) {
        this.tendered = tendered;
    }

    @Override
    public void pay(double amount) throws PaymentFailedException {
        if (tendered < amount) {
            throw new PaymentFailedException(
                    "Cash is not enough. Amount due: " + String.format("%.2f", amount));
        }
    }

    @Override
    public double getChange(double amount) {
        return tendered - amount;
    }

    @Override
    public String getMethodName() { return "CASH"; }
}