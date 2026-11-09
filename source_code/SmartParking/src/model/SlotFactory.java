package model;

import java.util.List;

public class SlotFactory {
    public static final List<String> TYPES = List.of("REGULAR", "HANDICAPPED", "VIP");

    public static ParkingSlot create(String slotType, String slotId) {
        if ("REGULAR".equals(slotType)) return new RegularSlot(slotId);
        if ("HANDICAPPED".equals(slotType)) return new HandicappedSlot(slotId);
        if ("VIP".equals(slotType)) return new VIPSlot(slotId);
        throw new IllegalArgumentException("Choose a slot type.");
    }

    public static ParkingSlot create(String slotType, String slotId, double hourlyRate) {
        if ("REGULAR".equals(slotType)) return new RegularSlot(slotId, hourlyRate);
        if ("HANDICAPPED".equals(slotType)) return new HandicappedSlot(slotId, hourlyRate);
        if ("VIP".equals(slotType)) return new VIPSlot(slotId, hourlyRate);
        throw new IllegalArgumentException("Choose a slot type.");
    }

    /** The letter that starts the ids of this type, such as R for R-01. */
    public static String idPrefix(String slotType) {
        if ("REGULAR".equals(slotType)) return "R";
        if ("HANDICAPPED".equals(slotType)) return "H";
        if ("VIP".equals(slotType)) return "V";
        throw new IllegalArgumentException("Choose a slot type.");
    }
}