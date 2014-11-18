package org.graylog2.alarmcallbacks.rundeck;

import com.google.common.collect.Lists;
import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginModule;

import java.util.Collection;

/**
 * Implement the Plugin interface here.
 */
public class RundeckAlarmCallbackPlugin implements Plugin {
    @Override
    public Collection<PluginModule> modules () {
        return Lists.newArrayList((PluginModule) new RundeckAlarmCallbackModule());
    }
}
