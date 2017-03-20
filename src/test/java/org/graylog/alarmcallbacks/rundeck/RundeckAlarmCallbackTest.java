package org.graylog.alarmcallbacks.rundeck;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.graylog2.alerts.AbstractAlertCondition;
import org.graylog2.alerts.types.DummyAlertCondition;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackConfigurationException;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.streams.Stream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class RundeckAlarmCallbackTest {
    private static final ImmutableMap<String, Object> VALID_CONFIG_SOURCE = ImmutableMap.<String, Object>builder()
            .put("rundeck_url", "http://rundeck.example.com")
            .put("job_id", "test-job-id")
            .put("api_token", "test_api_token")
            .put("args", "-test arg")
            .put("filter_include", "name:node01,tags:linux")
            .put("filter_exclude", "name:node02,tags:windows")
            .put("exclude_precedence", true)
            .build();

    private final OkHttpClient okHttpClient = new OkHttpClient();

    private RundeckAlarmCallback alarmCallback;

    @Before
    public void setUp() {
        alarmCallback = new RundeckAlarmCallback(okHttpClient);
    }

    @Test
    public void testInitialize() throws AlarmCallbackConfigurationException {
        final Configuration configuration = new Configuration(VALID_CONFIG_SOURCE);
        alarmCallback.initialize(configuration);
    }

    @Test
    public void testGetAttributes() throws AlarmCallbackConfigurationException {
        final Configuration configuration = new Configuration(VALID_CONFIG_SOURCE);
        alarmCallback.initialize(configuration);

        final Map<String, Object> attributes = alarmCallback.getAttributes();
        assertThat(attributes.keySet(), hasItems("rundeck_url", "job_id", "api_token", "args",
                "filter_include", "filter_exclude", "exclude_precedence"));
        assertThat((String) attributes.get("api_token"), equalTo("****"));
    }

    @Test
    public void checkConfigurationSucceedsWithValidConfiguration()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        alarmCallback.initialize(new Configuration(VALID_CONFIG_SOURCE));
        alarmCallback.checkConfiguration();
    }

    @Test
    public void testCallSucceedsWithValidConfiguration() throws Exception {
        final MockResponse mockResponse = new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML)
                .setResponseCode(200);
        final MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.enqueue(mockResponse);
        mockWebServer.start();

        final Stream mockStream = mock(Stream.class);
        final AlertCondition.CheckResult checkResult = new AbstractAlertCondition.NegativeCheckResult(
                new DummyAlertCondition(
                        mockStream,
                        "id",
                        new DateTime(2017, 3, 20, 0, 0, DateTimeZone.UTC),
                        "user",
                        Collections.emptyMap())
        );

        final Configuration configuration = new Configuration(VALID_CONFIG_SOURCE);
        configuration.setString("rundeck_url", mockWebServer.url("/").toString());

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);

        final RecordedRequest recordedRequest = mockWebServer.takeRequest(10, TimeUnit.SECONDS);
        assertEquals(1, mockWebServer.getRequestCount());
        assertEquals("POST /api/12/job/test-job-id/executions HTTP/1.1", recordedRequest.getRequestLine());
        assertEquals("text/xml", recordedRequest.getHeader("Accept"));
        assertEquals("test_api_token", recordedRequest.getHeader("X-Rundeck-Auth-Token"));
        assertEquals(0L, recordedRequest.getBodySize());

        mockWebServer.shutdown();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfJobIdIsMissing()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        alarmCallback.initialize(validConfigurationWithout("job_id"));
        alarmCallback.checkConfiguration();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfApiTokenIsMissing()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        alarmCallback.initialize(validConfigurationWithout("api_token"));
        alarmCallback.checkConfiguration();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfArgsContainsInvalidCharacters()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        final Map<String, Object> configSource = ImmutableMap.<String, Object>builder()
                .put("job_id", "TEST-job-id")
                .put("api_token", "TEST_api_token")
                .put("args", "&-invalid arg")
                .build();

        alarmCallback.initialize(new Configuration(configSource));
        alarmCallback.checkConfiguration();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfFieldArgsContainsInvalidCharacters()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        final Map<String, Object> configSource = ImmutableMap.<String, Object>builder()
                .put("job_id", "TEST-job-id")
                .put("api_token", "TEST_api_token")
                .put("field_args", "&-invalid field arg")
                .build();

        alarmCallback.initialize(new Configuration(configSource));
        alarmCallback.checkConfiguration();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfUsernameContainsInvalidCharacters()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        final Map<String, Object> configSource = ImmutableMap.<String, Object>builder()
                .put("job_id", "TEST-job-id")
                .put("api_token", "TEST_api_token")
                .put("as_user", "invalid&user")
                .build();

        alarmCallback.initialize(new Configuration(configSource));
        alarmCallback.checkConfiguration();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfRundeckUrlIsInvalid()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        final Map<String, Object> configSource = ImmutableMap.<String, Object>builder()
                .put("job_id", "TEST-job-id")
                .put("api_token", "TEST_api_token")
                .put("rundeck_url", "Definitely$$Not#A!!URL")
                .build();

        alarmCallback.initialize(new Configuration(configSource));
        alarmCallback.checkConfiguration();
    }

    @Test(expected = ConfigurationException.class)
    public void checkConfigurationFailsIfRundeckUrlIsNotHttpOrHttps()
            throws AlarmCallbackConfigurationException, ConfigurationException {
        final Map<String, Object> configSource = ImmutableMap.<String, Object>builder()
                .put("job_id", "TEST-job-id")
                .put("api_token", "TEST_api_token")
                .put("rundeck_url", "ftp://example.net")
                .build();

        alarmCallback.initialize(new Configuration(configSource));
        alarmCallback.checkConfiguration();
    }

    @Test
    public void testGetRequestedConfiguration() {
        assertThat(alarmCallback.getRequestedConfiguration().asList().keySet(),
                hasItems("rundeck_url", "job_id", "api_token", "args",
                        "filter_include", "filter_exclude", "exclude_precedence"));
    }

    @Test
    public void testGetName() {
        assertThat(alarmCallback.getName(), equalTo("Rundeck alarm callback"));
    }

    private Configuration validConfigurationWithout(final String key) {
        return new Configuration(Maps.filterEntries(VALID_CONFIG_SOURCE, new Predicate<Map.Entry<String, Object>>() {
            @Override
            public boolean apply(Map.Entry<String, Object> input) {
                return key.equals(input.getKey());
            }
        }));
    }
}
