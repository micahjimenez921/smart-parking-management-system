package payment;

public class PaymentFactory {
    public static PaymentMethod create(String method, String detail) {
        String text = (detail == null) ? "" : detail.trim();

        if ("CASH".equals(method)) {
            double tendered;
            try {
                tendered = Double.parseDouble(text.replace(",", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Enter the cash amount as a number.");
            }
            if (Double.isNaN(tendered) || Double.isInfinite(tendered) || tendered < 0) {
                throw new IllegalArgumentException("Enter a valid cash amount.");
            }
            return new CashPayment(tendered);
        }
        if ("CARD".equals(method)) {
            return new CardPayment(text.replaceAll("[\\s-]", ""));
        }
        if ("GCASH".equals(method)) {
            return new GCashPayment(text.replaceAll("[\\s-]", ""));
        }
        if ("PAYMAYA".equals(method)) {
            return new PayMayaPayment(text.replaceAll("[\\s-]", ""));
        }
        // kept so payments saved before GCash and PayMaya were added can still be read
        if ("ONLINE".equals(method)) {
            return new OnlinePayment(text.replaceAll("[\\s-]", ""));
        }
        throw new IllegalArgumentException("Choose a payment method.");
    }
}