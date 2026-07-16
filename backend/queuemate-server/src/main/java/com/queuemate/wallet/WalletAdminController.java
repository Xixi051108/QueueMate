package com.queuemate.wallet;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class WalletAdminController {

    private final WalletService walletService;

    public WalletAdminController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/wallet-transactions")
    public ApiResponse<List<WalletTransactionResponse>> listTransactions(
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) WalletTransactionType type,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(walletService.listAllTransactions(userId, type, principal));
    }

    @PostMapping("/wallets/{userId}/adjust")
    public ApiResponse<WalletResponse> adjust(
            @Positive @PathVariable Long userId,
            @Valid @RequestBody WalletAdminAdjustRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(walletService.adjust(userId, request, principal));
    }
}
