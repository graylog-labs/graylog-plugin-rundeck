package org.graylog2.alarmcallbacks.rundeck;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.Version;

import java.net.URI;

/**
 * Implement the PluginMetaData interface here.
 */
public class RundeckAlarmCallbackMetadata implements PluginMetaData {
    @Override
    public String getUniqueId() {
        return RundeckAlarmCallback.class.getCanonicalName();
    }

    @Override
    public String getName() {
        return "Rundeck Alarmcallback Plugin";
    }

    @Override
    public String getAuthor() {
        return "TORCH GmbH";
    }

    @Override
    public URI getURL() {
        return URI.create("http://www.torch.sh");
    }

    @Override
    public Version getVersion() {
        return new Version(0, 90, 0);
    }

    @Override
    public String getDescription() {
        return "Alarm callback plugin that triggeres a Rundeck job on stream alert.";
    }

    @Override
    public Version getRequiredVersion() {
        return new Version(0, 90, 0);
    }
}
