package com.chinacreator.gzcm.engine.kb.profile;

import java.util.List;

/**
 * 历史画像 resolve 返回体（F04-06 / D.3 契约）。
 * <pre>
 * {profileId, profileKey, profileVersion, confidence, degradeChain:[...],
 *  stats:{p10,p50,p90,mean,ciLow,ciHigh,sampleCount,missingRate,statsMethod}, traceId}
 * </pre>
 *
 * @author ECOS KB Team
 */
public class ProfileResolveVO {

    private String profileId;
    private String profileKey;
    private String profileVersion;
    private String confidence;
    private List<String> degradeChain;
    private ProfileStatsVO stats;
    private String traceId;

    public ProfileResolveVO() {
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getProfileKey() {
        return profileKey;
    }

    public void setProfileKey(String profileKey) {
        this.profileKey = profileKey;
    }

    public String getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(String profileVersion) {
        this.profileVersion = profileVersion;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<String> getDegradeChain() {
        return degradeChain;
    }

    public void setDegradeChain(List<String> degradeChain) {
        this.degradeChain = degradeChain;
    }

    public ProfileStatsVO getStats() {
        return stats;
    }

    public void setStats(ProfileStatsVO stats) {
        this.stats = stats;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
