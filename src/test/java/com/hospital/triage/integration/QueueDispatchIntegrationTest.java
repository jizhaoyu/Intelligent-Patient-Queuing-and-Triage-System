package com.hospital.triage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.modules.queue.entity.po.QueueTicket;
import com.hospital.triage.modules.queue.mapper.QueueTicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
class QueueDispatchIntegrationTest {

    static {
        System.setProperty("docker.available", String.valueOf(isDockerAvailable()));
    }

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueueTicketMapper queueTicketMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldLoginAndFinishQueueLifecycle() throws Exception {
        String token = login("admin", "password");
        assertThat(jdbcTemplate.queryForObject("select count(*) from clinic_dept", Integer.class)).isGreaterThanOrEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sys_user", Integer.class)).isGreaterThanOrEqualTo(4);

        Long patientId = readId(postJson("/api/patients", token, """
                {
                  \"patientName\": \"张三\",
                  \"gender\": \"MALE\",
                  \"birthDate\": \"1990-01-01\",
                  \"phone\": \"13800000001\"
                }
                """));

        Long visitId = readId(postJson("/api/visits", token, """
                {
                  \"patientId\": %d,
                  \"chiefComplaint\": \"胸痛伴呼吸困难\"
                }
                """.formatted(patientId)));

        postNoData("/api/visits/%d/arrive".formatted(visitId), token);

        Long assessmentId = readId(postJson("/api/triage/assessments", token, """
                {
                  \"visitId\": %d,
                  \"symptomTags\": \"胸痛,呼吸困难\",
                  \"heartRate\": 120,
                  \"bloodOxygen\": 88,
                  \"assessor\": \"nurse-a\"
                }
                """.formatted(visitId)));

        String ticketNo = readText(postJson("/api/queues/tickets", token, """
                {
                  \"visitId\": %d,
                  \"assessmentId\": %d,
                  \"roomId\": 1
                }
                """.formatted(visitId, assessmentId)), "/data/ticketNo");

        JsonNode calling = postJson("/api/queues/rooms/1/call-next", token, "");
        assertThat(readText(calling, "/data/ticketNo")).isEqualTo(ticketNo);
        assertThat(readText(calling, "/data/status")).isEqualTo("CALLING");

        QueueTicket ticket = queueTicketMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QueueTicket>()
                .eq(QueueTicket::getTicketNo, ticketNo)
                .last("limit 1"));
        assertThat(ticket).isNotNull();
        assertThat(ticket.getStatus()).isEqualTo("CALLING");
        assertThat(stringRedisTemplate.opsForZSet().score("queue:room:1:active", ticketNo)).isNull();
        assertThat(stringRedisTemplate.opsForZSet().score("queue:dept:1:active", ticketNo)).isNull();
        assertThat(stringRedisTemplate.opsForValue().get("queue:calling:1")).isEqualTo(ticketNo);

        JsonNode completed = postJson("/api/queues/tickets/%s/complete".formatted(ticketNo), token, "");
        assertThat(readText(completed, "/data/status")).isEqualTo("COMPLETED");
        assertThat(stringRedisTemplate.opsForValue().get("queue:calling:1")).isNull();
    }

    @Test
    void shouldAvoidDuplicateClaimDuringConcurrentCallNext() throws Exception {
        String token = login("admin", "password");

        Long firstTicketVisit = createQueuedVisit(token, "李四", "胸痛");
        Long secondTicketVisit = createQueuedVisit(token, "王五", "胸痛");
        assertThat(firstTicketVisit).isNotNull();
        assertThat(secondTicketVisit).isNotNull();

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Callable<String> task = () -> {
                JsonNode response = postJsonAllowBusinessError("/api/queues/rooms/1/call-next", token, "");
                if (!response.path("success").asBoolean()) {
                    return null;
                }
                return readText(response, "/data/ticketNo");
            };
            List<Future<String>> futures = executorService.invokeAll(List.of(task, task));
            List<String> claimed = new ArrayList<>();
            for (Future<String> future : futures) {
                String ticketNo = future.get();
                if (ticketNo != null) {
                    claimed.add(ticketNo);
                }
            }
            Set<String> distinct = claimed.stream().collect(Collectors.toSet());
            assertThat(claimed).hasSize(1);
            assertThat(distinct).hasSize(1);
            String claimedTicketNo = claimed.get(0);
            QueueTicket claimedTicket = queueTicketMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QueueTicket>()
                    .eq(QueueTicket::getTicketNo, claimedTicketNo)
                    .last("limit 1"));
            assertThat(claimedTicket).isNotNull();
            assertThat(claimedTicket.getStatus()).isEqualTo("CALLING");
            assertThat(stringRedisTemplate.opsForValue().get("queue:calling:1")).isEqualTo(claimedTicketNo);
        } finally {
            executorService.shutdownNow();
        }
    }

    private Long createQueuedVisit(String token, String patientName, String symptomTags) throws Exception {
        Long patientId = readId(postJson("/api/patients", token, """
                {
                  \"patientName\": \"%s\",
                  \"gender\": \"MALE\",
                  \"birthDate\": \"1992-02-02\",
                  \"phone\": \"%s\"
                }
                """.formatted(patientName, "139" + System.nanoTime()).replace("\r\n", "\n")));
        Long visitId = readId(postJson("/api/visits", token, """
                {
                  \"patientId\": %d,
                  \"chiefComplaint\": \"%s\"
                }
                """.formatted(patientId, symptomTags)));
        postNoData("/api/visits/%d/arrive".formatted(visitId), token);
        Long assessmentId = readId(postJson("/api/triage/assessments", token, """
                {
                  \"visitId\": %d,
                  \"symptomTags\": \"%s\",
                  \"heartRate\": 110,
                  \"bloodOxygen\": 92,
                  \"assessor\": \"nurse-b\"
                }
                """.formatted(visitId, symptomTags)));
        postJson("/api/queues/tickets", token, """
                {
                  \"visitId\": %d,
                  \"assessmentId\": %d,
                  \"roomId\": 1
                }
                """.formatted(visitId, assessmentId));
        return visitId;
    }

    private String login(String username, String password) throws Exception {
        JsonNode response = postJson("/api/auth/login", null, """
                {
                  \"username\": \"%s\",
                  \"password\": \"%s\"
                }
                """.formatted(username, password));
        return readText(response, "/data/token");
    }

    private void postNoData(String path, String token) throws Exception {
        JsonNode response = postJson(path, token, "");
        assertThat(response.path("success").asBoolean()).isTrue();
    }

    private JsonNode postJson(String path, String token, String body) throws Exception {
        JsonNode response = postJsonAllowBusinessError(path, token, body);
        assertThat(response.path("success").asBoolean())
                .withFailMessage(response.toPrettyString())
                .isTrue();
        return response;
    }

    private JsonNode postJsonAllowBusinessError(String path, String token, String body) throws Exception {
        var requestBuilder = post(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        if (body != null && !body.isBlank()) {
            requestBuilder.content(body);
        }
        String content = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private Long readId(JsonNode response) {
        return response.at("/data/id").asLong();
    }

    private String readText(JsonNode response, String pointer) {
        JsonNode node = response.at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
}
