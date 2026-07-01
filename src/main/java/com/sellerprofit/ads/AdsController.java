package com.sellerprofit.ads;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.ads.dto.AdSpendRequest;
import com.sellerprofit.ads.dto.AdSpendView;
import com.sellerprofit.ads.dto.ImportResult;
import com.sellerprofit.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 광고비 인제스트 API.
 *
 * 예) POST /api/ads/spends         — 수기 1건 입력
 *     POST /api/ads/spends/import  — CSV 업로드(멱등, 잘못된 행 리포트)
 *
 * ※ 로그인 필수. accountId 가 세션 유저 소유인지 확인한 뒤에만 저장한다(열거 차단은 AccountAccess 가 담당).
 */
@RestController
@RequestMapping("/api/ads/spends")
public class AdsController {

    private final AdSpendService adSpendService;
    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;

    public AdsController(AdSpendService adSpendService,
                         CurrentUser currentUser,
                         AccountAccess accountAccess) {
        this.adSpendService = adSpendService;
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdSpendView record(@Valid @RequestBody AdSpendRequest request, HttpServletRequest http) {
        accountAccess.assertOwner(request.accountId(), currentUser.requireUserId(http));
        return adSpendService.record(request);
    }

    @PostMapping("/import")
    public ImportResult importCsv(@RequestParam Long accountId,
                                  @RequestParam("file") MultipartFile file,
                                  HttpServletRequest http) {
        accountAccess.assertOwner(accountId, currentUser.requireUserId(http));
        if (file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 비어있습니다.");
        }
        String csvText = readAsUtf8(file);
        return adSpendService.importCsv(accountId, csvText);
    }

    private static String readAsUtf8(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("CSV 파일을 읽을 수 없습니다.");
        }
    }
}
