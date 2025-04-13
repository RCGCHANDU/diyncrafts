package com.diyncrafts.web.app.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.diyncrafts.web.app.model.Task;

public interface TaskRepository extends JpaRepository<Task, String> {
}