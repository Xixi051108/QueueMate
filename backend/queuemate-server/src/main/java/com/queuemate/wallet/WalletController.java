package com.queuemate.wallet;

import com.queuemate.auth.AuthenticatedUser;
import com.queuemate.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets/my")
@PreAuthorize("hasRole('USER')")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ApiResponse<WalletResponse> getMine(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(walletService.getMine(principal));
    }

    @PostMapping("/recharge")
    public ApiResponse<WalletResponse> recharge(
            @Valid @RequestBody WalletRechargeRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(walletService.recharge(request, principal));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<WalletTransactionResponse>> listMine(
            @RequestParam(required = false) WalletTransactionType type,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(walletService.listMine(type, principal));
    }
}
