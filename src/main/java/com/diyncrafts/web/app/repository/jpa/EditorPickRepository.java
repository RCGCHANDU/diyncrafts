package com.diyncrafts.web.app.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.diyncrafts.web.app.model.EditorPick;

public interface EditorPickRepository extends JpaRepository<EditorPick, Long> {
    Optional<EditorPick> findTopByOrderByIdDesc();
}