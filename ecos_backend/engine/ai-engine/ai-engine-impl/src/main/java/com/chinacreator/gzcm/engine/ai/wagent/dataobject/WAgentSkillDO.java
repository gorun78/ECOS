package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

/**
 * V237 {@code ecos_ai.ecos_wagent_skill} 技能目录 DO（DB 只在用版本，全文走 Git 归档——铁律 §3.1）。
 * <p>{@code content_git_ref} 指向 runtime Git 归档通道；gitService 单通道只传 repositoryId
 * （铁律前端 6，本任务不碰 GitService）。{@code bound_playbooks_json} JSON 语义 TEXT（MC02）。</p>
 * <p>UNIQUE(name, version, is_deleted)。存量 {@code public.ecos_skill}（0 行）收编动作待 R-41，
 * 本表只建新不动旧。</p>
 */
public class WAgentSkillDO extends WAgentBaseDO {

    private String id;
    private String name;
    private String version;
    private String summary;
    private String contentGitRef;
    private String contentVersion;
    private String boundPlaybooksJson;
    private String owner;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContentGitRef() { return contentGitRef; }
    public void setContentGitRef(String contentGitRef) { this.contentGitRef = contentGitRef; }

    public String getContentVersion() { return contentVersion; }
    public void setContentVersion(String contentVersion) { this.contentVersion = contentVersion; }

    public String getBoundPlaybooksJson() { return boundPlaybooksJson; }
    public void setBoundPlaybooksJson(String boundPlaybooksJson) { this.boundPlaybooksJson = boundPlaybooksJson; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
