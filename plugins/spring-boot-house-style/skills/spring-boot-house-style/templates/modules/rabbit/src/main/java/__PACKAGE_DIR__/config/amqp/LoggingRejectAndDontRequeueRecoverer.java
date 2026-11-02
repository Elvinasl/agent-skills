package __PACKAGE__.config.amqp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

@Slf4j
public class LoggingRejectAndDontRequeueRecoverer extends RejectAndDontRequeueRecoverer {

    @Override
    public void recover(Message message, Throwable cause) {
        log.error("amqp message {} failed after retries, sending to DLQ", message.getMessageProperties(), cause);
        super.recover(message, cause);
    }
}
