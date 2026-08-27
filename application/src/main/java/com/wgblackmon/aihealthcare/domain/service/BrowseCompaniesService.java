package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanySignal;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure domain service implementing {@link BrowseCompaniesUseCase}.
 *
 * <p>Delegates company data access to {@link HealthcareAiCompanyPort} and
 * signal computation to {@link CompanySignalService}. Contains no Spring or
 * Lombok dependencies — wired exclusively via {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-26
 * @updated 2026-08-27
 */
public class BrowseCompaniesService implements BrowseCompaniesUseCase {

    private final HealthcareAiCompanyPort companyPort;
    private final CompanySignalService signalService;

    public BrowseCompaniesService(HealthcareAiCompanyPort companyPort,
                                   CompanySignalService signalService) {
        this.companyPort = companyPort;
        this.signalService = signalService;
    }

    @Override
    public List<HealthcareAiCompany> listCompanies() {
        return companyPort.findAllByOrderByName();
    }

    @Override
    public Optional<HealthcareAiCompany> getCompany(String slug) {
        return companyPort.findBySlug(slug);
    }

    @Override
    public Map<String, CompanySignal> computeSignals(List<HealthcareAiCompany> companies) {
        return signalService.buildSignalMap(companies);
    }
}
