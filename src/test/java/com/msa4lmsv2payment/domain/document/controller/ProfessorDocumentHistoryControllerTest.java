package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.global.client.ProfessorCertificateClient;
import com.msa4lmsv2payment.global.client.ProfessorCertificateEligibilityResponse;
import com.msa4lmsv2payment.global.client.ProfessorCareerResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfessorDocumentHistoryControllerTest {
    @Test void historyUsesAuthenticatedProfessorAndDisablesRevokedDownloads() {
        var repository = mock(DocumentRepository.class);
        var client = mock(ProfessorCertificateClient.class);
        var user = new CurrentUser(7L, "PROFESSOR");
        when(client.fetch(user)).thenReturn(new ProfessorCareerResponse(7L,
                new ProfessorCertificateEligibilityResponse(8L, "P-test", "테스트", "학과", null, (short) 2020, "ACTIVE"), List.of()));
        var issued = new Document(null, 8L, DocumentType.CAREER, "test-token", "test-hash", "test.pdf");
        var revoked = new Document(null, 8L, DocumentType.CAREER, "test-token2", "test-hash2", "revoked.pdf");
        revoked.revoke();
        when(repository.findByProfessorIdOrderByIssuedAtDescIdDesc(8L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(issued, revoked)));
        var data = new ProfessorDocumentHistoryController(repository, client).history(user, 1, 10).data();
        assertEquals(2, data.items().size());
        assertTrue(data.items().get(0).downloadable());
        assertFalse(data.items().get(1).downloadable());
        assertTrue(data.items().get(1).revoked());
        verify(repository).findByProfessorIdOrderByIssuedAtDescIdDesc(8L, PageRequest.of(0, 10));
    }
}
