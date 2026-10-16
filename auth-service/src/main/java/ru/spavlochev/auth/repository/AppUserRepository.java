package ru.spavlochev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.spavlochev.auth.entity.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
