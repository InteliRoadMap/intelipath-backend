package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.DeclareCurriculumTermRequest;
import com.inteliroadmap.backend.domain.dto.request.SetStudentCurriculumRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateFptSubjectsRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.FptSubjectDetailResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentCurriculumResponse;

/**
 * Manages a student's declared FPT subjects and bridges them into the roadmap:
 * every PASSED subject seeds transcript evidence for the catalog skills it covers,
 * which the existing {@link RoadmapPersonalizationService} turns into "skip known
 * skills" recommendations. This is what makes the roadmap dynamic per student.
 */
public interface StudentCurriculumService {

    /** The full FPT-subject checklist annotated with the current student's declared status. */
    StudentCurriculumResponse getCurriculum();

    /**
     * Full syllabus detail for one subject: outcomes, references and the files we hold.
     *
     * Not scoped to the student's combo — the lookup page is for browsing what the school
     * teaches, and being an FPT account is the rule that matters.
     */
    FptSubjectDetailResponse getSubjectDetail(String subjectCode);

    /** Mark every subject up to the given term as PASSED, then re-sync evidence. */
    StudentCurriculumResponse applyCurriculumTerm(DeclareCurriculumTermRequest request);

    /** Apply manual tick/untick per subject, then re-sync evidence. */
    StudentCurriculumResponse updateSubjects(UpdateFptSubjectsRequest request);

    /** Override which curriculum version the student follows, then return the fresh checklist. */
    StudentCurriculumResponse setCurriculum(SetStudentCurriculumRequest request);
}
