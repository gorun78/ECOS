package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;

public interface UserDao {

    void insert(UserAccount user) throws Exception;

    void update(UserAccount user) throws Exception;

    void delete(String userId) throws Exception;

    UserAccount findById(String userId) throws Exception;

    UserAccount findByUsername(String username) throws Exception;

    UserAccount findByEmail(String email) throws Exception;

    List<UserAccount> query(Map<String, Object> condition) throws Exception;
}


