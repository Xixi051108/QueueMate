package com.queuemate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.user.UserRole;
import com.queuemate.user.UserStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesSnowflakeUserIdAsStringWithoutPrecisionLoss() throws Exception {
        UserResponse response = new UserResponse(
                2079197217998110722L,
                "remy",
                "remy",
                null,
                UserRole.USER,
                Set.of(UserRole.USER, UserRole.MERCHANT),
                UserStatus.ACTIVE
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"id\":\"2079197217998110722\"");
    }
}
