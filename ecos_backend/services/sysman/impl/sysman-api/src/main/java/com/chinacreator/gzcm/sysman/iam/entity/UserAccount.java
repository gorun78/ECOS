package com.chinacreator.gzcm.sysman.iam.entity;

import java.time.LocalDateTime;

/**
 * 用户账户实体（IAM新实现）
 * 对应老系统TD_SM_USER表
 */
public class UserAccount {
    private String userId;
    private String username; // user_name
    private String password; // user_password
    private String realName; // user_realname
    private String pinyin; // user_pinyin
    private String sex; // user_sex
    private String homeTel; // user_hometel
    private String workTel; // user_worktel
    private String workAddress; // user_workaddress
    private String mobileTel1; // user_mobiletel1
    private String mobileTel2; // user_mobiletel2
    private String fax; // user_fax
    private String oicq; // user_oicq
    private LocalDateTime birthday; // user_birthday
    private String email; // user_email
    private String phone; // 手机号（常用字段，可能来自mobileTel1）
    private String address; // user_address
    private String postalCode; // user_postalcode
    private String idCard; // user_idcard
    private String orgId; // 所属机构ID
    private String status; // ACTIVE/INACTIVE/DELETED (对应user_isvalid)
    private String locked; // 0/1
    private LocalDateTime lockTime;
    private LocalDateTime regDate; // user_regdate
    private Integer loginCount; // user_logincount
    private String userType; // user_type
    private LocalDateTime pastTime; // past_time
    private String dredgeTime; // dredge_time
    private LocalDateTime lastLoginTime; // lastlogin_date
    private String loginIp; // login_ip
    private String workLength; // worklength
    private String politics; // politics
    private String certSn; // cert_sn
    private Integer userSn; // user_sn
    private LocalDateTime createdTime;
    private String createdBy;
    private LocalDateTime updatedTime;
    private String updatedBy;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocked() {
        return locked;
    }

    public void setLocked(String locked) {
        this.locked = locked;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    // 便捷方法：getUserRealname 使用 realName
    public String getUserRealname() {
        return realName;
    }

    public void setUserRealname(String userRealname) {
        this.realName = userRealname;
    }

    public String getPinyin() {
        return pinyin;
    }

    public void setPinyin(String pinyin) {
        this.pinyin = pinyin;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getHomeTel() {
        return homeTel;
    }

    public void setHomeTel(String homeTel) {
        this.homeTel = homeTel;
    }

    public String getWorkTel() {
        return workTel;
    }

    public void setWorkTel(String workTel) {
        this.workTel = workTel;
    }

    public String getWorkAddress() {
        return workAddress;
    }

    public void setWorkAddress(String workAddress) {
        this.workAddress = workAddress;
    }

    public String getMobileTel1() {
        return mobileTel1;
    }

    public void setMobileTel1(String mobileTel1) {
        this.mobileTel1 = mobileTel1;
    }

    public String getMobileTel2() {
        return mobileTel2;
    }

    public void setMobileTel2(String mobileTel2) {
        this.mobileTel2 = mobileTel2;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getOicq() {
        return oicq;
    }

    public void setOicq(String oicq) {
        this.oicq = oicq;
    }

    public LocalDateTime getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDateTime birthday) {
        this.birthday = birthday;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public LocalDateTime getRegDate() {
        return regDate;
    }

    public void setRegDate(LocalDateTime regDate) {
        this.regDate = regDate;
    }

    public Integer getLoginCount() {
        return loginCount;
    }

    public void setLoginCount(Integer loginCount) {
        this.loginCount = loginCount;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public LocalDateTime getPastTime() {
        return pastTime;
    }

    public void setPastTime(LocalDateTime pastTime) {
        this.pastTime = pastTime;
    }

    public String getDredgeTime() {
        return dredgeTime;
    }

    public void setDredgeTime(String dredgeTime) {
        this.dredgeTime = dredgeTime;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getWorkLength() {
        return workLength;
    }

    public void setWorkLength(String workLength) {
        this.workLength = workLength;
    }

    public String getPolitics() {
        return politics;
    }

    public void setPolitics(String politics) {
        this.politics = politics;
    }

    public String getCertSn() {
        return certSn;
    }

    public void setCertSn(String certSn) {
        this.certSn = certSn;
    }

    public Integer getUserSn() {
        return userSn;
    }

    public void setUserSn(Integer userSn) {
        this.userSn = userSn;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
}
