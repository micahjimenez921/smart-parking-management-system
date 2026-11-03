package fee;

import model.Ticket;

public interface FeeStrategy {
    double calculateFee(Ticket ticket);

    String getDescription();
}
