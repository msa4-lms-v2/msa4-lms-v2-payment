CREATE TABLE IF NOT EXISTS scholarship_application_attachments (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    scholarship_application_id  BIGINT NOT NULL,
    file_name                   VARCHAR(255) NOT NULL,
    object_key                  VARCHAR(500) NOT NULL,
    content_type                VARCHAR(100) NOT NULL,
    file_size                   BIGINT NOT NULL,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scholarship_application_attachments_application_id (scholarship_application_id),
    CONSTRAINT fk_scholarship_application_attachments_application
        FOREIGN KEY (scholarship_application_id) REFERENCES scholarship_applications (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
