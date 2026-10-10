package com.thomas.order_management.exception;

import org.springframework.http.HttpStatus;

/**
 * Fachlicher Fehler im Stil einer SAP-Nachricht: Nachrichtenklasse + Nummer + Text,
 * z. B. "V1 382 Material 200001 ist nicht ausreichend verfügbar".
 */
public class BusinessException extends RuntimeException {

    private final String messageClass;
    private final String messageNumber;
    private final HttpStatus status;

    public BusinessException(String messageClass, String messageNumber, String message) {
        this(messageClass, messageNumber, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String messageClass, String messageNumber, String message, HttpStatus status) {
        super(message);
        this.messageClass = messageClass;
        this.messageNumber = messageNumber;
        this.status = status;
    }

    public String getMessageClass() {
        return messageClass;
    }

    public String getMessageNumber() {
        return messageNumber;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageId() {
        return messageClass + " " + messageNumber;
    }
}
