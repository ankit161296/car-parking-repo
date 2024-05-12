package com.example.carparking.service;


import com.example.carparking.dto.CarParkAvailabilityDto;
import com.example.carparking.dto.CarParkInfoDto;
import com.example.carparking.dto.CarParksResponseDto;
import com.example.carparking.entity.CarParkAvailability;
import com.example.carparking.entity.CarParkDetails;
import com.example.carparking.entity.CarParkingInfo;
import com.example.carparking.repository.CarParkDetailsRepo;
import com.example.carparking.repository.CarParkingAvailabilityRepo;
import com.example.carparking.repository.CarParkingInfoRepo;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CarParkingServiceImpl implements CarParkingService{


    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CarParkingAvailabilityRepo carParkingAvailabilityRepo;
    @Autowired
    private CarParkingInfoRepo carParkingInfoRepo;
    @Autowired
    private CarParkDetailsRepo carParkDetailsRepo;

    @Override
    public void saveCarParkAvailabilityToDB() {
        CarParkAvailabilityDto dataMap= restTemplate.
                getForEntity("https://api.data.gov.sg/v1/transport/carpark-availability", CarParkAvailabilityDto.class)
                .getBody();

        List<CarParkAvailabilityDto.AvailabilityItems> items = dataMap.getItems();
        List<CarParkingInfo> carParkingInfos = new ArrayList<>();
        List<CarParkAvailability> carParkAvailabilities = new ArrayList<>();

        if(Objects.nonNull(items)){
           for(CarParkAvailabilityDto.AvailabilityItems item: items){
               for(CarParkAvailabilityDto.CarparkData carparkData : item.getCarparkData()){
                   carParkingInfos.add(CarParkingInfo.builder()
                           .updateDatetime(carparkData.getUpdateDatetime())
                           .carparkNumber(carparkData.getCarparkNumber())
                           .build());
                   for(CarParkInfoDto carParkInfoDto : carparkData.getCarparkInfo()){
                       carParkAvailabilities.add(CarParkAvailability.builder()
                               .lotsAvailable(
                                       carParkInfoDto.getLotsAvailable())
                                       .carparkNumber(carparkData.getCarparkNumber())
                                       .lotType(carParkInfoDto.getLotType())
                                       .totalLots(carParkInfoDto.getTotalLots())
                               .build());
                   }
               }
            }
        }
        carParkingInfoRepo.saveAll(carParkingInfos);
        carParkingAvailabilityRepo.saveAll(carParkAvailabilities);
    }

    @Override
    public void saveCarParkInfoToDB() {
        List<CarParkDetails> dtos = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader("/Users/aayush/Downloads/Car Parking/src/HDBCarparkInformation.csv"))) {
            String[] line;
            reader.skip(1);
            int dataLimit = 1000;
            while ((line = reader.readNext()) != null && dataLimit-- > 0) {
                CarParkDetails dto = new CarParkDetails();
                dto.setCarParkNo(line[0]);
                dto.setAddress(line[1]);
                Map<String,Double> latLongMap = getLatLong(String.valueOf(line[2]),String.valueOf(line[3]));
                dto.setLatitude(latLongMap.get("latitude"));
                dto.setLongitude(latLongMap.get("longitude"));

                dtos.add(dto);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        carParkDetailsRepo.saveAll(dtos);
    }

    private Map<String,Double> getLatLong(String x, String y){
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization","Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MmI5NjJiZWY5OWE1M2JjOWNjZGE2MWYxMjlkYTg4MiIsImlzcyI6Imh0dHA6Ly9pbnRlcm5hbC1hbGItb20tcHJkZXppdC1pdC0xMjIzNjk4OTkyLmFwLXNvdXRoZWFzdC0xLmVsYi5hbWF6b25hd3MuY29tL2FwaS92Mi91c2VyL3Bhc3N3b3JkIiwiaWF0IjoxNzE1NDI1MzkzLCJleHAiOjE3MTU2ODQ1OTMsIm5iZiI6MTcxNTQyNTM5MywianRpIjoid2RQdXBEWlJiMDNVc0lTbiIsInVzZXJfaWQiOjM0NjMsImZvcmV2ZXIiOmZhbHNlfQ.9cNwvI3HmolxhGDo4Bff4HqKwYxH-esSwYeqqAyJvXE");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("https://www.onemap.gov.sg/api/common/convert/3414to4326?X=").append(x).append("&Y=").append(y);
        Map<String,Double> map= restTemplate.exchange(stringBuilder.toString(), HttpMethod.GET, new HttpEntity<>(null,headers), Map.class).getBody();
        return map;
    }

    @Override
    public List<CarParksResponseDto> getNearestCarParks(double latitude, double longitude, int page, int size) {
        List<CarParkDetails> carParkDetails = carParkDetailsRepo.findAll();
        CarParkDetails target = new CarParkDetails();
        target.setLatitude(latitude);
        target.setLongitude(longitude);

        carParkDetails.sort(Comparator.comparingDouble(source -> source.distanceTo(target)));
        // add in response list if availability is greater than zero
        List<CarParksResponseDto> carParksResponseDtos = new ArrayList<>();
        for(CarParkDetails details : carParkDetails){
            List<CarParkAvailability> carParkAvailabilities = carParkingAvailabilityRepo.findByCarparkNumber(details.getCarParkNo());
            if(Objects.nonNull(carParkAvailabilities))
             carParkAvailabilities=carParkAvailabilities.stream().filter(
                    carParkAvailability -> !carParkAvailability.getLotsAvailable().equals("0")).collect(Collectors.toList());

            if(!carParkAvailabilities.isEmpty()){
                CarParksResponseDto carParksResponseDto = new CarParksResponseDto();
                carParksResponseDto.setTotalLots(carParkAvailabilities.get(0).getTotalLots());
                carParksResponseDto.setAvailableLots(carParkAvailabilities.get(0).getLotsAvailable());
                carParksResponseDto.setLatitude(details.getLatitude());
                carParksResponseDto.setLongitude(details.getLongitude());
                carParksResponseDto.setAddress(details.getAddress());
                carParksResponseDtos.add(carParksResponseDto);
            }
        }
        int totalCount = carParksResponseDtos.size();
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, totalCount);
        return carParksResponseDtos.subList(startIndex, endIndex);
    }

}
