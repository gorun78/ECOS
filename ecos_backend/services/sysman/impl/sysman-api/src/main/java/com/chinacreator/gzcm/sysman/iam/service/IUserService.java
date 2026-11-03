package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;

public interface IUserService {

    UserAccount createUser(UserAccount user, String rawPassword, String operator) throws UserException;

    UserAccount updateUser(UserAccount user, String operator) throws UserException;

    void deleteUser(String userId, String operator) throws UserException;

    UserAccount getUser(String userId) throws UserException;

    /**
     * 用户<b>基础信息投影</b>（14 类矩阵之「用户基础信息」；§2.9.8⑥ 不缓存高敏感数据；#147AF/D）。
     *
     * <p><b>为什么是投影</b>：{@code UserAccount} 含口令、身份证、手机、邮箱、地址、证书序列等
     * 敏感字段，按 §2.9.8⑥ 一律不得进缓存。本方法只返回白名单字段（身份标识 + 展示名 + 机构 +
     * 状态位），可由缓存承受；<b>任何新增字段都须先过「是否敏感」判定</b>再进本记录。</p>
     *
     * @param userId 用户标识
     * @return 基础信息投影；用户不存在返回 {@code null}
     * @throws UserException 取数异常（调用方须显式报错，禁当空对象）
     */
    UserBasicProfile getUserBasicProfile(String userId) throws UserException;

    /** 用户基础信息投影（白名单字段；禁加敏感列）。 */
    record UserBasicProfile(String userId, String username, String realName, String orgId,
                            String status, String locked, String userType) {
    }

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


