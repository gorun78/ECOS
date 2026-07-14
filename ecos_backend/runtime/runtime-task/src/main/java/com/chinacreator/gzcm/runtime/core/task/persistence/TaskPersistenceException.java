package com.chinacreator.gzcm.runtime.core.task.persistence;

/**
 * 任务持久化异常。
 */
public class TaskPersistenceException extends Exception {
    private static final long serialVersionUID = 1L;

    public TaskPersistenceException(String message) {
        super(message);
    }

    public TaskPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

