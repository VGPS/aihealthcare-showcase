package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageOutreachUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyContactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyOutreachPort;

import java.time.Instant;
import java.util.List;

/**
 * Domain service implementing company outreach and contact management.
 *
 * <p>Outreach records are keyed by (slug, purpose) — one per company per purpose.
 * Contacts are keyed by company slug and shared across both purposes.
 * Status transitions are unrestricted; the UI enforces sensible progressions.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public class OutreachService implements ManageOutreachUseCase {

    private final CompanyOutreachPort outreachPort;
    private final CompanyContactPort  contactPort;

    public OutreachService(CompanyOutreachPort outreachPort, CompanyContactPort contactPort) {
        this.outreachPort = outreachPort;
        this.contactPort  = contactPort;
    }

    // --- Outreach ---

    @Override
    public CompanyOutreach addOutreach(String slug, OutreachPurpose purpose, String notes) {
        Instant now = Instant.now();
        CompanyOutreach outreach = new CompanyOutreach(null, slug, purpose,
                OutreachStatus.NOT_STARTED, null, notes, now, now);
        return outreachPort.save(outreach);
    }

    @Override
    public CompanyOutreach updateOutreachStatus(Long id, OutreachStatus status) {
        CompanyOutreach existing = outreachPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outreach record not found: " + id));
        Instant contactedAt = (status != OutreachStatus.NOT_STARTED && existing.contactedAt() == null)
                ? Instant.now() : existing.contactedAt();
        CompanyOutreach updated = new CompanyOutreach(
                existing.id(), existing.slug(), existing.purpose(),
                status, contactedAt, existing.notes(),
                existing.createdAt(), Instant.now());
        return outreachPort.save(updated);
    }

    @Override
    public CompanyOutreach updateOutreachNotes(Long id, String notes) {
        CompanyOutreach existing = outreachPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outreach record not found: " + id));
        CompanyOutreach updated = new CompanyOutreach(
                existing.id(), existing.slug(), existing.purpose(),
                existing.status(), existing.contactedAt(), notes,
                existing.createdAt(), Instant.now());
        return outreachPort.save(updated);
    }

    @Override
    public void deleteOutreach(Long id) {
        outreachPort.deleteById(id);
    }

    @Override
    public List<CompanyOutreach> listAllOutreach() {
        return outreachPort.findAll();
    }

    @Override
    public List<CompanyOutreach> listOutreachBySlug(String slug) {
        return outreachPort.findBySlug(slug);
    }

    // --- Contacts ---

    @Override
    public CompanyContact addContact(String slug, String fullName, String jobTitle,
                                     String email, String linkedinUrl,
                                     ContactSource source, String notes) {
        Instant now = Instant.now();
        CompanyContact contact = new CompanyContact(null, slug, fullName, jobTitle,
                email, linkedinUrl, source, ContactStatus.IDENTIFIED, notes, now, now);
        return contactPort.save(contact);
    }

    @Override
    public CompanyContact updateContactStatus(Long contactId, ContactStatus status) {
        CompanyContact existing = contactPort.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        CompanyContact updated = new CompanyContact(
                existing.id(), existing.slug(), existing.fullName(), existing.jobTitle(),
                existing.email(), existing.linkedinUrl(), existing.source(),
                status, existing.notes(), existing.createdAt(), Instant.now());
        return contactPort.save(updated);
    }

    @Override
    public CompanyContact updateContactEmail(Long contactId, String email) {
        CompanyContact existing = contactPort.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        CompanyContact updated = new CompanyContact(
                existing.id(), existing.slug(), existing.fullName(), existing.jobTitle(),
                email, existing.linkedinUrl(), existing.source(),
                existing.status(), existing.notes(), existing.createdAt(), Instant.now());
        return contactPort.save(updated);
    }

    @Override
    public CompanyContact updateContactNotes(Long contactId, String notes) {
        CompanyContact existing = contactPort.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        CompanyContact updated = new CompanyContact(
                existing.id(), existing.slug(), existing.fullName(), existing.jobTitle(),
                existing.email(), existing.linkedinUrl(), existing.source(),
                existing.status(), notes, existing.createdAt(), Instant.now());
        return contactPort.save(updated);
    }

    @Override
    public void deleteContact(Long contactId) {
        contactPort.deleteById(contactId);
    }

    @Override
    public List<CompanyContact> listContacts(String slug) {
        return contactPort.findBySlug(slug);
    }
}
