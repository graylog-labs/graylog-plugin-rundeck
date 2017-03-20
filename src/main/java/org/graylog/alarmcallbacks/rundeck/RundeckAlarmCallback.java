package org.graylog.alarmcallbacks.rundeck;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Maps;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.graylog2.plugin.MessageSummary;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RundeckAlarmCallback implements AlarmCallback {
    private static final Logger LOG = LoggerFactory.getLogger(RundeckAlarmCallback.class);

    private static final String CK_RUNDECK_URL = "rundeck_url";
    private static final String CK_JOB_ID = "job_id";
    private static final String CK_API_TOKEN = "api_token";
    private static final String CK_ARGS = "args";
    private static final String CK_FIELD_ARGS = "field_args";
    private static final String CK_AS_USER = "as_user";
    private static final String CK_FILTER_INCLUDE = "filter_include";
    private static final String CK_FILTER_EXCLUDE = "filter_exclude";
    private static final String CK_FILTER_EXCLUDE_PRECEDENCE = "exclude_precedence";

    private static final String API_VERSION = "12";

    private static final CharMatcher ARG_MATCHER = CharMatcher.noneOf("/&?").precomputed();
    private static final MediaType TEXT_XML = MediaType.parse("text/xml");
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final OkHttpClient httpClient;
    private Configuration configuration;

    @Inject
    public RundeckAlarmCallback(final OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void initialize(Configuration config) throws AlarmCallbackConfigurationException {
        this.configuration = config;
    }

    @Override
    public void call(Stream stream, AlertCondition.CheckResult result) throws AlarmCallbackException {
        String jobArguments = "";
        List<String> argumentList = Arrays.asList(configuration.getString(CK_ARGS, "").split("&"));
        List<String> includeFilters = Arrays.asList(configuration.getString(CK_FILTER_INCLUDE, "").split("&"));
        List<String> excludeFilters = Arrays.asList(configuration.getString(CK_FILTER_EXCLUDE, "").split("&"));

        if (!result.getMatchingMessages().isEmpty()) {
            // get fields from last message only
            MessageSummary lastMessage = result.getMatchingMessages().get(0);
            Map<String, Object> lastMessageFields = lastMessage.getFields();
            List<String> fieldsOfInterest = Arrays.asList(configuration.getString(CK_FIELD_ARGS, "").split(","));

            // append message fields as job argument
            for (Map.Entry<String, Object> arg : lastMessageFields.entrySet()) {
                if (fieldsOfInterest.contains(arg.getKey())) {
                    jobArguments = jobArguments + "-" + arg.getKey() + " '" + arg.getValue() + "' ";
                }
            }
            // append message fields with getter functions
            if (fieldsOfInterest.contains("source")) {
                jobArguments = jobArguments + "-source '" + lastMessage.getSource() + "' ";
            }
            if (fieldsOfInterest.contains("message")) {
                jobArguments = jobArguments + "-message '" + lastMessage.getMessage() + "' ";
            }
        }

        // append job arguments given by user
        for (String arg : argumentList) {
            String[] argumentPair = arg.split(":");
            if (argumentPair.length == 2) {
                jobArguments = jobArguments + "-" + argumentPair[0] + " '" + argumentPair[1] + "' ";
            }
        }

        try {
            final HttpUrl.Builder urlBuilder = HttpUrl.parse(configuration.getString(CK_RUNDECK_URL))
                    .newBuilder()
                    .addPathSegment("api")
                    .addPathSegment(API_VERSION)
                    .addPathSegment("job")
                    .addPathSegment(configuration.getString(CK_JOB_ID, "0"))
                    .addPathSegment("executions");

            for (String filter : includeFilters) {
                String[] filterPair = filter.split(":");
                if (filterPair.length == 2) {
                    String filterKey = filterPair[0];
                    String filterValue = filterPair[1];

                    if (!filterKey.trim().isEmpty() && !filterValue.trim().isEmpty()) {
                        urlBuilder.addQueryParameter(filterKey, filterValue);
                    }
                }
            }

            for (String filter : excludeFilters) {
                String[] filterPair = filter.split(":");
                if (filterPair.length == 2) {
                    String filterKey = filterPair[0];
                    String filterValue = filterPair[1];
                    if (!filterKey.trim().isEmpty() && !filterValue.trim().isEmpty()) {
                        urlBuilder.addQueryParameter("exclude-" + filterKey, filterValue);
                    }
                }
            }

            if (!configuration.getBoolean(CK_FILTER_EXCLUDE_PRECEDENCE)) {
                urlBuilder.addQueryParameter("exclude-precedence", Boolean.toString(configuration.getBoolean(CK_FILTER_EXCLUDE_PRECEDENCE)));
            }
            if (!jobArguments.isEmpty()) {
                urlBuilder.addQueryParameter("argString", jobArguments);
            }
            if (!configuration.getString(CK_AS_USER, "").trim().isEmpty()) {
                urlBuilder.addQueryParameter("asUser", configuration.getString(CK_AS_USER));
            }

            Request request = new Request.Builder()
                    .post(RequestBody.create(TEXT_XML, EMPTY_BYTE_ARRAY))
                    .url(urlBuilder.build())
                    .addHeader(HttpHeaders.ACCEPT, TEXT_XML.toString())
                    .addHeader("X-Rundeck-Auth-Token", configuration.getString(CK_API_TOKEN, ""))
                    .build();
            final Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Rundeck HTTP response headers: {}", response.headers().toString());
                    LOG.debug("Rundeck HTTP response body: {}", response.body().string());
                }
                throw new AlarmCallbackException("Failed to send alarm to Rundeck with HTTP response code: " + response.code());
            }
        } catch (Exception e) {
            throw new AlarmCallbackException("Failed to send alarm to Rundeck", e);
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Maps.transformEntries(configuration.getSource(), (key, value) -> CK_API_TOKEN.equals(key) ? "****" : value);
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

        if (configuration.stringIsSet(CK_FIELD_ARGS) && !ARG_MATCHER.matchesAllOf(configuration.getString(CK_FIELD_ARGS))) {
            throw new ConfigurationException("Field arguments should not contain /,?,&");
        }

        if (configuration.stringIsSet(CK_AS_USER) && !ARG_MATCHER.matchesAllOf(configuration.getString(CK_AS_USER))) {
            throw new ConfigurationException("Username should not contain /,?,&");
        }
    }

    @Override
    public ConfigurationRequest getRequestedConfiguration() {
        final ConfigurationRequest configurationRequest = new ConfigurationRequest();

        configurationRequest.addField(new TextField(
                CK_RUNDECK_URL,
                "Rundeck URL",
                "",
                "URL to your Rundeck installation",
                ConfigurationField.Optional.NOT_OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                CK_API_TOKEN,
                "API Token",
                "",
                "Rundeck API authentication token",
                ConfigurationField.Optional.NOT_OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                CK_JOB_ID,
                "Job ID",
                "",
                "ID of the Rundeck job to trigger in case of an alert",
                ConfigurationField.Optional.NOT_OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                CK_AS_USER,
                "As User",
                "",
                "Username who ran the job. Requires 'runAs' permission",
                ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                CK_FILTER_INCLUDE,
                "Include Node filter",
                "",
                "Run job on these nodes. Format is 'filter:value&filter:value'. Filter can be" +
                        " 'name', 'hostname', 'tags' or 'os-[name,family,arch,version]'",
                ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                CK_FILTER_EXCLUDE,
                "Exclude Node filter",
                "",
                "Exclude these nodes from job execution. Format is 'filter:value&filter:value'. Filter can be" +
                        " 'name', 'hostname', 'tags' or 'os-[name,family,arch,version]'",
                ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new BooleanField(
                CK_FILTER_EXCLUDE_PRECEDENCE, "Exclude precedence", true,
                "Whether exclusion filters take precedence")
        );
        configurationRequest.addField(new TextField(
                CK_ARGS, "Job arguments", "",
                "Argument string to pass to the job, of the form: 'key:value&key:value'",
                ConfigurationField.Optional.OPTIONAL)
        );
        configurationRequest.addField(new TextField(
                CK_FIELD_ARGS, "Field arguments", "",
                "Comma separated list of message fields which should append as a argument to the job.",
                ConfigurationField.Optional.OPTIONAL)
        );


        return configurationRequest;
    }

    @Override
    public String getName() {
        return "Rundeck alarm callback";
    }
}
