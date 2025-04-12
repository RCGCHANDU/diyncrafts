package com.diyncrafts.transcoding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.diyncrafts.transcoding.service.model.Task;

public interface TaskRepository extends JpaRepository<Task, String> {
}