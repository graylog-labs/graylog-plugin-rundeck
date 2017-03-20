package org.graylog.alarmcallbacks.rundeck;

import com.google.common.collect.ImmutableMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.graylog2.alerts.AbstractAlertCondition;
import org.graylog2.alerts.types.DummyAlertCondition;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.alarms.callbacks.AlarmCallbackException;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.streams.Stream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.mock;

public class RundeckAlarmCallbackIT {
    private static final String RUNDECK_HOST = System.getProperty("rundeck.host", "127.0.0.1");
    private static final int RUNDECK_PORT = Integer.getInteger("rundeck.port", 4440);
    private static final String TOKEN_HOST = System.getProperty("token.host", "127.0.0.1");
    private static final int TOKEN_PORT = Integer.getInteger("token.port", 12345);

    private static final ImmutableMap<String, Object> VALID_CONFIG_SOURCE = ImmutableMap.<String, Object>builder()
            .put("rundeck_url", "http://" + RUNDECK_HOST + ":" + RUNDECK_PORT)
            .build();

    private static String token = "";

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private final OkHttpClient okHttpClient = new OkHttpClient();

    private RundeckAlarmCallback alarmCallback;

    @BeforeClass
    public static void initialize() throws IOException {
        final Request request = new Request.Builder()
                .get()
                .url("http://" + TOKEN_HOST + ":" + TOKEN_PORT + "/token.txt")
                .build();
        final Response response = new OkHttpClient().newCall(request).execute();
        token = response.body().string().trim();
    }

    @Before
    public void setUp() {
        alarmCallback = new RundeckAlarmCallback(okHttpClient);
    }

    @Test
    public void testCallSucceedsWithSimpleJob() throws Exception {
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
        configuration.setString("api_token", token);
        configuration.setString("job_id", "804f107a-cafe-babe-0000-deadbeef0000");

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);
    }

    @Test
    public void testCallFailsWithDisabledJob() throws Exception {
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
        configuration.setString("api_token", token);
        configuration.setString("job_id", "804f107a-cafe-babe-0000-deadbeef0001");

        expectedException.expect(AlarmCallbackException.class);
        expectedException.expectMessage("Failed to send alarm to Rundeck");

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);
    }

    @Test
    public void testCallSucceedsWithFailingJob() throws Exception {
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
        configuration.setString("api_token", token);
        configuration.setString("job_id", "804f107a-cafe-babe-0000-deadbeef0002");

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);
    }

    @Test
    public void testCallSucceedsWithParameterizedJob() throws Exception {
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
        configuration.setString("api_token", token);
        configuration.setString("job_id", "804f107a-cafe-babe-0000-deadbeef0003");
        configuration.setString("args", "custom-arg:Test");

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);
    }

    @Test
    public void testCallFailsWithParameterizedJobWithoutParameters() throws Exception {
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
        configuration.setString("api_token", token);
        configuration.setString("job_id", "804f107a-cafe-babe-0000-deadbeef0003");

        expectedException.expect(AlarmCallbackException.class);
        expectedException.expectMessage("Failed to send alarm to Rundeck");

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);
    }

    @Test
    public void testCallFailsWithUnknownJob() throws Exception {
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
        configuration.setString("api_token", token);
        configuration.setString("job_id", "unknown");

        expectedException.expect(AlarmCallbackException.class);
        expectedException.expectMessage("Failed to send alarm to Rundeck");

        alarmCallback.initialize(configuration);
        alarmCallback.checkConfiguration();
        alarmCallback.call(mockStream, checkResult);
    }
}
