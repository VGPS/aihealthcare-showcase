package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;

import java.util.List;
import java.util.Optional;

/**
 * Pure domain service implementing {@link BrowseCompaniesUseCase}.
 *
 * <p>Delegates all data access to {@link HealthcareAiCompanyPort}.
 * Contains no Spring or Lombok dependencies — wired exclusively via
 * {@code AppConfig} to keep the domain layer framework-free.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-26
 * @updated 2026-08-26
 */
public class BrowseCompaniesService implements BrowseCompaniesUseCase {

    private final HealthcareAiCompanyPort companyPort;

    public BrowseCompaniesService(HealthcareAiCompanyPort companyPort) {
        this.companyPort = companyPort;
    }

    @Override
    public List<HealthcareAiCompany> listCompanies() {
        return companyPort.findAllByOrderByName();
    }

    @Override
    public Optional<HealthcareAiCompany> getCompany(String slug) {
        return companyPort.findBySlug(slug);
    }
}
