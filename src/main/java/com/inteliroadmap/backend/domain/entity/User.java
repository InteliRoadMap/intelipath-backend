package com.inteliroadmap.backend.domain.entity;

import jakarta.persistence.*;

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private int id;
    private String name;
    private String password;
    private String email;
}
