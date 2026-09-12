package com.fitcheck.tryon.repository;

import com.fitcheck.tryon.entity.TryonRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TryonRequestRepository extends JpaRepository<TryonRequest, UUID> {
}