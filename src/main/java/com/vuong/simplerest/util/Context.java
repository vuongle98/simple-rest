package com.vuong.simplerest.util;

import com.vuong.simplerest.exception.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Objects;

public class Context {

  public static String getCurrentUserToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (Objects.isNull(authentication) || !authentication.isAuthenticated()) {
      throw new UserNotFoundException("User not authenticated");
    }

    if (authentication instanceof JwtAuthenticationToken) {
      Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
      return jwt.getTokenValue();
    }

    throw new UserNotFoundException("Invalid authentication type");
  }

  public static String getCurrentUserId() {
    Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return jwt.getSubject(); // sub is Keycloak userId
  }
}
