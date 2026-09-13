package com.msa4lmsv2payment.global.document;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 재학/졸업/재직 증명서와 등록금 납부확인서를 같은 레이아웃(제목 + 항목표 + 검증 QR)으로 생성한다.
// 항목 구성만 문서 종류별로 다르고, PDF 렌더링 로직 자체는 공유한다.
@Component
public class CertificatePdfGenerator {

    private static final DateTimeFormatter ISSUED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
    private static final float MARGIN_X = 60f;
    private static final float TITLE_Y = 760f;
    private static final float ROW_START_Y = 680f;
    private static final float ROW_HEIGHT = 28f;
    private static final float QR_SIZE = 110f;

    private final byte[] fontBytes;

    public CertificatePdfGenerator() {
        try (InputStream fontStream = new ClassPathResource("fonts/NotoSansKR.ttf").getInputStream()) {
            this.fontBytes = fontStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("증명서 PDF용 폰트를 불러올 수 없습니다.", e);
        }
    }

    public byte[] generate(String title, List<Map.Entry<String, String>> rows, String verifyUrl, LocalDateTime issuedAt) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes), true);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, 22);
                contentStream.newLineAtOffset(MARGIN_X, TITLE_Y);
                contentStream.showText(title);
                contentStream.endText();

                float y = ROW_START_Y;
                for (Map.Entry<String, String> row : rows) {
                    contentStream.beginText();
                    contentStream.setFont(font, 12);
                    contentStream.newLineAtOffset(MARGIN_X, y);
                    contentStream.showText(row.getKey());
                    contentStream.endText();

                    contentStream.beginText();
                    contentStream.setFont(font, 12);
                    contentStream.newLineAtOffset(MARGIN_X + 160, y);
                    contentStream.showText(row.getValue() == null ? "-" : row.getValue());
                    contentStream.endText();

                    y -= ROW_HEIGHT;
                }

                contentStream.beginText();
                contentStream.setFont(font, 11);
                contentStream.newLineAtOffset(MARGIN_X, y - 20);
                contentStream.showText("발급일: " + issuedAt.format(ISSUED_AT_FORMAT));
                contentStream.endText();

                PDImageXObject qrImage = LosslessFactory.createFromImage(document, renderQr(verifyUrl));
                contentStream.drawImage(qrImage, MARGIN_X, y - 20 - QR_SIZE, QR_SIZE, QR_SIZE);

                contentStream.beginText();
                contentStream.setFont(font, 9);
                contentStream.newLineAtOffset(MARGIN_X + QR_SIZE + 12, y - 20 - QR_SIZE / 2);
                contentStream.showText("QR 또는 아래 링크로 진위를 확인할 수 있습니다.");
                contentStream.endText();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("증명서 PDF 생성에 실패했습니다.", e);
        }
    }

    private BufferedImage renderQr(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception e) {
            throw new IllegalStateException("QR 코드 생성에 실패했습니다.", e);
        }
    }
}
