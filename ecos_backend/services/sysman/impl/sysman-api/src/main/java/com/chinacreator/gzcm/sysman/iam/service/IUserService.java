package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;

public interface IUserService {

    UserAccount createUser(UserAccount user, String rawPassword, String operator) throws UserException;

    UserAccount updateUser(UserAccount user, String operator) throws UserException;

    void deleteUser(String userId, String operator) throws UserException;

    UserAccount getUser(String userId) throws UserException;

    UserAccount getUserByUserId(String userId) throws UserException;

    UserAccount getUserByUsername(String username) throws UserException;

    List<UserAccount> listUsers(String keyword, String status, int page, int pageSize) throws UserException;

    void activateUser(String userId, String operator) throws UserException;

    void deactivateUser(String userId, String operator) throws UserException;

    void lockUser(String userId, String operator) throws UserException;

    void unlockUser(String userId, String operator) throws UserException;

    void resetPassword(String userId, String newPassword, String operator) throws UserException;

    boolean verifyPassword(String userId, String rawPassword) throws UserException;

    class UserException extends Exception {
        private static final long serialVersionUID = 1L;
        public UserException(String message) { super(message); }
        public UserException(String message, Throwable cause) { super(message, cause); }
    }
}


