package com.smartedtech.ai;

import com.smartedtech.assessment.GenerationJob;
import com.smartedtech.assessment.GenerationJobService;
import com.smartedtech.assessment.dto.GenerateQuestionsRequest;
import com.smartedtech.assessment.dto.GenerationJobDto;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final GenerationJobService jobService;

    public AiController(GenerationJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/generate-questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> generateQuestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GenerateQuestionsRequest request) {

        GenerationJob job = jobService.createQuestionGenerationJob(principal.getUser(), request);
        jobService.executeQuestionGenerationAsync(job.getId(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus(),
                "jobType", job.getJobType()
        ));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<GenerationJobDto> getJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {

        GenerationJobDto dto = jobService.getJobDto(id, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/jobs/{id}/retry")
    public ResponseEntity<GenerationJobDto> retryJob(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {

        GenerationJob job = jobService.retryJob(id, principal.getUser());
        return ResponseEntity.ok(jobService.getJobDto(job.getId(), principal.getUser()));
    }
}
