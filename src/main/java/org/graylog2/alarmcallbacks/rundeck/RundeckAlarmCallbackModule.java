package org.graylog2.alarmcallbacks.rundeck;

import org.graylog2.plugin.PluginModule;

public class RundeckAlarmCallbackModule extends PluginModule {
    @Override
    protected void configure() {
        addAlarmCallback(RundeckAlarmCallback.class);
    }
}
