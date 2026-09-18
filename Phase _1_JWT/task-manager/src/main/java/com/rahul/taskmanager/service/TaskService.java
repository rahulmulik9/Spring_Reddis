package com.rahul.taskmanager.service;

import com.rahul.taskmanager.dto.TaskRequest;
import com.rahul.taskmanager.dto.TaskResponse;
import com.rahul.taskmanager.entity.Task;
import com.rahul.taskmanager.entity.User;
import com.rahul.taskmanager.exception.TaskAccessDeniedException;
import com.rahul.taskmanager.exception.TaskNotFoundException;
import com.rahul.taskmanager.repository.TaskRepository;
import com.rahul.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskResponse createTask(TaskRequest request, String username) {
        User owner = getUserByUsername(username);

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(request.isCompleted())
                .owner(owner)
                .build();

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    public List<TaskResponse> getAllTasks(String username) {
        User owner = getUserByUsername(username);
        return taskRepository.findByOwnerId(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TaskResponse getTaskById(Long id, String username) {
        Task task = findOwnedTask(id, username);
        return toResponse(task);
    }

    public TaskResponse updateTask(Long id, TaskRequest request, String username) {
        Task task = findOwnedTask(id, username);

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCompleted(request.isCompleted());

        Task updated = taskRepository.save(task);
        return toResponse(updated);
    }

    public void deleteTask(Long id, String username) {
        Task task = findOwnedTask(id, username);
        taskRepository.delete(task);
    }

    // Fetches the task by id, then confirms it belongs to the requesting user.
    // Throws 404 if the task doesn't exist at all, 403 if it exists but belongs
    // to someone else — so users can't tell the two cases apart from outside.
    private Task findOwnedTask(Long id, String username) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

        if (!task.getOwner().getUsername().equals(username)) {
            throw new TaskAccessDeniedException("You do not have access to task id: " + id);
        }

        return task;
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new TaskNotFoundException("User not found: " + username));
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .completed(task.isCompleted())
                .createdAt(task.getCreatedAt())
                .build();
    }
}