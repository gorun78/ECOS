package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.iam.dao.UserDao;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements IUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String PASSWORD_COMPLEXITY_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$";
    private static final int LOCK_FAIL_THRESHOLD = 5;
    private static final int DEFAULT_MIN_PASSWORD_LENGTH = 6;
    private final UserDao userDao;
    private final SysConfigService sysConfigService;
    private final PasswordHasher passwordHasher = new PasswordHasher();

    // 简单失败计数（可替换为Redis）
    private final Map<String, Integer> failedAttempts = new HashMap<>();

    @Autowired
    public UserServiceImpl(UserDao userDao, SysConfigService sysConfigService) {
        this.userDao = userDao;
        this.sysConfigService = sysConfigService;
    }

    @Override
    public UserAccount createUser(UserAccount user, String rawPassword, String operator) throws UserException {
        try {
            if (userDao.findByUsername(user.getUsername()) != null) {
                throw new UserException("用户名已存在");
            }
            if (user.getEmail() != null && userDao.findByEmail(user.getEmail()) != null) {
                throw new UserException("邮箱已存在");
            }
            validatePasswordPolicy(rawPassword);
            String nowStatus = user.getStatus() == null ? "ACTIVE" : user.getStatus();
            user.setUserId(user.getUserId() == null ? UUID.randomUUID().toString() : user.getUserId());
            user.setPassword(passwordHasher.hash(rawPassword));
            user.setStatus(nowStatus);
            user.setLocked(user.getLocked() == null ? "0" : user.getLocked());
            LocalDateTime now = LocalDateTime.now();
            user.setCreatedTime(now);
            user.setUpdatedTime(now);
            user.setCreatedBy(operator);
            user.setUpdatedBy(operator);
            userDao.insert(user);
            return userDao.findById(user.getUserId());
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("创建用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount updateUser(UserAccount user, String operator) throws UserException {
        try {
            UserAccount existing = userDao.findById(user.getUserId());
            if (existing == null) {
                throw new UserException("用户不存在");
            }
            if (user.getPassword() != null) {
                validatePasswordPolicy(user.getPassword());
                existing.setPassword(passwordHasher.hash(user.getPassword()));
            }
            if (user.getEmail() != null) existing.setEmail(user.getEmail());
            if (user.getPhone() != null) existing.setPhone(user.getPhone());
            if (user.getStatus() != null) existing.setStatus(user.getStatus());
            existing.setUpdatedBy(operator);
            existing.setUpdatedTime(LocalDateTime.now());
            userDao.update(existing);
            return userDao.findById(existing.getUserId());
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("更新用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteUser(String userId, String operator) throws UserException {
        try {
            UserAccount existing = userDao.findById(userId);
            if (existing == null) {
                throw new UserException("用户不存在");
            }
            userDao.delete(userId);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("删除用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount getUser(String userId) throws UserException {
        try {
            return userDao.findById(userId);
        } catch (Exception e) {
            throw new UserException("获取用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount getUserByUserId(String userId) throws UserException {
        try {
            return userDao.findById(userId);
        } catch (Exception e) {
            throw new UserException("根据用户ID获取用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UserAccount getUserByUsername(String username) throws UserException {
        try {
            return userDao.findByUsername(username);
        } catch (Exception e) {
            throw new UserException("根据用户名获取用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UserAccount> listUsers(String keyword, String status, int page, int pageSize) throws UserException {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("keyword", keyword);
            condition.put("status", status);
            List<UserAccount> users = userDao.query(condition);
            int from = Math.max(0, (page - 1) * pageSize);
            int to = Math.min(users.size(), from + pageSize);
            if (from >= to) return new ArrayList<>();
            return users.subList(from, to).stream().collect(Collectors.toList());
        } catch (Exception e) {
            throw new UserException("查询用户列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void activateUser(String userId, String operator) throws UserException {
        setStatus(userId, "ACTIVE", operator);
    }

    @Override
    public void deactivateUser(String userId, String operator) throws UserException {
        setStatus(userId, "INACTIVE", operator);
    }

    @Override
    public void lockUser(String userId, String operator) throws UserException {
        try {
            UserAccount user = userDao.findById(userId);
            if (user == null) throw new UserException("用户不存在");
            user.setLocked("1");
            user.setLockTime(LocalDateTime.now());
            user.setUpdatedBy(operator);
            user.setUpdatedTime(LocalDateTime.now());
            userDao.update(user);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("锁定用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void unlockUser(String userId, String operator) throws UserException {
        try {
            UserAccount user = userDao.findById(userId);
            if (user == null) throw new UserException("用户不存在");
            user.setLocked("0");
            user.setLockTime(null);
            user.setUpdatedBy(operator);
            user.setUpdatedTime(LocalDateTime.now());
            failedAttempts.remove(userId);
            userDao.update(user);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("解锁用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void resetPassword(String userId, String newPassword, String operator) throws UserException {
        try {
            UserAccount user = userDao.findById(userId);
            if (user == null) throw new UserException("用户不存在");
            validatePasswordPolicy(newPassword);
            user.setPassword(passwordHasher.hash(newPassword));
            user.setUpdatedBy(operator);
            user.setUpdatedTime(LocalDateTime.now());
            userDao.update(user);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("重置密码失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyPassword(String userId, String rawPassword) throws UserException {
        try {
            UserAccount user = userDao.findById(userId);
            if (user == null) throw new UserException("用户不存在");
            if ("1".equals(user.getLocked())) {
                throw new UserException("用户已锁定");
            }
            boolean ok = passwordHasher.matches(rawPassword, user.getPassword());
            if (!ok) {
                int count = failedAttempts.getOrDefault(userId, 0) + 1;
                failedAttempts.put(userId, count);
                if (count >= LOCK_FAIL_THRESHOLD) {
                    lockUser(userId, "system");
                }
            } else {
                failedAttempts.remove(userId);
                user.setLastLoginTime(LocalDateTime.now());
                userDao.update(user);
            }
            return ok;
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("验证密码失败: " + e.getMessage(), e);
        }
    }

    private void setStatus(String userId, String status, String operator) throws UserException {
        try {
            UserAccount user = userDao.findById(userId);
            if (user == null) throw new UserException("用户不存在");
            user.setStatus(status);
            user.setUpdatedBy(operator);
            user.setUpdatedTime(LocalDateTime.now());
            userDao.update(user);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            throw new UserException("更新状态失败: " + e.getMessage(), e);
        }
    }

    private void validatePasswordPolicy(String rawPassword) throws UserException {
        int minLength = sysConfigService.getInt("password.min_length", DEFAULT_MIN_PASSWORD_LENGTH);
        if (rawPassword == null || rawPassword.length() < minLength) {
            throw new UserException("密码长度至少" + minLength + "位");
        }
        boolean requireComplexity = sysConfigService.getBoolean("password.require_complexity", true);
        if (requireComplexity && !rawPassword.matches(PASSWORD_COMPLEXITY_REGEX)) {
            throw new UserException("密码需包含大小写字母、数字和特殊字符");
        }
    }
}
