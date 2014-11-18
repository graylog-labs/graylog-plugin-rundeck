package org.graylog2.alarmcallbacks.rundeck;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Maps;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.alarms.callbacks.AlarmCallback;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackConfigurationException;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackException;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.BooleanField;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.streams.Stream;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class RundeckAlarmCallback implements AlarmCallback{
    private static final String CK_RUNDECK_URL = "rundeck_url";
    private static final String CK_JOB_ID      = "job_id";
    private static final String CK_API_TOKEN   = "api_token";
    private static final String CK_ARGS        = "args";
    private static final String CK_FILTER_INCLUDE_NAME       = "filter_include_node_name";
    private static final String CK_FILTER_INCLUDE_HOSTNAME   = "filter_include_hostname";
    private static final String CK_FILTER_INCLUDE_TAGS       = "filter_include_tags";
    private static final String CK_FILTER_EXCLUDE_NAME       = "filter_exclude_node_name";
    private static final String CK_FILTER_EXCLUDE_HOSTNAME   = "filter_exclude_hostname";
    private static final String CK_FILTER_EXCLUDE_TAGS       = "filter_exclude_tags";
    private static final String CK_FILTER_EXCLUDE_PRECEDENCE = "exclude_precedence";

    private static final String API_VERSION = "12";

    private static final CharMatcher ARG_MATCHER = CharMatcher.noneOf("/&?").precomputed();

    private Configuration configuration;

    @Override
    public void initialize(Configuration config) throws AlarmCallbackConfigurationException{
        this.configuration = config;
    }

    @Override
    public void call(Stream stream, AlertCondition.CheckResult result) throws AlarmCallbackException {
        try {
            Client client = ClientBuilder.newClient();

            WebTarget webTarget = client.target(configuration.getString(CK_RUNDECK_URL) +
                    "/api/" + API_VERSION +
                    "/job/" + configuration.getString(CK_JOB_ID) +
                    "/executions");

            if(!configuration.getString(CK_FILTER_INCLUDE_NAME).trim().isEmpty()) {
                webTarget = webTarget.queryParam("name", configuration.getString(CK_FILTER_INCLUDE_NAME));
            }
            if(!configuration.getString(CK_FILTER_INCLUDE_HOSTNAME).trim().isEmpty()) {
                webTarget = webTarget.queryParam("hostname", configuration.getString(CK_FILTER_INCLUDE_HOSTNAME));
            }
            if(!configuration.getString(CK_FILTER_INCLUDE_TAGS).trim().isEmpty()) {
                webTarget = webTarget.queryParam("tags", configuration.getString(CK_FILTER_INCLUDE_TAGS));
            }
            if(!configuration.getString(CK_FILTER_EXCLUDE_NAME).trim().isEmpty()) {
                webTarget = webTarget.queryParam("exclude-name", configuration.getString(CK_FILTER_EXCLUDE_NAME));
            }
            if(!configuration.getString(CK_FILTER_EXCLUDE_HOSTNAME).trim().isEmpty()) {
                webTarget = webTarget.queryParam("exclude-hostname", configuration.getString(CK_FILTER_EXCLUDE_HOSTNAME));
            }
            if(!configuration.getString(CK_FILTER_EXCLUDE_TAGS).trim().isEmpty()) {
                webTarget = webTarget.queryParam("exclude-tags", configuration.getString(CK_FILTER_EXCLUDE_TAGS));
            }
            if(!configuration.getBoolean(CK_FILTER_EXCLUDE_PRECEDENCE)) {
                webTarget = webTarget.queryParam("exclude-precedence",
                        Boolean.toString(configuration.getBoolean(CK_FILTER_EXCLUDE_PRECEDENCE)));
            }
            if(!configuration.getString(CK_ARGS).trim().isEmpty()) {
                webTarget = webTarget.queryParam("argString", configuration.getString(CK_ARGS));
            }

            Invocation.Builder invocationBuilder = webTarget.request(MediaType.TEXT_XML_TYPE);
            invocationBuilder.header("X-Rundeck-Auth-Token", configuration.getString(CK_API_TOKEN));
            Response response = invocationBuilder.post(Entity.entity("", MediaType.TEXT_XML_TYPE));

            if (response.getStatus() != 200) {
                throw new AlarmCallbackException("Failed : HTTP error code : "
                        + response.getStatus());
            }

        } catch (Exception e) {
            throw new AlarmCallbackException();
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Maps.transformEntries(configuration.getSource(), new Maps.EntryTransformer<String, Object, Object>() {
            @Override
            public Object transformEntry(String key, Object value) {
                if (CK_API_TOKEN.equals(key)) {
                    return "****";
                }
                return value;
            }
        });
    }

    @Override
    public void checkConfiguration() throws ConfigurationException {
        if (configuration.stringIsSet(CK_RUNDECK_URL)) {
            try {
                final URI rundeckUri = new URI(configuration.getString(CK_RUNDECK_URL));

                if (!"http".equals(rundeckUri.getScheme()) && !"https".equals(rundeckUri.getScheme())) {
                    throw new ConfigurationException(CK_RUNDECK_URL + " must be a valid HTTP or HTTPS URL.");
                }
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Couldn't parse " + CK_RUNDECK_URL + " correctly.", e);
            }
        }

        if (!configuration.stringIsSet(CK_JOB_ID)) {
            throw new ConfigurationException(CK_JOB_ID + " is mandatory and must not be empty.");
        }

        if (!configuration.stringIsSet(CK_API_TOKEN)) {
            throw new ConfigurationException(CK_API_TOKEN + " is mandatory and must not be empty.");
        }

        if (configuration.stringIsSet(CK_ARGS) && !ARG_MATCHER.matchesAllOf(configuration.getString(CK_ARGS))) {
            throw new ConfigurationException("Job arguments should not contain /,?,&");
        }
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        final ConfigurationRequest configurationRequest = new ConfigurationRequest();

        configurationRequest.addField(new TextField(
                        CK_RUNDECK_URL, "Rundeck URL", "",
                        "URL to your Rundeck installation",
                        ConfigurationField.Optional.NOT_OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_JOB_ID, "Job ID", "",
                        "ID of the Rundeck job to trigger in case of an alert",
                        ConfigurationField.Optional.NOT_OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_API_TOKEN, "API Token", "",
                        "Rundeck API authentication token",
                        ConfigurationField.Optional.NOT_OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_FILTER_INCLUDE_NAME, "Node filter: name", "",
                        "Include nodes by name",
                        ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_FILTER_INCLUDE_HOSTNAME, "Node filter: hostname", "",
                        "Include nodes by hostname",
                        ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_FILTER_INCLUDE_TAGS, "Node filter: tags", "",
                        "Include nodes by tags",
                        ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_FILTER_EXCLUDE_NAME, "Node filter: exclude-name", "",
                        "Exclude nodes by name",
                        ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_FILTER_EXCLUDE_HOSTNAME, "Node filter: exclude-hostname", "",
                        "Exclude nodes by hostname",
                        ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                        CK_FILTER_EXCLUDE_TAGS, "Node filter: exclude-tags", "",
                        "Exclude nodes by tags",
                        ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new BooleanField(
                        CK_FILTER_EXCLUDE_PRECEDENCE, "Exclude precedence", true,
                        "Whether exclusion filters take precedence")
        );
        configurationRequest.addField(new TextField(
                        CK_ARGS, "Job arguments", "",
                        "Choose execution options",
                        ConfigurationField.Optional.OPTIONAL)
        );

        return configurationRequest;
    }

    @Override
    public String getName() {
        return "Rundeck alarm callback";
    }
}
