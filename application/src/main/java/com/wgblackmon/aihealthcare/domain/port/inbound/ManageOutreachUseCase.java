package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;

import java.util.List;

/**
 * Inbound port for managing company outreach records and their associated contacts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public interface ManageOutreachUseCase {

    // --- Outreach ---

    CompanyOutreach addOutreach(String slug, OutreachPurpose purpose, String notes);

    CompanyOutreach updateOutreachStatus(Long id, OutreachStatus status);

    CompanyOutreach updateOutreachNotes(Long id, String notes);

    void deleteOutreach(Long id);

    List<CompanyOutreach> listAllOutreach();

    List<CompanyOutreach> listOutreachBySlug(String slug);

    // --- Contacts ---

    CompanyContact addContact(String slug, String fullName, String jobTitle,
                              String email, String linkedinUrl,
                              ContactSource source, String notes);

    CompanyContact updateContactStatus(Long contactId, ContactStatus status);

    CompanyContact updateContactEmail(Long contactId, String email);

    CompanyContact updateContactNotes(Long contactId, String notes);

    void deleteContact(Long contactId);

    List<CompanyContact> listContacts(String slug);
}
