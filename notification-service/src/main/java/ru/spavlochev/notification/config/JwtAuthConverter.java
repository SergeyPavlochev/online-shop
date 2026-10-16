package ru.spavlochev.notification.config;

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
    private static final String ROLES_CLAIM = "roles";

    public JwtAuthenticationConverter createJwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtRolesConverter());
        return jwtAuthenticationConverter;
    }

    private static class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

            Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
            if (realmAccess != null && realmAccess.containsKey(ROLES_CLAIM)) {
                @SuppressWarnings("unchecked")
                var realmRoles = (java.util.List<String>) realmAccess.get(ROLES_CLAIM);
                realmRoles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }

            return authorities;
        }
    }
}
