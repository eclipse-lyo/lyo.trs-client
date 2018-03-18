package org.eclipse.lyo.trs.consumer.mqtt;

import org.apache.jena.rdf.model.Model;
import org.eclipse.lyo.core.trs.ChangeEvent;

/**
 * Created on 2018-03-17
 *
 * @author Andrew Berezovskyi (andriib@kth.se)
 * @version $version-stub$
 * @since 0.0.1
 */
public class ChangeEventMessage {
    private final Model       mqttMessageModel;
    private final ChangeEvent changeEvent;
    private final boolean     isFat;

    public ChangeEventMessage(final Model mqttMessageModel, final ChangeEvent changeEvent,
            final boolean isFat) {
        this.mqttMessageModel = mqttMessageModel;
        this.changeEvent = changeEvent;
        this.isFat = isFat;
    }

    public ChangeEvent getChangeEvent() {
        return changeEvent;
    }

    public Model getMqttMessageModel() {
        return mqttMessageModel;
    }

    public boolean isFat() {
        return isFat;
    }
}
