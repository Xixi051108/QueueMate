package com.queuemate;

import com.queuemate.common.api.ApiResponse;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class QueueMateApplicationTests {

    @Test
    void apiResponseSuccessUsesStandardCode() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.code()).isEqualTo("0");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("ok");
    }
}
