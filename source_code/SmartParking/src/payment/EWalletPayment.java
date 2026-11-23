package payment;

import exception.PaymentFailedException;

/**
 * Base class for mobile wallets such as GCash and PayMaya. Both are paid with a
 * Philippine mobile number, so the check lives here once and each wallet only
 * supplies its own name.
 */
public abstract class EWalletPayment implements PaymentMethod {
    private final String mobileNumber;

    protected EWalletPayment(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    @Override
    public void pay(double amount) throws PaymentFailedException {
        if (!mobileNumber.matches("09\\d{9}")) {
            throw new PaymentFailedException(
                    "Enter the " + walletLabel() + " mobile number: 11 digits starting with 09.");
        }
    }

    /** The name shown to people, for example "GCash". */
    protected abstract String walletLabel();
}
