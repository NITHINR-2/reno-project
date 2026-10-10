package com.renault.fleet.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers for driving the auth endpoints through MockMvc. */
public final class AuthFlow {

    public static final String COOKIE = "fleet_rt";

    private AuthFlow() {
    }

    public record Session(String accessToken, String refreshToken, String setCookieHeader) {

        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie cookie() {
            return new Cookie(COOKIE, refreshToken);
        }
    }

    public static ResultActions login(MockMvc mockMvc, String username, String password) throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    public static Session loginOk(MockMvc mockMvc, String username) throws Exception {
        return loginOk(mockMvc, username, TestUsers.PASSWORD);
    }

    public static Session loginOk(MockMvc mockMvc, String username, String password) throws Exception {
        return session(login(mockMvc, username, password).andReturn().getResponse());
    }

    public static ResultActions refresh(MockMvc mockMvc, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie(COOKIE, refreshToken)));
    }

    public static Session session(MockHttpServletResponse response) throws Exception {
        if (response.getStatus() != 200) {
            throw new AssertionError("Expected 200 but was " + response.getStatus() + ": " + response.getContentAsString());
        }
        String accessToken = JsonPath.read(response.getContentAsString(), "$.accessToken");
        String header = refreshSetCookie(response);
        String value = header.substring(COOKIE.length() + 1, header.indexOf(';'));
        return new Session(accessToken, value, header);
    }

    public static String refreshSetCookie(MockHttpServletResponse response) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        return headers.stream().filter(h -> h.startsWith(COOKIE + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("No " + COOKIE + " Set-Cookie header in " + headers));
    }
}
