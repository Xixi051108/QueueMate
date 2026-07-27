package com.queuemate.testsupport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostmanCleanupRequest(
        @NotBlank
        @Pattern(regexp = "[a-z0-9]{8,32}")
        String runId,
        @Pattern(regexp = "^$|^[0-9]{1,19}$")
        String venueId,
        @Size(max = 10)
        List<@Pattern(regexp = "^[0-9]{1,19}$") String> slotIds
) {
}
