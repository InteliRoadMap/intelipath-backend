package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.utils.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Optional;

public interface AuthenticatedStudentService {

    public Student getOrCreateStudent() ;

    public Student getRequiredStudent() ;

    public Student getOrCreateStudentForUpdate() ;

    public User getAuthenticatedUser() ;
}
