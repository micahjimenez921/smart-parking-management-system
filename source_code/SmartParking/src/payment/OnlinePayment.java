package payment;

import exception.PaymentFailedException;

public class OnlinePayment implements PaymentMethod {
    private String mobileNumber;

    public OnlinePayment(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    @Override
    public void pay(double amount) throws PaymentFailedException {
        if (!mobileNumber.matches("\\d{11}")) {
            throw new PaymentFailedException("Mobile number must be 11 digits.");
        }
    }

    @Override
    public String getMethodName() { return "ONLINE"; }
}