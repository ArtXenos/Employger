package com.employger.controller;

import com.employger.dto.ChoiceNameRequest;
import com.employger.dto.DepartmentResponse;
import com.employger.dto.DepartmentResponse.JobTitleResponse;
import com.employger.service.choice.ChoiceListService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The Department and Job Title choice lists. Everyone who can see the
 * employee records can read them; only administrators and HR managers can
 * change them (SecurityConfig). Errors (400 invalid input, 404 not found,
 * 409 conflict) are turned into plain-text responses by ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api")
public class ChoiceListController {

    private final ChoiceListService choiceListService;

    public ChoiceListController(ChoiceListService choiceListService) {
        this.choiceListService = choiceListService;
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> getDepartments() {
        return choiceListService.list();
    }

    @PostMapping("/departments")
    public DepartmentResponse addDepartment(@Valid @RequestBody ChoiceNameRequest request) {
        return DepartmentResponse.from(choiceListService.addDepartment(request.name()), List.of());
    }

    @PutMapping("/departments/{id}")
    public void renameDepartment(@PathVariable Integer id, @Valid @RequestBody ChoiceNameRequest request) {
        choiceListService.renameDepartment(id, request.name());
    }

    @PostMapping("/departments/{id}/retire")
    public void retireDepartment(@PathVariable Integer id) {
        choiceListService.setDepartmentActive(id, false);
    }

    @PostMapping("/departments/{id}/restore")
    public void restoreDepartment(@PathVariable Integer id) {
        choiceListService.setDepartmentActive(id, true);
    }

    @PostMapping("/departments/{id}/job-titles")
    public JobTitleResponse addJobTitle(@PathVariable Integer id, @Valid @RequestBody ChoiceNameRequest request) {
        return JobTitleResponse.from(choiceListService.addJobTitle(id, request.name()));
    }

    @PutMapping("/job-titles/{id}")
    public void renameJobTitle(@PathVariable Integer id, @Valid @RequestBody ChoiceNameRequest request) {
        choiceListService.renameJobTitle(id, request.name());
    }

    @PostMapping("/job-titles/{id}/retire")
    public void retireJobTitle(@PathVariable Integer id) {
        choiceListService.setJobTitleActive(id, false);
    }

    @PostMapping("/job-titles/{id}/restore")
    public void restoreJobTitle(@PathVariable Integer id) {
        choiceListService.setJobTitleActive(id, true);
    }
}
