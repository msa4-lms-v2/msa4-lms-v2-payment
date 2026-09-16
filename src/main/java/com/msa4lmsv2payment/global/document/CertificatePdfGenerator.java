package com.msa4lmsv2payment.global.document;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 증명서 종류별 항목은 유지하고, 대학 증명서의 공통 서식과 검증 QR을 적용한다.
@Component
public class CertificatePdfGenerator {
    private static final DateTimeFormatter ISSUED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
    private static final float WIDTH = PDRectangle.A4.getWidth();
    private static final float LEFT = 68f;
    private static final float RIGHT = WIDTH - LEFT;
    private static final float BODY_TOP = 620f;
    private static final float BODY_BOTTOM = 310f;
    private static final Color NAVY = new Color(24, 44, 69);
    private static final Color MUTED = new Color(92, 103, 115);
    private static final Color RULE = new Color(211, 219, 226);
    private final byte[] fontBytes;
    private final byte[] headingFontBytes;
    private final byte[] emblemBytes;

    public CertificatePdfGenerator() {
        fontBytes = resource("fonts/NotoSansKR.ttf");
        headingFontBytes = resource("fonts/NotoSansKR-SemiBold.ttf");
        emblemBytes = resource("images/university-emblem.png");
    }

    public byte[] generate(String title, List<Map.Entry<String, String>> rows, String verifyUrl, LocalDateTime issuedAt) {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes), true);
            PDType0Font heading = PDType0Font.load(document, new ByteArrayInputStream(headingFontBytes), true);
            boolean compact = rows.size() > 9;
            float fontSize = compact ? 10.5f : 12f;
            float lineHeight = compact ? 15f : 19f;
            float padding = compact ? 8f : 21f;
            List<List<Row>> pages = paginate(font, rows, fontSize, lineHeight, padding);
            PDImageXObject qr = LosslessFactory.createFromImage(document, renderQr(verifyUrl));
            PDImageXObject emblem = PDImageXObject.createFromByteArray(document, emblemBytes, "university-emblem");
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle(title);
            info.setAuthor("미래대학교");
            document.setDocumentInformation(info);
            for (int i = 0; i < pages.size(); i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    frame(stream, font, heading, emblem, title);
                    float top = BODY_TOP;
                    for (Row row : pages.get(i)) {
                        float height = row.keys().size() * lineHeight + padding;
                        stream.setNonStrokingColor(new Color(246, 248, 250));
                        stream.addRect(LEFT, top - height, 117, height);
                        stream.fill();
                        float baseline = top - padding / 2 - fontSize;
                        for (int j = 0; j < row.keys().size(); j++) {
                            text(stream, font, fontSize, LEFT + 14, baseline, row.keys().get(j), MUTED);
                            text(stream, font, fontSize, LEFT + 133, baseline, row.values().get(j), NAVY);
                            baseline -= lineHeight;
                        }
                        line(stream, LEFT, top - height, RIGHT, top - height, RULE, 0.5f);
                        top -= height;
                    }
                    line(stream, LEFT, BODY_TOP, RIGHT, BODY_TOP, NAVY, 1f);
                    footer(stream, font, heading, qr, title, verifyUrl, issuedAt, i + 1, pages.size());
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("증명서 PDF 생성에 실패했습니다.", e);
        }
    }

    private List<List<Row>> paginate(PDType0Font font, List<Map.Entry<String, String>> rows,
                                     float size, float lineHeight, float padding) throws IOException {
        List<List<Row>> pages = new ArrayList<>();
        List<Row> page = new ArrayList<>();
        pages.add(page);
        float remaining = BODY_TOP - BODY_BOTTOM;
        int maxLines = (int) ((BODY_TOP - BODY_BOTTOM - padding) / lineHeight);
        for (var entry : rows) {
            List<String> keys = wrap(font, entry.getKey(), 92, size);
            List<String> values = wrap(font, entry.getValue(), RIGHT - LEFT - 147, size);
            int count = Math.max(keys.size(), values.size());
            for (int offset = 0; offset < count;) {
                int length = Math.min(maxLines, count - offset);
                float height = length * lineHeight + padding;
                if (!page.isEmpty() && height > remaining) {
                    page = new ArrayList<>();
                    pages.add(page);
                    remaining = BODY_TOP - BODY_BOTTOM;
                }
                List<String> keyLines = new ArrayList<>();
                List<String> valueLines = new ArrayList<>();
                for (int j = offset; j < offset + length; j++) {
                    keyLines.add(j < keys.size() ? keys.get(j) : "");
                    valueLines.add(j < values.size() ? values.get(j) : "");
                }
                page.add(new Row(keyLines, valueLines));
                remaining -= height;
                offset += length;
            }
        }
        return pages;
    }

    private void frame(PDPageContentStream stream, PDType0Font font, PDType0Font heading,
                       PDImageXObject emblem, String title) throws IOException {
        stream.setStrokingColor(RULE);
        stream.setLineWidth(0.7f);
        stream.addRect(34, 34, WIDTH - 68, PDRectangle.A4.getHeight() - 68);
        stream.stroke();
        stream.drawImage(emblem, LEFT, 745, 38, 38 * emblem.getHeight() / emblem.getWidth());
        text(stream, heading, 13, LEFT + 49, 769, "미래대학교", NAVY);
        text(stream, font, 7.5f, LEFT + 49, 752, "MIRAE UNIVERSITY", MUTED);
        text(stream, font, 8, RIGHT - 70, 763, "학사 증명 문서", MUTED);
        line(stream, LEFT, 727, RIGHT, 727, RULE, 0.6f);
        centered(stream, heading, 29, 678, title, NAVY);
        centered(stream, font, 8, 651, "MIRAE UNIVERSITY  /  CERTIFICATE", MUTED);
    }

    private void footer(PDPageContentStream stream, PDType0Font font, PDType0Font heading,
                        PDImageXObject qr, String title, String verifyUrl, LocalDateTime issuedAt,
                        int page, int pageCount) throws IOException {
        String statement = "재학증명서".equals(title.replace(" ", ""))
                ? "위 사람은 본교에 재학 중임을 증명합니다." : "위의 사실을 증명합니다.";
        centered(stream, font, 13, 282, statement, NAVY);
        centered(stream, font, 12, 243, issuedAt.format(ISSUED_AT_FORMAT), NAVY);
        centered(stream, heading, 20, 205, "미래대학교 총장", NAVY);
        line(stream, LEFT, 178, RIGHT, 178, RULE, 0.6f);
        stream.drawImage(qr, LEFT, 78, 86, 86);
        text(stream, heading, 10, LEFT + 105, 144, "증명서 진위 확인", NAVY);
        text(stream, font, 9, LEFT + 105, 124, "QR 코드를 스캔하여 발급 정보와 유효 여부를 확인하세요.", MUTED);
        text(stream, font, 8, LEFT + 105, 103, "문서 확인번호", MUTED);
        text(stream, font, 8, LEFT + 105, 88, verificationNumber(verifyUrl), NAVY);
        centered(stream, font, 8, 51, page + " / " + pageCount, MUTED);
    }

    private String verificationNumber(String verifyUrl) {
        String query = URI.create(verifyUrl).getRawQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) {
                    String token = URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
                    if (token.matches("[A-Za-z0-9-]{1,40}")) return token;
                }
            }
        }
        return "QR 코드로 확인";
    }

    private void centered(PDPageContentStream stream, PDType0Font font, float size,
                          float y, String value, Color color) throws IOException {
        text(stream, font, size, (WIDTH - font.getStringWidth(value) / 1000 * size) / 2, y, value, color);
    }

    private void text(PDPageContentStream stream, PDType0Font font, float size,
                      float x, float y, String value, Color color) throws IOException {
        stream.setNonStrokingColor(color);
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private void line(PDPageContentStream stream, float x1, float y1, float x2,
                      float y2, Color color, float width) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private List<String> wrap(PDType0Font font, String value, float width, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int cp : (value == null ? "-" : value).codePoints().toArray()) {
            String character = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
            if (!line.isEmpty() && font.getStringWidth(line + character) / 1000 * size > width) {
                int space = line.lastIndexOf(" ");
                if (space > 0) {
                    lines.add(line.substring(0, space));
                    line.delete(0, space + 1);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                }
            }
            line.append(character);
        }
        lines.add(line.toString());
        return lines;
    }

    private static byte[] resource(String path) {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("증명서 PDF 리소스를 불러올 수 없습니다: " + path, e);
        }
    }

    private BufferedImage renderQr(String content) {
        try {
            return MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE,
                    300, 300, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)));
        } catch (Exception e) {
            throw new IllegalStateException("QR 코드 생성에 실패했습니다.", e);
        }
    }

    private record Row(List<String> keys, List<String> values) {}
}
