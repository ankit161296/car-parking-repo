package com.example.carparking.repository;

import com.example.carparking.entity.CarParkDetails;
import com.example.carparking.entity.CarParkingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarParkDetailsRepo extends JpaRepository<CarParkDetails, Long> {

}
