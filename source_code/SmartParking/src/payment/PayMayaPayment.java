package payment;

public class PayMayaPayment extends EWalletPayment {
    public PayMayaPayment(String mobileNumber) {
        super(mobileNumber);
    }

    @Override
    protected String walletLabel() { return "PayMaya"; }

    @Override
    public String getMethodName() { return "PAYMAYA"; }
}
