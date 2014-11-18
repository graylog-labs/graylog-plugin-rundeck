package org.graylog2.alarmcallbacks.rundeck;

import org.graylog2.plugin.PluginModule;

/**
 * Extend the PluginModule abstract class here to add you plugin to the system.
 */
public class RundeckAlarmCallbackModule extends PluginModule {
    @Override
    protected void configure() {
        registerPlugin(RundeckAlarmCallbackMetadata.class);
        addAlarmCallback(RundeckAlarmCallback.class);
    }
}
