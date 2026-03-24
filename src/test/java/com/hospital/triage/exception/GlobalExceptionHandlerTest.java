package com.hospital.triage.exception;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeHttpStatusCodeInSuccessBody() throws Exception {
        mockMvc.perform(get("/test/success").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void shouldAlignServiceExceptionStatusAndBodyCode() throws Exception {
        mockMvc.perform(get("/test/conflict").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("409"))
                .andExpect(jsonPath("$.message").value("当前状态不可操作"));
    }

    @Test
    void shouldReturnInternalServerErrorForUnhandledException() throws Exception {
        mockMvc.perform(get("/test/runtime").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后再试"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/success")
        Result<String> success() {
            return Result.success("ok");
        }

        @GetMapping("/conflict")
        Result<Void> conflict() {
            throw new ServiceException(ErrorCodeEnum.CONFLICT.getCode(), "当前状态不可操作");
        }

        @GetMapping("/runtime")
        Result<Void> runtime() {
            throw new IllegalStateException("boom");
        }
    }
}
