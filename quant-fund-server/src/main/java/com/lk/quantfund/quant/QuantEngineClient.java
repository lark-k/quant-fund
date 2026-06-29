package com.lk.quantfund.quant;

import com.lk.quantfund.dto.quant.QuantAnalyzeRequest;
import com.lk.quantfund.dto.quant.QuantAnalyzeResponse;
import com.lk.quantfund.vo.quant.QuantEngineHealthVO;
import java.util.List;

public interface QuantEngineClient {

    QuantAnalyzeResponse analyze(QuantAnalyzeRequest request);

    List<QuantAnalyzeResponse> analyzeBatch(List<QuantAnalyzeRequest> requests);

    QuantEngineHealthVO health();
}
