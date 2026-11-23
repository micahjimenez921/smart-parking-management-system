package payment;

public class GCashPayment extends EWalletPayment {
    public GCashPayment(String mobileNumber) {
        super(mobileNumber);
    }

    @Override
    protected String walletLabel() { return "GCash"; }

    @Override
    public String getMethodName() { return "GCASH"; }
}
