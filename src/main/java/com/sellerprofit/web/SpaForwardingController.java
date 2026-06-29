package com.sellerprofit.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA(React Router) 딥링크/새로고침 지원.
 *
 * <p>React 라우트(/login, /signup, /accounts, /pricing)는 클라이언트에서만 존재한다. 사용자가 해당 URL 을
 * 직접 열거나 새로고침하면 서버에 매핑이 없어 404 가 난다. 이를 정적 {@code index.html} 로
 * 포워드해 SPA 가 라우팅을 이어받게 한다. ('/' 와 정적 자산은 Spring 이 이미 서빙)
 *
 * <p>API(/api/**)·정적 자산(.js/.css 등)은 포워드 대상이 아니므로 라우트를 명시적으로 나열한다.
 * 새 클라이언트 라우트를 추가하면 여기에도 더한다.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/login", "/signup", "/accounts", "/pricing"})
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
