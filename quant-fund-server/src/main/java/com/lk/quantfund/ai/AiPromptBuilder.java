package com.lk.quantfund.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lk.quantfund.ai.model.AiAnalysisContext;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.enums.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AiPromptBuilder {

    private final ObjectMapper objectMapper;

    public AiPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String systemPrompt() {
        return """
                你是 QuantFund 的基金量化分析助手。必须只基于用户提供的结构化数据分析，不允许编造不存在的数据。
                输出必须是合法 JSON 对象，字段为 action、actionText、suggestAmount、suggestRatio、confidence、riskLevel、deadline、strategy、reasons、risks、dataSummary、finalConclusion。
                action 只能是 BUY、SELL、HOLD、CONVERT、WATCH。riskLevel 只能是 LOW、MEDIUM、HIGH。
                confidence 必须是 0 到 1 之间的小数；suggestRatio 使用百分比数值，例如 10 表示 10%。
                如果数据不足、估值延迟、策略信号不足或不确定，必须返回 WATCH。
                所有买卖建议必须保守、可解释、可复盘，且不得承诺收益。
                必须说明当日估值不等于最终净值，最终净值以晚间正式净值为准。
                如果 latestQuantSignal 不为空，AI 只能解释 latestQuantSignal，输出 action、actionText、suggestAmount、suggestRatio、riskLevel 不得与 latestQuantSignal 冲突。
                不要在 finalConclusion、reasons、risks、dataSummary 中重复输出免责声明或“用户自行到原基金平台操作”之类的固定话术。
                """;
    }

    public String userPrompt(AiAnalysisContext context) {
        try {
            return "请基于以下 QuantFund 结构化数据生成今日 15:00 前基金操作参考 JSON：\n"
                    + objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI analysis context serialization failed");
        }
    }
}
