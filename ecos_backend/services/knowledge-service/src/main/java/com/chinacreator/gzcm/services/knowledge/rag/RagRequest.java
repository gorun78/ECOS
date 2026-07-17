package com.chinacreator.gzcm.services.knowledge.rag;

public class RagRequest {
    private String query;
    private int topK = 5;
    private double threshold = 0.7;
    private boolean useGraph = true;
    private boolean useVector = true;

    public RagRequest() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public boolean isUseGraph() { return useGraph; }
    public void setUseGraph(boolean useGraph) { this.useGraph = useGraph; }
    public boolean isUseVector() { return useVector; }
    public void setUseVector(boolean useVector) { this.useVector = useVector; }
}
