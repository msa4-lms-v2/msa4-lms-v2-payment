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
            PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes), true);
            var lines = new java.util.ArrayList<Map.Entry<String, String>>();
            for (var row : rows) {
                var keys = wrap(font, row.getKey(), 145f);
                var values = wrap(font, row.getValue(), 310f);
                for (int i = 0; i < Math.max(keys.size(), values.size()); i++)
                    lines.add(Map.entry(i < keys.size() ? keys.get(i) : "", i < values.size() ? values.get(i) : ""));
            }
            int pageCount = Math.max(1, (lines.size() + 23) / 24);
            PDImageXObject qrImage = LosslessFactory.createFromImage(document, renderQr(verifyUrl));
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    text(stream, font, 22, MARGIN_X, TITLE_Y, title);
                    float y = ROW_START_Y;
                    for (int i = pageIndex * 24; i < Math.min(lines.size(), (pageIndex + 1) * 24); i++) {
                        text(stream, font, 12, MARGIN_X, y, lines.get(i).getKey());
                        text(stream, font, 12, MARGIN_X + 160, y, lines.get(i).getValue());
                        y -= 18;
                    }
                    text(stream, font, 11, MARGIN_X, 195, "발급일: " + issuedAt.format(ISSUED_AT_FORMAT));
                    stream.drawImage(qrImage, MARGIN_X, 65, QR_SIZE, QR_SIZE);
                    text(stream, font, 9, MARGIN_X + QR_SIZE + 12, 120, "QR로 증명서의 진위를 확인할 수 있습니다.");
                    text(stream, font, 9, 490, 40, (pageIndex + 1) + " / " + pageCount);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("증명서 PDF 생성에 실패했습니다.", e);
        }
    }

    private void text(PDPageContentStream stream, PDType0Font font, int size, float x, float y, String value) throws IOException {
        stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, y);
        stream.showText(value); stream.endText();
    }

    private List<String> wrap(PDType0Font font, String value, float width) throws IOException {
        var lines = new java.util.ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (int cp : (value == null ? "-" : value).codePoints().toArray()) {
            String character = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
            if (!line.isEmpty() && font.getStringWidth(line.toString() + character) / 1000 * 12 > width) {
                lines.add(line.toString()); line.setLength(0);
            }
            line.append(character);
        }
        lines.add(line.toString());
        return lines;
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
