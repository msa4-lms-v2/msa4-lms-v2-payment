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
    void 새_서식에서도_문서확인번호와_원본_QR_링크를_유지한다() throws Exception {
        String token = "d9cb52ca-4f29-4ac8-b618-9222a9a23a79";
        String verifyUrl = "https://example.test/certificates/verify?token=" + token + "&qrHash=signature";
        byte[] bytes = new CertificatePdfGenerator().generate("재 학 증 명 서",
                List.of(java.util.Map.entry("성명", "홍길동"), java.util.Map.entry("학번", "20260001")),
                verifyUrl, LocalDateTime.of(2026, 9, 16, 0, 0));
        try (var document = PDDocument.load(bytes)) {
            assertThat(new PDFTextStripper().getText(document)).contains(
                    "미래대학교 총장", "위 사람은 본교에 재학 중임을 증명합니다.", token);
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("재 학 증 명 서");
            var page = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 144);
            // 인쇄 결과의 QR 영역을 읽어 실제 검증 주소가 그대로 유지되는지 확인한다.
            var qr = page.getSubimage(136, page.getHeight() - 328, 172, 172);
            var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                    new com.google.zxing.client.j2se.BufferedImageLuminanceSource(qr)));
            assertThat(new com.google.zxing.MultiFormatReader().decode(bitmap).getText()).isEqualTo(verifyUrl);
        }
    }

    @Test
    void 교과목과_성적의_줄바꿈은_같은_페이지에_묶는다() throws Exception {
        var rows = new java.util.ArrayList<java.util.Map.Entry<String,String>>();
        for (int i = 0; i < 23; i++) rows.add(java.util.Map.entry("항목", "내용"));
        rows.add(java.util.Map.entry("2025학년도 1학기", "자료구조와 알고리즘의 이론 및 응용 프로젝트 (CS101) / 3학점 / A+ / 평점 4.5"));
        byte[] bytes = new CertificatePdfGenerator().generate("성 적 증 명 서", rows,
                "https://example.test/verify", LocalDateTime.of(2026,9,16,0,0));
        try (var document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            var extractor = new PDFTextStripper();
            extractor.setStartPage(1); extractor.setEndPage(1);
            assertThat(extractor.getText(document)).doesNotContain("CS101");
            extractor.setStartPage(2); extractor.setEndPage(2);
            assertThat(extractor.getText(document)).contains("2025학년도 1학기", "CS101", "평점 4.5");
        }
    }

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
