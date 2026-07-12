package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;

public interface AuthenticatedStudentService {

    Student getOrCreateStudent() ;

    Student getRequiredStudent() ;

    Student getOrCreateStudentForUpdate() ;

    User getAuthenticatedUser() ;

    String getAuthenticatedEmail() ;
}
