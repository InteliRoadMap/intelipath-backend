package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupUserProfileRequest;
import com.inteliroadmap.backend.domain.dto.response.StudentResponse;
import com.inteliroadmap.backend.domain.dto.response.UserResponse;
import com.inteliroadmap.backend.services.StudentService;
import com.inteliroadmap.backend.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student services", description = "Student endpoints")
public class StudentController {

    private final StudentService studentService;

    @PatchMapping("/profile")
    @Operation(
            summary = "Setup student profile",
            description = "User setup student profile"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Setup profile successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<UserResponse> setupStudentProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Student Profile payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetupUserProfileRequest.class)
                    )
            )
            @RequestBody @Valid SetupStudentProfileRequest setupStudentProfileRequest
    ) {
        log.info("Student profile setup successfully");
        return ResponseEntity.ok(studentService.setupStudentProfile(setupStudentProfileRequest));
    }

//    @GetMapping("/{student_id}/skills")
//    @Operation(
//            summary = "Get student skills",
//            description = "Get student skills"
//    )
//    @ApiResponses(value = {
//            @ApiResponse(
//                    responseCode = "200",
//                    description = "Setup profile successful",
//                    content = @Content(
//                            mediaType = "application/json",
//                            schema = @Schema(implementation = StudentResponse.class)
//                    )
//            ),
//            @ApiResponse(
//                    responseCode = "404",
//                    description = "Student skills not found"
//            )
//    })
//    public ResponseEntity<StudentResponse> getStudentSkills(@PathVariable String student_id) {
//        log.info("Student skills fetched successfully");
//        return ResponseEntity.ok(studentService.getStudentSkills(student_id));
//    }
}
