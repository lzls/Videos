/*
 * Created on 2020-10-14 8:35:24 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import java.io.IOException;

/**
 * @author 刘振林
 */
public class ResponseNotOKException extends IOException {

    public ResponseNotOKException() {
    }

    public ResponseNotOKException(String message) {
        super(message);
    }

    public ResponseNotOKException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResponseNotOKException(Throwable cause) {
        super(cause);
    }
}
