package com.fitcheck.identity.repository;

import com.fitcheck.identity.entity.PhotoType;
import com.fitcheck.identity.entity.UserBodyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBodyPhotoRepository extends JpaRepository<UserBodyPhoto, UUID> {

    Optional<UserBodyPhoto> findByUserIdAndPhotoType(UUID userId, PhotoType photoType);
    List<UserBodyPhoto> findAllByUserId(UUID userId);
}
