package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataJobStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.web.dto.DataFeedResponse;
import com.wgblackmon.aihealthcare.web.dto.DataJobResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

/**
 * Thymeleaf controller for the enterprise data console page.
 *
 * <p>Serves the main console page plus HTMX-compatible fragments for
 * live job table polling, log tailing, and artifact previews.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Controller
@RequestMapping("/enterprise/data")
public class EnterpriseDataConsoleController {

    private final RequestEnterpriseDataUseCase useCase;

    public EnterpriseDataConsoleController(RequestEnterpriseDataUseCase useCase) {
        log.debug("EnterpriseDataConsoleController() | useCase={}",
                useCase.getClass().getSimpleName());
        this.useCase = useCase;
    }

    @GetMapping
    public String console(Model model, Principal principal) {
        log.debug("console() | principal={}", principal.getName());

        List<DataFeedResponse> feeds = useCase.listFeeds(principal.getName())
                .stream().map(DataFeedResponse::from).toList();
        List<DataJobResponse> jobs = useCase.listJobs(principal.getName(), 0, 50)
                .stream().map(DataJobResponse::from).toList();

        model.addAttribute("feeds", feeds);
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasInFlightJobs", jobs.stream()
                .anyMatch(j -> "QUEUED".equals(j.status()) || "RUNNING".equals(j.status())));

        log.debug("console() | return=enterprise-data-console");
        return "enterprise-data-console";
    }

    @GetMapping("/jobs/rows")
    public String jobRows(Model model, Principal principal) {
        log.debug("jobRows() | principal={}", principal.getName());
        List<DataJobResponse> jobs = useCase.listJobs(principal.getName(), 0, 50)
                .stream().map(DataJobResponse::from).toList();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasInFlightJobs", jobs.stream()
                .anyMatch(j -> "QUEUED".equals(j.status()) || "RUNNING".equals(j.status())));
        log.debug("jobRows() | return=fragment enterprise-job-rows");
        return "fragments/enterprise-job-rows";
    }

    @GetMapping("/jobs/{jobId}/log-tail")
    public String logTail(@PathVariable String jobId,
                           @RequestParam(defaultValue = "0") long from,
                           Model model, Principal principal) {
        log.debug("logTail() | jobId={}, from={}, principal={}", jobId, from, principal.getName());
        try {
            String logContent = useCase.readLog(jobId, principal.getName(), from);
            model.addAttribute("logContent", logContent != null ? logContent : "");
            model.addAttribute("jobId", jobId);
            model.addAttribute("nextOffset", from + (logContent != null ? logContent.length() : 0));
        } catch (IllegalArgumentException e) {
            model.addAttribute("logContent", "Job not found.");
            model.addAttribute("jobId", jobId);
            model.addAttribute("nextOffset", from);
        }
        log.debug("logTail() | return=fragment enterprise-log-tail");
        return "fragments/enterprise-log-tail";
    }

    @GetMapping("/jobs/{jobId}/preview")
    public String preview(@PathVariable String jobId, Model model, Principal principal) {
        log.debug("preview() | jobId={}, principal={}", jobId, principal.getName());
        try {
            DataJob job = useCase.getJob(jobId, principal.getName());
            model.addAttribute("job", DataJobResponse.from(job));
            model.addAttribute("isSucceeded", job.status() == DataJobStatus.SUCCEEDED);
        } catch (IllegalArgumentException e) {
            model.addAttribute("job", null);
            model.addAttribute("isSucceeded", false);
        }
        log.debug("preview() | return=fragment enterprise-job-preview");
        return "fragments/enterprise-job-preview";
    }
}
