package com.msa4lmsv2payment.global.document;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfGeneratorTest {

    @Test
    void 한글_텍스트와_QR을_포함한_PDF를_생성한다() throws Exception {
        CertificatePdfGenerator generator = new CertificatePdfGenerator();

        byte[] pdfBytes = generator.generate(
                "재 학 증 명 서",
                List.of(new AbstractMap.SimpleEntry<>("성명", "김학생"), new AbstractMap.SimpleEntry<>("학번", "2024123456")),
                "http://localhost:5176/certificates/verify?token=abc&qrHash=def",
                LocalDateTime.of(2026, 9, 11, 0, 0)
        );

        assertThat(pdfBytes).isNotEmpty();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("재 학 증 명 서", "김학생", "2024123456", "2026년 09월 11일");
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }
}
