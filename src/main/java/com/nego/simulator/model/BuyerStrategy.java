package com.nego.simulator.model;

/**
 * 买方策略枚举。
 *
 * <p>该枚举用于定义买方在谈判中的不同出价风格。
 * 当前版本不引入复杂的策略模式，而是使用枚举来封装策略参数，
 * 因为本项目中的买方策略差异主要体现在“出价比例和加价速度”上，
 * 用枚举表达更简单，也更适合当前项目规模。</p>
 *
 * <p>每一种买方策略都包含以下信息：</p>
 * <ul>
 *     <li>首轮出价比例：第一次报价通常相对于原始价格的多少</li>
 *     <li>每轮最小加价比例：谈判继续时，最低会增加多少</li>
 *     <li>每轮最大加价比例：谈判继续时，最高会增加多少</li>
 *     <li>预算上限比例：买方最多愿意接受到原始价格的多少</li>
 *     <li>策略说明：用于前端展示或作为 Agent Prompt 的描述信息</li>
 * </ul>
 *
 * <p>后续在 Agent 工具类中，会把这些参数注入进去，
 * 让不同策略对报价行为产生实际影响。</p>
 */
public enum BuyerStrategy {

    /**
     * 激进型买方策略。
     *
     * <p>特点：</p>
     * <ul>
     *     <li>首轮报价较低，通常从原价的 40% 开始</li>
     *     <li>后续每轮逐步加价，但整体压价较强</li>
     *     <li>最高预算不超过原价的 85%</li>
     * </ul>
     *
     * <p>适合强调成本控制、压价意愿较强的买方角色。</p>
     */
    AGGRESSIVE(0.40, 0.08, 0.12, 0.85, "激进型：低开高走，压价较强，强调成本控制"),

    /**
     * 保守型买方策略。
     *
     * <p>特点：</p>
     * <ul>
     *     <li>首轮报价相对更高，通常从原价的 60% 开始</li>
     *     <li>每轮加价幅度较温和，更容易快速接近成交区间</li>
     *     <li>最高预算不超过原价的 90%</li>
     * </ul>
     *
     * <p>适合更注重谈判效率、愿意更快推进交易的买方角色。</p>
     */
    CONSERVATIVE(0.60, 0.05, 0.08, 0.90, "保守型：温和开价，稳步推进，优先促成交易");

    /**
     * 首轮出价比例。
     *
     * <p>例如 0.40 表示买方第一轮出价通常是原始价格的 40%。</p>
     */
    private final double openRatio;

    /**
     * 每轮最小加价比例。
     *
     * <p>用于表示在继续谈判时，买方至少会在当前基础上提高多少比例。</p>
     */
    private final double minStep;

    /**
     * 每轮最大加价比例。
     *
     * <p>用于表示在继续谈判时，买方最多会在当前基础上提高多少比例。</p>
     */
    private final double maxStep;

    /**
     * 预算上限比例。
     *
     * <p>表示买方在当前策略下，最多愿意接受原始价格的多少比例。</p>
     */
    private final double ceiling;

    /**
     * 策略文字说明。
     *
     * <p>用于前端展示、日志记录，或传递给 Agent 作为上下文说明。</p>
     */
    private final String description;

    BuyerStrategy(double openRatio, double minStep, double maxStep, double ceiling, String description) {
        this.openRatio = openRatio;
        this.minStep = minStep;
        this.maxStep = maxStep;
        this.ceiling = ceiling;
        this.description = description;
    }

    /**
     * 获取首轮出价比例。
     *
     * @return 首轮出价比例
     */
    public double getOpenRatio() {
        return openRatio;
    }

    /**
     * 获取每轮最小加价比例。
     *
     * @return 每轮最小加价比例
     */
    public double getMinStep() {
        return minStep;
    }

    /**
     * 获取每轮最大加价比例。
     *
     * @return 每轮最大加价比例
     */
    public double getMaxStep() {
        return maxStep;
    }

    /**
     * 获取预算上限比例。
     *
     * @return 预算上限比例
     */
    public double getCeiling() {
        return ceiling;
    }

    /**
     * 获取策略说明。
     *
     * @return 策略说明文字
     */
    public String getDescription() {
        return description;
    }
}
