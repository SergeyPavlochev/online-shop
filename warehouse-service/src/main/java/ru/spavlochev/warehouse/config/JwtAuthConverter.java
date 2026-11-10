package ru.spavlochev.warehouse.config;

import jakarta.annotation.Nonnull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Map;

public class JwtAuthConverter {

    private static final String REALM_ACCESS = "realm_access";
    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String ROLES_CLAIM = "roles";

    public JwtAuthenticationConverter createJwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtRolesConverter());
        return jwtAuthenticationConverter;
    }

    private static class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

        @Override
        public Collection<GrantedAuthority> convert(@Nonnull Jwt jwt) {
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

            // Realm roles (for user JWTs with realm_access.roles)
            Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
            if (realmAccess != null && realmAccess.containsKey(ROLES_CLAIM)) {
                @SuppressWarnings("unchecked")
                var realmRoles = (java.util.List<String>) realmAccess.get(ROLES_CLAIM);
                realmRoles.forEach(role -> authorities.add(
                        new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role)));
            }

            // Client roles (for M2M/service account JWTs with resource_access.<client_id>.roles)
            Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS);
            if (resourceAccess != null) {
                for (Object value : resourceAccess.values()) {
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> clientRoles = (Map<String, Object>) value;
                        if (clientRoles.containsKey(ROLES_CLAIM)) {
                            @SuppressWarnings("unchecked")
                            var roles = (java.util.List<String>) clientRoles.get(ROLES_CLAIM);
                            roles.forEach(role -> authorities.add(
                                    new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role)));
                        }
                    }
                }
            }

            return authorities;
        }
    }
}
