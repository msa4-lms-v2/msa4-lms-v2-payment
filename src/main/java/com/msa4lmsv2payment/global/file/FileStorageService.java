package com.msa4lmsv2payment.global.file;

import com.msa4lmsv2payment.global.error.FileStorageException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofDays(1);

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    // 서버에서 생성한 바이트(증명서 PDF 등)를 MinIO에 올리고 버킷 내 objectKey를 반환한다.
    public String upload(String pathPrefix, String extension, byte[] content, String contentType) {
        String objectKey = pathPrefix + "/" + UUID.randomUUID() + extension;
        try (var inputStream = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("파일 업로드에 실패했습니다: " + objectKey, e);
        }
        return objectKey;
    }

    // 사용자가 업로드한 파일(장학금 증빙 등)을 MinIO에 올리고 버킷 내 objectKey를 반환한다.
    public String upload(String pathPrefix, MultipartFile file) {
        String objectKey = pathPrefix + "/" + UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
        try (var inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (IOException e) {
            throw new FileStorageException("파일을 읽을 수 없습니다: " + file.getOriginalFilename(), e);
        } catch (Exception e) {
            throw new FileStorageException("파일 업로드에 실패했습니다: " + file.getOriginalFilename(), e);
        }
        return objectKey;
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^a-zA-Z0-9._\\-가-힣]", "_");
    }

    // 조회용 임시 서명 URL을 발급한다(만료 시간 존재, 버킷은 비공개 유지).
    public String presignedDownloadUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry((int) DOWNLOAD_URL_EXPIRY.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("다운로드 URL 발급에 실패했습니다: " + objectKey, e);
        }
    }

    public byte[] download(String objectKey) {
        try (var inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return inputStream.readAllBytes();
        } catch (Exception exception) {
            throw new FileStorageException("파일 다운로드에 실패했습니다: " + objectKey, exception);
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new FileStorageException("파일 삭제에 실패했습니다: " + objectKey, exception);
        }
    }
}
