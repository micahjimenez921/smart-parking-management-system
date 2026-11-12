package model;

import java.time.Duration;
import java.time.LocalDateTime;

/** A slot held for one plate number until a time limit. Nothing is charged for a reservation. */
public class Reservation {
    private final String id;
    private final String ownerId;
    private final String plateNumber;
    private final ParkingSlot slot;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    public Reservation(String id, String ownerId, String plateNumber, ParkingSlot slot,
                       LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.plateNumber = plateNumber;
        this.slot = slot;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }

    /** The id of the driver account that made the reservation. */
    public String getOwnerId() { return ownerId; }

    public String getPlateNumber() { return plateNumber; }

    public ParkingSlot getSlot() { return slot; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /** Whole minutes left, rounded up, never below zero. */
    public long minutesLeft(LocalDateTime now) {
        long seconds = Duration.between(now, expiresAt).getSeconds();
        return seconds <= 0 ? 0 : (seconds + 59) / 60;
    }
}
