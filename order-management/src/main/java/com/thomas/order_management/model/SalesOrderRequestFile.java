package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Originaldatei eines Auftragseingangs (z. B. die Bestellung als PDF).
 */
@Entity
@Table(name = "sales_order_request_files")
@Getter
@Setter
@NoArgsConstructor
public class SalesOrderRequestFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false, length = 5_000_000)
    private byte[] content;

    public SalesOrderRequestFile(String fileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
    }
}
