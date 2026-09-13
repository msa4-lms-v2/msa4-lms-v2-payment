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
    @Test
    void 긴_강의명이_있는_대량_이력도_모두_출력한다() throws Exception {
        var rows = new java.util.ArrayList<java.util.Map.Entry<String,String>>();
        for (int i = 0; i < 80; i++) rows.add(java.util.Map.entry("강의 " + i, "매우 긴 강의명 ".repeat(8) + "끝" + i));
        byte[] bytes = new CertificatePdfGenerator().generate("강 의 경 력 증 명 서", rows,
                "https://example.com/verify", LocalDateTime.of(2026,9,13,0,0));
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(document)).contains("끝0", "끝79", "2026년 09월 13일");
        }
    }
}
