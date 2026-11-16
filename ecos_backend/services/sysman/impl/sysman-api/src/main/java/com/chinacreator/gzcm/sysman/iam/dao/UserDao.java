package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;

public interface UserDao {

    void insert(UserAccount user) throws Exception;

    void update(UserAccount user) throws Exception;

    /**
     * 只更新口令列与审计列。
     *
     * <p>{@link #update} 的 SQL 刻意不含 password（防资料编辑顺带覆盖口令），
     * 因此改密必须走本方法——否则接口回 200 而库里的口令不变。</p>
     */
    void updatePassword(UserAccount user) throws Exception;

    void delete(String userId) throws Exception;

    UserAccount findById(String userId) throws Exception;

    UserAccount findByUsername(String username) throws Exception;

    UserAccount findByEmail(String email) throws Exception;

    List<UserAccount> query(Map<String, Object> condition) throws Exception;
}


