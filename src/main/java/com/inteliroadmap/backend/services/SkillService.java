package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.CompareStRmSkillRequest;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.SkillMapper;
import com.inteliroadmap.backend.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SkillService {

    public SkillResponse getStudentSkills() ;

    public SkillResponse searchSkills(String search) ;

    public SkillResponse importStudentSkills(ImportSkillsRequest request) ;

    public SkillResponse compareWithStudentSkills(CompareStRmSkillRequest request) ;
}
