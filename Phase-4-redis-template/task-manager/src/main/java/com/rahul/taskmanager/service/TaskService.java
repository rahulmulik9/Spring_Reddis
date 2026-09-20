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
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

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

   /* @Cacheable("tasks")
    public TaskResponse getTaskById(Long id, String username) {
        Task task = findOwnedTask(id, username);
        return toResponse(task);
    }*/

    // Manual equivalent of getTaskById(id, username) above — same behavior,
    // but written by hand instead of relying on @Cacheable to do it for us.
    // Key includes username (not just id) for the same reason the annotated
    // version's SimpleKey[id, username] does: without it, a cache hit would
    // skip findOwnedTask's ownership check and could leak another user's task.
    // (Key format is intentionally simple here — Step 2 covers proper key design.)
    public TaskResponse getTaskByIdManual(Long id, String username) {
        String key = buildTaskKey(id, username);

        TaskResponse cached = (TaskResponse) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        Task task = findOwnedTask(id, username);
        TaskResponse response = toResponse(task);

        redisTemplate.opsForValue().set(key, response);

        return response;
    }


   // Manually rebuild the exact same cache key that @Cacheable("tasks") on getTaskById(id, username) generates by default.
    // Why this is needed: Spring's default key generator (SimpleKeyGenerator) builds a cache key from ALL of a method's parameters, in order.
    // getTaskById(id, username)      -> key = SimpleKey [id, username]
    // updateTask(id, request, username) -> if left to the default, key would be SimpleKey [id, request, username]
    // Those two keys don't match, so a plain @CacheEvict("tasks") here would try to evict a key that was never actually
    // cached -> eviction silently does nothing, and the stale GET result stays cached after an update.

    // The fix: call SimpleKeyGenerator.generateKey(...) ourselves, passing only #id and #username (skipping #request),
    // so we reconstruct the SAME key  getTaskById used when it originally cached this entry.
    @CachePut(value = "tasks", key = "T(org.springframework.cache.interceptor.SimpleKeyGenerator).generateKey(#id, #username)")
    public TaskResponse updateTask(Long id, TaskRequest request, String username) {
        Task task = findOwnedTask(id, username);

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setCompleted(request.isCompleted());

        Task updated = taskRepository.save(task);
        return toResponse(updated);
    }


    @CacheEvict("tasks")
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

    // Centralizes the key format decided in Step 2.1: task:<id>:<username>.
    private String buildTaskKey(Long id, String username) {
        return "task:" + id + ":" + username;
    }


    // Acquires a lock by trying to SET a key only if it doesn't already exist, with an expiry attached in the same atomic call.
    // If another request already holds the lock, setIfAbsent returns false immediately — this
    // request does NOT block/wait, it just knows someone else got there first.
    // lockValue should be unique per lock attempt (see 2.2) so we can safely verify we're the one releasing our own lock, not someone else's.
    private boolean tryAcquireLock(String lockKey, String lockValue, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseLock(String lockKey, String lockValue) {
        Object currentValue = redisTemplate.opsForValue().get(lockKey);
        if (lockValue.equals(currentValue)) {
            redisTemplate.delete(lockKey);
        }
        // If currentValue doesn't match, we don't own this lock anymore
        // (it likely expired and someone else acquired it) — do NOT delete a lock we don't actually hold.
    }

    public TaskResponse claimTask(Long id, String username) {
        String lockKey = "lock:task:" + id;
        String lockValue = java.util.UUID.randomUUID().toString();
        Duration lockTtl = Duration.ofSeconds(2); // generous vs. expected ~tens-of-ms execution time

        boolean acquired = tryAcquireLock(lockKey, lockValue, lockTtl);
        if (!acquired) {
            throw new TaskAccessDeniedException("Task is being claimed by someone else, try again");
        }

        try {
            Task task = taskRepository.findById(id)
                    .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

            if (task.getClaimedBy() != null) {
                throw new TaskAccessDeniedException("Task already claimed");
            }

            User claimer = getUserByUsername(username);
            task.setClaimedBy(claimer);

            Task saved = taskRepository.save(task);
            return toResponse(saved);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

}