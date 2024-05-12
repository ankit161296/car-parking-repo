package com.example.carparking.repository;

import com.example.carparking.entity.CarParkAvailability;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface CarParkingAvailabilityRepo extends JpaRepository<CarParkAvailability, Long> {
    List<CarParkAvailability> findByCarparkNumber(String carparkNumber);
}
