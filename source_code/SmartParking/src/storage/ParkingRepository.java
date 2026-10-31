package storage;

import service.ParkingLot;

import java.io.IOException;

public interface ParkingRepository {
    boolean hasSavedData();

    void load(ParkingLot lot) throws IOException;

    void save(ParkingLot lot) throws IOException;
}