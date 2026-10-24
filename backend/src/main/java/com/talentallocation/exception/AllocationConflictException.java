package com.talentallocation.exception;

import org.springframework.http.HttpStatus;

public class AllocationConflictException extends BusinessException {
    public AllocationConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "ALLOCATION_CONFLICT");
    }
}
