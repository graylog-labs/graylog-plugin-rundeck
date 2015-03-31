package org.graylog.alarmcallbacks.rundeck;

import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.PluginModule;

import java.util.Collection;
import java.util.Collections;

public class RundeckAlarmCallbackPlugin implements Plugin {
    @Override
    public Collection<PluginModule> modules() {
        return Collections.<PluginModule>singleton(new RundeckAlarmCallbackModule());
    }

    @Override
    public PluginMetaData metadata() {
        return new RundeckAlarmCallbackMetadata();
    }
}
