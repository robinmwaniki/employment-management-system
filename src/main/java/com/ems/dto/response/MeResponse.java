package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Returned by GET /api/auth/me so the React app knows who is signed in,
 * what role they have, and which employee record they are linked to.
 */
@Data
@Builder
public class MeResponse {

    private Long userId;
    private String username;
    private String role;
    private Long employeeId;
    private String firstName;
    private String lastName;
    private String profilePhoto;
}
