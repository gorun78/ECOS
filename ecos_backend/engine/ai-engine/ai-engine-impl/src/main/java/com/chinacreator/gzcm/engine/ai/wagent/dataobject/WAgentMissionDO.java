package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDate;

/**
 * V228 {@code ecos_ai.ecos_wagent_mission} 任务书 DO（Goal 之下委派任务与 Playbook 绑定，REQ-WAG-12）。
 * <p>Playbook 定义 = Git 归档资产（铁律 §3.1），DB 只存 {@code playbook_git_ref + playbook_version}。</p>
 */
public class WAgentMissionDO extends WAgentBaseDO {

    private String id;
    private String goalId;
    private String name;
    private String stage;
    private String ownerId;
    private LocalDate dueDate;
    private String playbookId;
    private String playbookGitRef;
    private String playbookVersion;
    private Integer isTemporary;
    private String blockedReasonText;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getPlaybookId() { return playbookId; }
    public void setPlaybookId(String playbookId) { this.playbookId = playbookId; }

    public String getPlaybookGitRef() { return playbookGitRef; }
    public void setPlaybookGitRef(String playbookGitRef) { this.playbookGitRef = playbookGitRef; }

    public String getPlaybookVersion() { return playbookVersion; }
    public void setPlaybookVersion(String playbookVersion) { this.playbookVersion = playbookVersion; }

    public Integer getIsTemporary() { return isTemporary; }
    public void setIsTemporary(Integer isTemporary) { this.isTemporary = isTemporary; }

    public String getBlockedReasonText() { return blockedReasonText; }
    public void setBlockedReasonText(String blockedReasonText) { this.blockedReasonText = blockedReasonText; }
}
