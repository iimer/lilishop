package cn.lili.common.trigger;

import cn.hutool.json.JSONUtil;
import cn.lili.common.cache.Cache;
import cn.lili.common.rocketmq.RocketmqSendCallbackBuilder;
import cn.lili.common.trigger.delay.PromotionDelayQueue;
import cn.lili.common.trigger.interfaces.TimeTrigger;
import cn.lili.common.trigger.model.TimeTriggerMsg;
import cn.lili.common.trigger.util.TimeTriggerUtil;
import cn.lili.common.utils.DateUtil;
import cn.lili.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 延时任务实现
 *
 * @author paulG
 * @since 2020/11/5
 **/
@Component
@Slf4j
public class RocketmqTimerTrigger implements TimeTrigger {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private Cache<Integer> cache;
    @Autowired
    private PromotionDelayQueue promotionDelayQueue;


    @Override
    public void add(TimeTriggerMsg timeTriggerMsg) {
        this.addExecute(timeTriggerMsg.getTriggerExecutor(), timeTriggerMsg.getParam(), timeTriggerMsg.getTriggerTime(), timeTriggerMsg.getUniqueKey(), timeTriggerMsg.getTopic());
    }

    /**
     * 添加延时任务
     *
     * @param executorName 执行器beanId
     * @param param        执行参数
     * @param triggerTime  执行时间 时间戳 秒为单位
     * @param uniqueKey    如果是一个 需要有 修改/取消 延时任务功能的延时任务，<br/>
     *                     请填写此参数，作为后续删除，修改做为唯一凭证 <br/>
     *                     建议参数为：COUPON_{ACTIVITY_ID} 例如 coupon_123<br/>
     *                     业务内全局唯一
     * @param topic        rocketmq topic
     */
    public void addExecute(String executorName, Object param, Long triggerTime, String uniqueKey, String topic) {

        TimeTriggerMsg timeTriggerMsg = new TimeTriggerMsg(executorName, triggerTime, param, uniqueKey, topic);
        Message<TimeTriggerMsg> message = MessageBuilder.withPayload(timeTriggerMsg).build();
        log.info("延时任务发送信息：{}", message);
        this.rocketMQTemplate.asyncSend(topic, message, RocketmqSendCallbackBuilder.commonCallback());
    }

    @Override
    public void addDelay(TimeTriggerMsg timeTriggerMsg, int delayTime) {
        //执行器唯一key
        String uniqueKey = timeTriggerMsg.getUniqueKey();
        if (StringUtils.isEmpty(uniqueKey)) {
            uniqueKey = StringUtils.getRandStr(10);
        }
        //执行任务key
        String generateKey = TimeTriggerUtil.generateKey(timeTriggerMsg.getTriggerExecutor(), timeTriggerMsg.getTriggerTime(), uniqueKey);
        this.cache.put(generateKey, 1);
        //设置延时任务
        if (Boolean.TRUE.equals(promotionDelayQueue.addJob(JSONUtil.toJsonStr(timeTriggerMsg), delayTime))) {
            log.info("add Redis key {}", generateKey);
            log.info("定时执行在【" + DateUtil.toString(timeTriggerMsg.getTriggerTime(), "yyyy-MM-dd HH:mm:ss") + "】，消费【" + timeTriggerMsg.getParam().toString() + "】");
        } else {
            log.error("延时任务添加失败:{}", timeTriggerMsg);
        }
    }

    @Override
    public void edit(String executorName, Object param, Long oldTriggerTime, Long triggerTime, String uniqueKey, int delayTime, String topic) {
        this.delete(executorName, oldTriggerTime, uniqueKey, topic);
        this.addDelay(new TimeTriggerMsg(executorName, triggerTime, param, uniqueKey, topic), delayTime);
    }

    @Override
    public void delete(String executorName, Long triggerTime, String uniqueKey, String topic) {
        String generateKey = TimeTriggerUtil.generateKey(executorName, triggerTime, uniqueKey);
        log.info("删除延时任务{}", generateKey);
        this.cache.remove(generateKey);
    }
}
