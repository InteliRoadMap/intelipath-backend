package com.inteliroadmap.backend.services.impl;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubPortfolioSourceSamplerTest {
    @Test
    void samplesArchitecturalRolesWithoutDroppingUsefulCandidatesBelowTheBound() {
        assertTrue(GithubPortfolioServiceImpl.isProductionSource("src/controllers/UserController.java"));
        List<String> selected = GithubPortfolioServiceImpl.representativeSourcePaths(List.of(
                "src/services/UserService.java", "src/services/SkillService.java",
                "src/controllers/UserController.java", "src/domain/UserEntity.java",
                "src/repositories/UserRepository.java", "src/config/SecurityConfig.java"));
        assertEquals(6, selected.size());
        assertTrue(selected.stream().anyMatch(path -> path.contains("Controller")), selected.toString());
        assertTrue(selected.stream().anyMatch(path -> path.contains("Entity")));
        assertTrue(selected.stream().anyMatch(path -> path.contains("Repository")));
        assertTrue(selected.stream().anyMatch(path -> path.contains("Service")));
        assertTrue(selected.stream().anyMatch(path -> path.contains("Config")));
    }

    @Test
    void worksAcrossFrontendPythonDotnetGoAndRustNaming() {
        List<String> selected = GithubPortfolioServiceImpl.representativeSourcePaths(List.of(
                "src/pages/Dashboard.tsx", "src/hooks/useSession.ts", "app/db/models.py",
                "Api/Data/AppDbContext.cs", "internal/repository/user.go",
                "src/persistence/user.rs", "tests/dashboard.test.tsx", "vendor/generated.js"));
        assertEquals(7, selected.size());
        assertTrue(selected.stream().anyMatch(path -> path.contains("tests")), selected.toString());
        assertTrue(selected.stream().noneMatch(path -> path.contains("vendor")));

    }

    @Test
    void doesNotLetMainOrClientDisplaceHttpPersistenceAndTestEvidence() {
        List<String> selected = GithubPortfolioServiceImpl.representativeSourcePaths(List.of(
                "src/Application.java", "src/client/GithubApiClient.java",
                "src/controller/UserController.java", "src/domain/User.java",
                "src/repository/UserRepository.java", "src/service/UserService.java",
                "src/test/UserControllerTest.java", "src/security/JwtService.java",
                "src/config/SecurityConfig.java", "src/mapper/UserMapper.java"));

        assertTrue(selected.stream().anyMatch(path -> path.contains("Controller.java")), selected.toString());
        assertTrue(selected.stream().anyMatch(path -> path.contains("Repository.java")), selected.toString());
        assertTrue(selected.stream().anyMatch(path -> path.contains("ControllerTest.java")), selected.toString());
    }

    @Test
    void acceptsEverySourceFamilyOfferedByPickALanguage() {
        for (String path : List.of(
                "src/Main.java", "src/Main.kt", "src/Main.scala", "src/App.swift",
                "src/App.cs", "src/App.fs", "src/App.vb", "cmd/main.go", "src/main.rs",
                "app/main.py", "src/app.tsx", "src/app.js", "public/index.php",
                "app/main.rb", "src/player.gd", "src/main.c", "src/main.cpp")) {
            assertTrue(GithubPortfolioServiceImpl.isProductionSource(path), path);
        }
    }
}
