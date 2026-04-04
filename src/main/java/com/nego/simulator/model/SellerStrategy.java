package com.nego.simulator.model;

/**
 * 卖方策略枚举。
 *
 * <p>这个枚举用于定义卖方在谈判过程中的不同出价风格。
 * 当前项目不引入复杂的策略模式，而是使用枚举来封装策略参数，
 * 这样更适合当前项目规模，也更方便在运行时做策略切换和组合对比。</p>
 *
 * <p>每个卖方策略主要描述以下几类信息：</p>
 * <ul>
 *     <li>初始要价比例：首轮通常以商品标价的多少比例开始报价</li>
 *     <li>每轮降价范围：在多轮谈判中每次让价的大致区间</li>
 *     <li>最低接受比例：卖方最多愿意降到商品标价的哪个比例</li>
 *     <li>策略说明：用于前端展示或拼接到 Agent 的上下文提示中</li>
 * </ul>
 *
 * <p>在后续实现中，该枚举通常会配合：</p>
 * <ul>
 *     <li>{@code NegotiationConfig}：指定本次谈判使用哪种卖方策略</li>
 *     <li>{@code SellerTools}：在工具方法中根据策略参数约束卖方报价</li>
 *     <li>{@code NegotiationService}：批量跑不同买卖组合做效果对比</li>
 * </ul>
 */
public enum SellerStrategy {

    /**
     * 高端坚挺型卖方策略。
     *
     * <p>特点：</p>
     * <ul>
     *     <li>首轮按 100% 标价开始，不主动打折</li>
     *     <li>每轮只做较小幅度让步，强调服务质量和价值</li>
     *     <li>最低底线为原始标价的 55%</li>
     * </ul>
     *
     * <p>适合表达“价格坚挺、强调品质”的卖方风格。</p>
     */
    PREMIUM(1.00, 0.06, 0.09, 0.55, "坚挺型：强调品质，缓慢降价"),

    /**
     * 灵活促成型卖方策略。
     *
     * <p>特点：</p>
     * <ul>
     *     <li>首轮从 95% 标价开始，愿意先做一点让利</li>
     *     <li>每轮降价幅度更大，更强调快速成交</li>
     *     <li>最低底线为原始标价的 45%</li>
     * </ul>
     *
     * <p>适合表达“愿意快速让利、优先促成交易”的卖方风格。</p>
     */
    FLEXIBLE(0.95, 0.10, 0.15, 0.45, "灵活型：快速让利，促成交易");

    /**
     * 首轮要价比例。
     *
     * <p>表示卖方在第一轮通常会以商品标价的多少比例开始报价。
     * 例如：
     * <ul>
     *     <li>1.00 表示按原始标价报价</li>
     *     <li>0.95 表示按原始标价的 95% 报价</li>
     * </ul>
     */
    private final double openRatio;

    /**
     * 每轮最小降价比例。
     *
     * <p>表示卖方在多轮谈判中每次至少愿意让出多少比例。
     * 该值通常与 {@link #maxStep} 一起用于构造一个降价区间。</p>
     */
    private final double minStep;

    /**
     * 每轮最大降价比例。
     *
     * <p>表示卖方在多轮谈判中每次最多愿意让出多少比例。</p>
     */
    private final double maxStep;

    /**
     * 最低接受比例（底价比例）。
     *
     * <p>表示卖方在本策略下通常不会低于原始标价的这个比例，
     * 可用于工具方法中实现硬边界校验，避免模型给出过低报价。</p>
     */
    private final double floor;

    /**
     * 策略说明。
     *
     * <p>用于描述该策略的整体风格，便于前端展示，
     * 也可以在构造 Agent 上下文时拼接到提示语中。</p>
     */
    private final String description;

    SellerStrategy(double openRatio, double minStep, double maxStep, double floor, String description) {
        this.openRatio = openRatio;
        this.minStep = minStep;
        this.maxStep = maxStep;
        this.floor = floor;
        this.description = description;
    }

    /**
     * 获取首轮要价比例。
     *
     * @return 首轮要价比例
     */
    public double getOpenRatio() {
        return openRatio;
    }

    /**
     * 获取每轮最小降价比例。
     *
     * @return 最小降价比例
     */
    public double getMinStep() {
        return minStep;
    }

    /**
     * 获取每轮最大降价比例。
     *
     * @return 最大降价比例
     */
    public double getMaxStep() {
        return maxStep;
    }

    /**
     * 获取最低接受比例。
     *
     * @return 卖方底价比例
     */
    public double getFloor() {
        return floor;
    }

    /**
     * 获取策略说明。
     *
     * @return 策略描述文本
     */
    public String getDescription() {
        return description;
    }
}
