package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.service.DocumentService;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentDocumentHistoryControllerTest {
    @Test void 인증된_학생의_내역만_페이지로_반환한다() {
        var repository = mock(DocumentRepository.class);
        var tuition = mock(TuitionBillService.class);
        var user = new CurrentUser(7L, "STUDENT");
        when(tuition.resolveStudentId(user)).thenReturn(8L);
        var revoked = new Document(8L, null, DocumentType.ENROLLMENT, "token", "hash", "test.pdf");
        revoked.revoke();
        var pageable = PageRequest.of(1, 10);
        when(repository.findByStudentIdOrderByIssuedAtDescIdDesc(8L, pageable))
                .thenReturn(new PageImpl<>(List.of(revoked), pageable, 11));
        var result = new StudentDocumentHistoryController(repository, tuition).history(user, 2, 10).data();
        assertEquals(11, result.totalCount());
        assertEquals(2, result.page());
        assertFalse(result.items().getFirst().downloadable());
        assertFalse(result.hasNext());
        verify(repository).findByStudentIdOrderByIssuedAtDescIdDesc(8L, pageable);
    }

    @Test void PDF는_리다이렉트_없이_캐시금지_첨부파일로_반환한다() {
        var service = mock(DocumentService.class);
        var user = new CurrentUser(7L, "STUDENT");
        byte[] pdf = "%PDF-fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(service.downloadCertificate(user, 1L)).thenReturn(pdf);
        var response = new DocumentController(service).certificateContent(user, 1L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
        assertNull(response.getHeaders().getLocation());
        assertArrayEquals(pdf, response.getBody());
    }
}
