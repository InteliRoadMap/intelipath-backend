package com.inteliroadmap.backend.exceptions;

import com.inteliroadmap.backend.exceptions.enums.ErrorCode;
import lombok.Getter;


@Getter
public class AppException extends  RuntimeException {
        private final ErrorCode errorCode;

        public AppException(ErrorCode errorCode) {
            super(errorCode.getMessage());
            this.errorCode = errorCode;
        }

}
