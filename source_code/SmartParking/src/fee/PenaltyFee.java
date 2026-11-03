package fee;

import model.Ticket;

public class PenaltyFee implements FeeStrategy {
    private final FeeStrategy base;
    private final double penalty;

    public PenaltyFee(FeeStrategy base, double penalty) {
        if (base == null) {
            throw new IllegalArgumentException("A base fee strategy is required.");
        }
        if (Double.isNaN(penalty) || Double.isInfinite(penalty) || penalty < 0) {
            throw new IllegalArgumentException("The penalty must be zero or more.");
        }
        this.base = base;
        this.penalty = penalty;
    }

    public double getPenalty() {
        return penalty;
    }

    @Override
    public double calculateFee(Ticket ticket) {
        return base.calculateFee(ticket) + penalty;
    }

    @Override
    public String getDescription() {
        return base.getDescription() + ", plus a flat penalty of " + String.format("%.2f", penalty);
    }
}