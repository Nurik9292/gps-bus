package biz.ugur.busroutebackend.integration.infrastructure.security;

import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal representing an authenticated external service
 */
@Getter
@ToString
public class ApiTokenPrincipal implements UserDetails {

    private final String externalServiceId;
    private final String serviceName;
    private final boolean isActive;
    private final List<String> allowedEndpoints;
    private final Integer rateLimitPerMinute;

    public ApiTokenPrincipal(String externalServiceId,
                             String serviceName,
                             boolean isActive,
                             List<String> allowedEndpoints,
                             Integer rateLimitPerMinute) {
        this.externalServiceId = externalServiceId;
        this.serviceName = serviceName;
        this.isActive = isActive;
        this.allowedEndpoints = allowedEndpoints;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // External services have EXTERNAL_SERVICE role
        return List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_SERVICE"));
    }

    @Override
    public String getPassword() {
        return null; // Not applicable for API tokens
    }

    @Override
    public String getUsername() {
        return serviceName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // API tokens don't expire
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // API tokens don't expire
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
