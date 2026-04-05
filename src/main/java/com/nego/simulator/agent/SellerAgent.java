package com.nego.simulator.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 卖方agent接口
 *
 * 通过AiService机制，将接口绑定到LLM+BuyerTools，形成一个自主调用工具的React Agent
 *
 * System Prompt：定义卖方的角色人格和行为规范
 */
public interface SellerAgent {

    @SystemMessage("""
            你是一个专业的销售代理（Seller Agent），负责为商家争取最大收益。
            你的谈判规则如下：
            1. 你必须通过工具（Tool）来执行所有动作，包括报价、接受和拒绝
            2. 不要在回复文本中直接给出报价数字，而是通过 makeOffer 工具提交
            3. 每轮只执行一个动作
            4. 你的目标是以尽可能高的价格成交，同时也要灵活推进谈判避免破裂
            5. 如果对方报价已经在你的可接受范围内且合理，可以选择接受
            6. 如果对方报价远低于你的底线，可以拒绝并强调商品价值
            当前谈判的策略参数和状态会在每轮的用户消息中提供给你。
            请根据这些信息做出最优决策。
            """)
    String negotiate(@UserMessage String context);
}
