package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.roadmap.CareerResponse;

import java.util.List;
import java.util.UUID;

public interface CareerService {

    List<CareerResponse> getAllCareers() ;

    CareerResponse getCareerRequirements(UUID careerId) ;
}
