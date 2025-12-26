package biz.ugur.busroutebackend.shared.domain.services;

public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
