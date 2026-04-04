package com.nego.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务信息领域模型。
 *
 * <p>该类用于描述一个可参与谈判的商品或服务，是整个谈判流程的起点。
 * 买方和卖方围绕这个对象中的基础信息进行报价、还价和成交判断。</p>
 *
 * <p>它主要承担以下职责：</p>
 * <ul>
 *     <li>描述当前正在谈判的服务标的</li>
 *     <li>提供卖方原始标价，作为后续策略计算的基础</li>
 *     <li>为前端展示、接口返回和谈判上下文提供必要信息</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInfo {

    /**
     * 服务唯一标识。
     *
     * <p>用于区分不同的服务或商品，便于前端选择、后端查询和历史记录关联。</p>
     */
    private String id;

    /**
     * 服务名称。
     *
     * <p>用于展示给用户，也会作为谈判上下文的一部分传递给智能体或策略层。</p>
     */
    private String name;

    /**
     * 服务描述。
     *
     * <p>用于补充说明服务内容、特点和适用场景，帮助谈判双方更准确地理解交易标的。</p>
     */
    private String description;

    /**
     * 卖方标价（原始价格）。
     *
     * <p>这是谈判开始时的参考价格，也是后续计算折扣、报价区间和成交价格的重要基准。</p>
     */
    private Double askingPrice;

    /**
     * 服务类别。
     *
     * <p>用于对服务进行分类，例如 API、数据分析、算力服务等，
     * 便于前端分类展示以及后续按类别做策略分析。</p>
     */
    private String category;
}
