package com.employger.dto;

/**
 * The reason sent with Reject or Cancel. Checked by the request itself
 * (ApprovalRequest): it must not be blank and is at most 255 characters.
 */
public record ReasonRequest(String reason) {

    public static String of(ReasonRequest request) {
        return request == null ? null : request.reason();
    }
}
