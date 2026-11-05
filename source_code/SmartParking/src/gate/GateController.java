package gate;

import java.time.Duration;
import java.time.LocalDateTime;

public class GateController {
    private final String gateId;
    private final int autoCloseSeconds;
    private GateState state = GateState.CLOSED;
    private String openedFor;
    private LocalDateTime openedAt;

    public GateController(String gateId, int autoCloseSeconds) {
        if (gateId == null || gateId.isBlank()) {
            throw new IllegalArgumentException("A gate needs an id.");
        }
        if (autoCloseSeconds < 1 || autoCloseSeconds > 3600) {
            throw new IllegalArgumentException("The gate must close by itself after 1 to 3600 seconds.");
        }
        this.gateId = gateId;
        this.autoCloseSeconds = autoCloseSeconds;
    }

    public String getGateId() {
        return gateId;
    }

    public void open(String plateNumber) {
        open(plateNumber, LocalDateTime.now());
    }

    public void open(String plateNumber, LocalDateTime now) {
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new IllegalArgumentException("A gate opens for a specific vehicle.");
        }
        state = GateState.OPEN;
        openedFor = plateNumber;
        openedAt = now;
    }

    /** The sensor reports that the vehicle has driven through, so the barrier comes down. */
    public void vehiclePassed() {
        close();
    }

    public void close() {
        state = GateState.CLOSED;
        openedFor = null;
        openedAt = null;
    }

    public GateState getState() {
        return getState(LocalDateTime.now());
    }

    public GateState getState(LocalDateTime now) {
        if (state == GateState.OPEN && secondsOpen(now) >= autoCloseSeconds) {
            close();
        }
        return state;
    }

    public String getOpenedFor() {
        return getOpenedFor(LocalDateTime.now());
    }

    public String getOpenedFor(LocalDateTime now) {
        return getState(now) == GateState.OPEN ? openedFor : null;
    }

    public long getSecondsLeft() {
        return getSecondsLeft(LocalDateTime.now());
    }

    public long getSecondsLeft(LocalDateTime now) {
        if (getState(now) == GateState.CLOSED) return 0;
        return Math.max(0, autoCloseSeconds - secondsOpen(now));
    }

    private long secondsOpen(LocalDateTime now) {
        return Duration.between(openedAt, now).getSeconds();
    }
}