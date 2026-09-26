package com.diyncrafts.web.app.repository.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.diyncrafts.web.app.model.Task;
import com.diyncrafts.web.app.model.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * Updates progress only while the task is still in {@code status}, so a late progress report can
     * never overwrite a finished task.
     */
    @Modifying
    @Query("UPDATE Task t SET t.progress = :progress WHERE t.taskId = :taskId AND t.status = :status")
    int updateProgress(@Param("taskId") String taskId, @Param("progress") double progress,
            @Param("status") TaskStatus status);

    List<Task> findByStatusAndStartTimeBefore(TaskStatus status, LocalDateTime cutoff);
}
