package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import java.util.List;

public interface CausalReasonerService {
    List<CausalEdge> inferCausalGraph(String domain);
    double estimateCausalEffect(String source, String target);
}
