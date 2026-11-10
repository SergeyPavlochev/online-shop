package ru.spavlochev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.auth.entity.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
